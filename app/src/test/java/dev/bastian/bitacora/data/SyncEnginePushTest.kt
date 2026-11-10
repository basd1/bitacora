package dev.bastian.bitacora.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bastian.bitacora.data.db.BitacoraDatabase
import dev.bastian.bitacora.data.sync.KtorSyncRemote
import dev.bastian.bitacora.data.sync.SyncEngine
import dev.bastian.bitacora.data.sync.SyncResult
import dev.bastian.bitacora.data.sync.createSyncHttpClient
import dev.bastian.bitacora.domain.SyncState
import dev.bastian.bitacora.shared.BatchResponse
import dev.bastian.bitacora.shared.ChangeResult
import dev.bastian.bitacora.shared.ChangeStatus
import dev.bastian.bitacora.testing.FakeClock
import dev.bastian.bitacora.testing.FakeRemote
import dev.bastian.bitacora.testing.InMemorySyncStore
import dev.bastian.bitacora.testing.entity
import dev.bastian.bitacora.testing.inMemoryDatabase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlin.test.assertNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncEnginePushTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: BitacoraDatabase
    private val remote = FakeRemote()
    private val store = InMemorySyncStore()
    private val clock = FakeClock(9_000L)
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        database = inMemoryDatabase(context)
        engine = SyncEngine(database.observationDao(), remote, store, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private val dao get() = database.observationDao()

    @Test
    fun pendingRecordIsUploadedAndBecomesSynced() = runTest {
        dao.upsert(entity(id = "a", title = "Heron", updatedAt = 50))
        remote.pushHandler = { changes ->
            BatchResponse(changes.map { ChangeResult(it.observation.id, ChangeStatus.ACCEPTED, 5) })
        }

        val result = engine.run()

        assertEquals(SyncResult.Success, result)
        val stored = assertNotNull(dao.getById("a"))
        assertEquals(SyncState.SYNCED, stored.syncState)
        assertEquals(5L, stored.serverVersion)
        assertNotNull(stored.baseSnapshot)
        val sent = remote.pushes.single().single()
        assertEquals(0L, sent.baseVersion)
        assertEquals("Heron", sent.observation.title)
        assertEquals(9_000L, store.lastSyncAt.first())
    }

    @Test
    fun syncedRecordsAreNotUploadedAgain() = runTest {
        dao.upsert(entity(id = "a", syncState = SyncState.SYNCED, serverVersion = 2))
        engine.run()
        assertTrue(remote.pushes.isEmpty())
    }

    @Test
    fun unreachableServerKeepsRecordsPendingAndRecordsTheError() = runTest {
        dao.upsert(entity(id = "a"))
        remote.failure = IOException("Connection refused")

        val result = engine.run()

        assertEquals(SyncResult.Failure("Connection refused"), result)
        assertEquals(SyncState.PENDING, dao.getById("a")?.syncState)
        assertEquals("Connection refused", store.lastError.first())
        assertNull(store.lastSyncAt.first())
    }

    @Test
    fun recordsAreUploadedInBatches() = runTest {
        repeat(120) { index -> dao.upsert(entity(id = "id-$index", updatedAt = index.toLong())) }
        engine.run()
        assertEquals(listOf(50, 50, 20), remote.pushes.map { it.size })
        assertTrue(dao.getByState(SyncState.PENDING).isEmpty())
    }

    @Test
    fun editMadeWhileUploadingStaysPendingOnTheNewBase() = runTest {
        dao.upsert(entity(id = "a", title = "before", updatedAt = 50))
        var firstCall = true
        remote.pushHandler = { changes ->
            if (firstCall) {
                firstCall = false
                // The user edits the record while the request is in flight.
                dao.update("a") { it?.copy(title = "edited meanwhile", updatedAt = 60) }
            }
            BatchResponse(changes.map { ChangeResult(it.observation.id, ChangeStatus.ACCEPTED, 4) })
        }

        engine.run()

        // The second round uploads the edit on top of version 4.
        assertEquals(2, remote.pushes.size)
        assertEquals(4L, remote.pushes[1].single().baseVersion)
        assertEquals("edited meanwhile", remote.pushes[1].single().observation.title)
        assertEquals(SyncState.SYNCED, dao.getById("a")?.syncState)
    }

    @Test
    fun rejectedRequestMarksTheBatchAsErrorWithItsCause() = runTest {
        dao.upsert(entity(id = "a"))
        val client = createSyncHttpClient(MockEngine { respond("nope", HttpStatusCode.BadRequest) })
        val engineWithHttp = SyncEngine(dao, KtorSyncRemote(client, "http://server"), store, clock)

        engineWithHttp.run()

        val stored = assertNotNull(dao.getById("a"))
        assertEquals(SyncState.ERROR, stored.syncState)
        assertEquals("HTTP 400", stored.errorMessage)
    }

    @Test
    fun repeatedPassesAfterAnInterruptedUploadDoNotDuplicateRecords() = runTest {
        dao.upsert(entity(id = "a"))
        remote.failure = IOException("connection reset")
        engine.run()
        remote.failure = null
        engine.run()
        engine.run()
        assertEquals(1, remote.pushes.size)
        assertEquals(SyncState.SYNCED, dao.getById("a")?.syncState)
    }
}
