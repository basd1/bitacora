package dev.bastian.bitacora.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bastian.bitacora.data.db.BitacoraDatabase
import dev.bastian.bitacora.data.repo.DefaultSyncStatusRepository
import dev.bastian.bitacora.data.sync.ReconnectSyncTrigger
import dev.bastian.bitacora.domain.NetworkMonitor
import dev.bastian.bitacora.domain.SyncState
import dev.bastian.bitacora.testing.FakeSyncScheduler
import dev.bastian.bitacora.testing.InMemorySyncStore
import dev.bastian.bitacora.testing.entity
import dev.bastian.bitacora.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncStatusTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: BitacoraDatabase
    private val store = InMemorySyncStore()
    private val scheduler = FakeSyncScheduler()
    private lateinit var repository: DefaultSyncStatusRepository

    @Before
    fun setUp() {
        database = inMemoryDatabase(context)
        repository = DefaultSyncStatusRepository(database.observationDao(), store, scheduler)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun statusCountsPendingConflictsAndErrorsWithTheirCause() = runTest {
        val dao = database.observationDao()
        dao.upsert(entity(id = "p1", syncState = SyncState.PENDING))
        dao.upsert(entity(id = "p2", syncState = SyncState.PENDING))
        dao.upsert(entity(id = "c", syncState = SyncState.CONFLICT))
        dao.upsert(entity(id = "e", title = "Heron", syncState = SyncState.ERROR, errorMessage = "HTTP 400"))
        dao.upsert(entity(id = "s", syncState = SyncState.SYNCED))
        store.recordError("Connection refused")

        val status = repository.status.first()

        assertEquals(2, status.pending)
        assertEquals(1, status.conflicts)
        assertEquals("Heron", status.errors.single().title)
        assertEquals("HTTP 400", status.errors.single().message)
        assertEquals("Connection refused", status.lastError)
        assertNull(status.lastSyncAt)
    }

    @Test
    fun statusShowsTheLastSuccessfulSync() = runTest {
        store.recordError("boom")
        store.recordSuccess(5_000L)
        val status = repository.status.first()
        assertEquals(5_000L, status.lastSyncAt)
        assertNull(status.lastError)
    }

    @Test
    fun retryQueuesRejectedRecordsAgainAndStartsASync() = runTest {
        val dao = database.observationDao()
        dao.upsert(entity(id = "e", syncState = SyncState.ERROR, errorMessage = "HTTP 400"))

        repository.retry()

        val stored = dao.getById("e")
        assertEquals(SyncState.PENDING, stored?.syncState)
        assertNull(stored?.errorMessage)
        assertEquals(1, scheduler.retries)
    }

    private fun networkOf(vararg values: Boolean) = object : NetworkMonitor {
        override val isOnline = flowOf(*values.toTypedArray())
    }

    @Test
    fun reconnectingRequestsASyncEachTime() = runTest {
        // Offline at start, then online, offline again, online again.
        ReconnectSyncTrigger(networkOf(false, true, true, false, true), scheduler).run()
        assertEquals(2, scheduler.requests)
    }

    @Test
    fun startingOnlineIsNotAReconnection() = runTest {
        ReconnectSyncTrigger(networkOf(true, true), scheduler).run()
        assertEquals(0, scheduler.requests)
    }
}
