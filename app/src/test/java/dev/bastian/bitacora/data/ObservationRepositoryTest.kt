package dev.bastian.bitacora.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bastian.bitacora.data.db.BitacoraDatabase
import dev.bastian.bitacora.data.repo.DefaultObservationRepository
import dev.bastian.bitacora.data.sync.SnapshotCodec
import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.ListFilter
import dev.bastian.bitacora.domain.ObservationDraft
import dev.bastian.bitacora.domain.SyncState
import dev.bastian.bitacora.shared.ObservationDto
import dev.bastian.bitacora.shared.ServerObservation
import dev.bastian.bitacora.testing.FakeClock
import dev.bastian.bitacora.testing.FakeSyncScheduler
import dev.bastian.bitacora.testing.SequentialIds
import dev.bastian.bitacora.testing.entity
import dev.bastian.bitacora.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlin.test.assertNotNull
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
class ObservationRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: BitacoraDatabase
    private val clock = FakeClock(1_000L)
    private val scheduler = FakeSyncScheduler()
    private lateinit var repository: DefaultObservationRepository

    @Before
    fun setUp() {
        database = inMemoryDatabase(context)
        repository = DefaultObservationRepository(database.observationDao(), clock, SequentialIds(), scheduler)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun draft(id: String? = null, title: String = "Heron", notes: String = "", photoPath: String? = null) =
        ObservationDraft(
            id = id,
            title = title,
            notes = notes,
            category = Category.FAUNA,
            latitude = null,
            longitude = null,
            photoPath = photoPath,
            observedAt = null,
        )

    @Test
    fun newObservationIsStoredAsPending() = runTest {
        val id = repository.save(draft(title = "  Heron  "))
        val saved = assertNotNull(repository.get(id))
        assertEquals("Heron", saved.title)
        assertEquals(SyncState.PENDING, saved.syncState)
        assertEquals(1_000L, saved.observedAt)
    }

    @Test
    fun editingBumpsUpdatedAtAndKeepsObservationDate() = runTest {
        val id = repository.save(draft())
        clock.time = 5_000L
        repository.save(draft(id = id, title = "Egret"))
        val saved = assertNotNull(repository.get(id))
        assertEquals("Egret", saved.title)
        assertEquals(5_000L, saved.updatedAt)
        assertEquals(1_000L, saved.observedAt)
    }

    @Test
    fun editingASyncedRecordMarksItPending() = runTest {
        database.observationDao().upsert(entity(id = "a", syncState = SyncState.SYNCED, serverVersion = 3))
        repository.save(draft(id = "a", title = "Changed"))
        assertEquals(SyncState.PENDING, repository.get("a")?.syncState)
    }

    @Test
    fun photoOnlyChangeDoesNotQueueAnUpload() = runTest {
        database.observationDao().upsert(
            entity(id = "a", title = "Heron", category = Category.FAUNA, syncState = SyncState.SYNCED, serverVersion = 3),
        )
        repository.save(draft(id = "a", title = "Heron", photoPath = "/photos/x.jpg"))
        val saved = assertNotNull(repository.get("a"))
        assertEquals(SyncState.SYNCED, saved.syncState)
        assertEquals("/photos/x.jpg", saved.photoPath)
    }

    @Test
    fun deleteHidesTheRecordAndRestoreBringsItBack() = runTest {
        val id = repository.save(draft())
        repository.delete(id)
        assertTrue(repository.observeAll(ListFilter()).first().isEmpty())
        repository.restore(id)
        assertEquals(listOf(id), repository.observeAll(ListFilter()).first().map { it.id })
    }

    @Test
    fun deletingASyncedRecordQueuesTheDeletion() = runTest {
        database.observationDao().upsert(entity(id = "a", syncState = SyncState.SYNCED, serverVersion = 2))
        repository.delete("a")
        val stored = assertNotNull(database.observationDao().getById("a"))
        assertTrue(stored.deleted)
        assertEquals(SyncState.PENDING, stored.syncState)
    }

    @Test
    fun editingARecordInConflictKeepsItInConflict() = runTest {
        database.observationDao().upsert(entity(id = "a", title = "local", syncState = SyncState.CONFLICT))
        repository.save(draft(id = "a", title = "edited again"))
        assertEquals(SyncState.CONFLICT, repository.get("a")?.syncState)
    }

    @Test
    fun filterAppliesToTheRepositoryList() = runTest {
        repository.save(draft(title = "Heron"))
        repository.save(draft(title = "Oak").copy(category = Category.FLORA))
        val result = repository.observeAll(ListFilter(category = Category.FLORA)).first()
        assertEquals(listOf("Oak"), result.map { it.title })
    }

    @Test
    fun everyLocalChangeRequestsASync() = runTest {
        val id = repository.save(draft())
        repository.delete(id)
        repository.restore(id)
        assertEquals(3, scheduler.requests)
    }

    private fun dto(title: String) = ObservationDto(
        id = "a", title = title, notes = "", category = "FAUNA", observedAt = 100, updatedAt = 100,
    )

    private suspend fun storeConflict(localTitle: String = "Local", remoteTitle: String = "Remote") {
        database.observationDao().upsert(
            entity(
                id = "a",
                title = localTitle,
                category = Category.FAUNA,
                syncState = SyncState.CONFLICT,
                serverVersion = 2,
                baseSnapshot = SnapshotCodec.encodeDto(dto("Base")),
                remoteSnapshot = SnapshotCodec.encodeServer(ServerObservation(dto(remoteTitle), 4)),
            ),
        )
    }

    @Test
    fun conflictDetailsAreOnlyAvailableWhileInConflict() = runTest {
        database.observationDao().upsert(entity(id = "a", syncState = SyncState.PENDING))
        assertNull(repository.getConflict("a"))
        assertNull(repository.getConflict("missing"))
    }

    @Test
    fun conflictDetailsExposeBaseLocalAndRemote() = runTest {
        storeConflict()
        val details = assertNotNull(repository.getConflict("a"))
        assertEquals("Base", details.base.title)
        assertEquals("Local", details.local.title)
        assertEquals("Remote", details.remote.title)
        assertEquals(4L, details.remoteVersion)
    }

    @Test
    fun resolvedConflictIsQueuedOnTopOfTheRemoteVersion() = runTest {
        storeConflict()
        val details = assertNotNull(repository.getConflict("a"))

        repository.resolveConflict("a", details.local.copy(title = "Chosen"))

        val stored = assertNotNull(database.observationDao().getById("a"))
        assertEquals(SyncState.PENDING, stored.syncState)
        assertEquals("Chosen", stored.title)
        assertEquals(4L, stored.serverVersion)
        assertNull(stored.remoteSnapshot)
        assertEquals(1, scheduler.requests)
    }

    @Test
    fun takingEveryRemoteValueLeavesNothingToUpload() = runTest {
        storeConflict()
        val details = assertNotNull(repository.getConflict("a"))

        repository.resolveConflict("a", details.remote)

        val stored = assertNotNull(database.observationDao().getById("a"))
        assertEquals(SyncState.SYNCED, stored.syncState)
        assertEquals("Remote", stored.title)
        assertEquals(4L, stored.serverVersion)
    }

    @Test
    fun resolvingARecordNotInConflictChangesNothing() = runTest {
        database.observationDao().upsert(entity(id = "a", title = "Unchanged", syncState = SyncState.PENDING))
        val fields = assertNotNull(database.observationDao().getById("a")).toFields().copy(title = "Other")

        repository.resolveConflict("a", fields)

        assertEquals("Unchanged", database.observationDao().getById("a")?.title)
    }
}
