package dev.bastian.bitacora.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bastian.bitacora.data.db.BitacoraDatabase
import dev.bastian.bitacora.data.sync.SnapshotCodec
import dev.bastian.bitacora.data.sync.SyncEngine
import dev.bastian.bitacora.data.sync.SyncResult
import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.SyncState
import dev.bastian.bitacora.shared.BatchResponse
import dev.bastian.bitacora.shared.ChangeResult
import dev.bastian.bitacora.shared.ChangeStatus
import dev.bastian.bitacora.shared.ObservationDto
import dev.bastian.bitacora.shared.PullResponse
import dev.bastian.bitacora.shared.ServerObservation
import dev.bastian.bitacora.testing.FakeClock
import dev.bastian.bitacora.testing.FakeRemote
import dev.bastian.bitacora.testing.InMemorySyncStore
import dev.bastian.bitacora.testing.entity
import dev.bastian.bitacora.testing.inMemoryDatabase
import java.io.IOException
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncEnginePullTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: BitacoraDatabase
    private val remote = FakeRemote()
    private val store = InMemorySyncStore()
    private val clock = FakeClock(10_000L)
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

    private fun dto(
        id: String = "a",
        title: String = "Heron",
        notes: String = "",
        category: Category = Category.OTHER,
        updatedAt: Long = 100,
        deleted: Boolean = false,
    ) = ObservationDto(
        id = id,
        title = title,
        notes = notes,
        category = category.name,
        observedAt = 100,
        updatedAt = updatedAt,
        deleted = deleted,
    )

    private fun pulls(vararg items: ServerObservation, latest: Long = items.maxOfOrNull { it.version } ?: 0) {
        remote.pullResponse = PullResponse(items.toList(), latest)
    }

    private fun snapshot(dto: ObservationDto) = SnapshotCodec.encodeDto(dto)

    @Test
    fun unknownRemoteRecordIsInsertedAsSynced() = runTest {
        pulls(ServerObservation(dto("a", "Heron"), 3))
        assertEquals(SyncResult.Success, engine.run())
        val stored = assertNotNull(dao.getById("a"))
        assertEquals("Heron", stored.title)
        assertEquals(SyncState.SYNCED, stored.syncState)
        assertEquals(3L, stored.serverVersion)
        assertNull(stored.photoPath)
    }

    @Test
    fun cursorAdvancesToTheLatestServerVersion() = runTest {
        pulls(ServerObservation(dto("a"), 3), latest = 7)
        engine.run()
        assertEquals(7L, store.lastPulledVersion())
    }

    @Test
    fun syncedRecordIsReplacedByANewerRemoteVersionKeepingItsPhoto() = runTest {
        val old = dto("a", "Old", updatedAt = 50)
        dao.upsert(
            entity(
                id = "a", title = "Old", syncState = SyncState.SYNCED, serverVersion = 2,
                baseSnapshot = snapshot(old), photoPath = "/photos/a.jpg",
            ),
        )
        pulls(ServerObservation(dto("a", "New", updatedAt = 80), 4))

        engine.run()

        val stored = assertNotNull(dao.getById("a"))
        assertEquals("New", stored.title)
        assertEquals(4L, stored.serverVersion)
        assertEquals(SyncState.SYNCED, stored.syncState)
        assertEquals("/photos/a.jpg", stored.photoPath)
    }

    @Test
    fun versionAlreadyHeldLocallyIsIgnored() = runTest {
        dao.upsert(entity(id = "a", title = "Local edit", syncState = SyncState.SYNCED, serverVersion = 5))
        pulls(ServerObservation(dto("a", "Other content"), 5))
        engine.run()
        assertEquals("Local edit", dao.getById("a")?.title)
    }

    @Test
    fun pendingRecordMergesCleanlyWithADifferentRemoteField() = runTest {
        remote.ignorePushes()
        val base = dto("a", title = "Heron", notes = "")
        dao.upsert(
            entity(
                id = "a", title = "Grey heron", notes = "", syncState = SyncState.PENDING,
                serverVersion = 3, baseSnapshot = snapshot(base), updatedAt = 120,
            ),
        )
        pulls(ServerObservation(dto("a", title = "Heron", notes = "by the river", updatedAt = 110), 4))

        engine.run()

        val stored = assertNotNull(dao.getById("a"))
        assertEquals("Grey heron", stored.title)
        assertEquals("by the river", stored.notes)
        assertEquals(SyncState.PENDING, stored.syncState)
        assertEquals(4L, stored.serverVersion)
    }

    @Test
    fun mergedRecordIsUploadedOnTheNextPassOnTopOfTheRemoteVersion() = runTest {
        remote.ignorePushes()
        val base = dto("a", title = "Heron", notes = "")
        dao.upsert(
            entity(id = "a", title = "Grey heron", syncState = SyncState.PENDING, serverVersion = 3, baseSnapshot = snapshot(base)),
        )
        pulls(ServerObservation(dto("a", title = "Heron", notes = "by the river"), 4))
        engine.run()
        remote.pullResponse = PullResponse(emptyList(), 4)
        remote.pushes.clear()
        remote.acceptAll(version = 5)

        engine.run()

        val sent = remote.pushes.single().single()
        assertEquals(4L, sent.baseVersion)
        assertEquals("Grey heron", sent.observation.title)
        assertEquals("by the river", sent.observation.notes)
    }

    @Test
    fun sameFieldChangedOnBothSidesBecomesAConflict() = runTest {
        remote.ignorePushes()
        val base = dto("a", title = "Heron")
        dao.upsert(
            entity(id = "a", title = "Grey heron", syncState = SyncState.PENDING, serverVersion = 3, baseSnapshot = snapshot(base)),
        )
        pulls(ServerObservation(dto("a", title = "Purple heron"), 4))

        engine.run()

        val stored = assertNotNull(dao.getById("a"))
        assertEquals(SyncState.CONFLICT, stored.syncState)
        assertEquals("Grey heron", stored.title)
        val remoteRecord = SnapshotCodec.decodeServer(assertNotNull(stored.remoteSnapshot))
        assertEquals("Purple heron", remoteRecord.observation.title)
        assertEquals(4L, remoteRecord.version)
    }

    @Test
    fun remoteDeletionHidesASyncedRecord() = runTest {
        dao.upsert(entity(id = "a", syncState = SyncState.SYNCED, serverVersion = 2, baseSnapshot = snapshot(dto("a"))))
        pulls(ServerObservation(dto("a", deleted = true), 3))
        engine.run()
        assertTrue(assertNotNull(dao.getById("a")).deleted)
    }

    @Test
    fun localEditBeatsARemoteDeletion() = runTest {
        remote.ignorePushes()
        val base = dto("a", notes = "")
        dao.upsert(
            entity(id = "a", notes = "edited offline", syncState = SyncState.PENDING, serverVersion = 2, baseSnapshot = snapshot(base)),
        )
        pulls(ServerObservation(dto("a", deleted = true), 3))

        engine.run()

        val stored = assertNotNull(dao.getById("a"))
        assertFalse(stored.deleted)
        assertEquals("edited offline", stored.notes)
        assertEquals(SyncState.PENDING, stored.syncState)
    }

    @Test
    fun failedPullDoesNotAdvanceTheCursor() = runTest {
        pulls(ServerObservation(dto("a"), 3))
        remote.failure = IOException("offline")
        assertTrue(engine.run() is SyncResult.Failure)
        assertEquals(0L, store.lastPulledVersion())
        assertNull(dao.getById("a"))
    }

    @Test
    fun uploadRejectedWithACleanMergeIsUploadedAgainOnTheServerVersion() = runTest {
        val base = dto("a", title = "Heron", notes = "")
        dao.upsert(
            entity(
                id = "a", title = "Heron", notes = "my notes", syncState = SyncState.PENDING,
                serverVersion = 1, baseSnapshot = snapshot(base), updatedAt = 120,
            ),
        )
        var attempt = 0
        remote.pushHandler = { changes ->
            attempt++
            val change = changes.single()
            if (attempt == 1) {
                val current = dto("a", title = "Grey heron", notes = "", updatedAt = 110)
                BatchResponse(listOf(ChangeResult("a", ChangeStatus.CONFLICT, 2, current)))
            } else {
                BatchResponse(listOf(ChangeResult(change.observation.id, ChangeStatus.ACCEPTED, 3)))
            }
        }
        remote.pullResponse = PullResponse(emptyList(), 3)

        engine.run()

        assertEquals(2, remote.pushes.size)
        val second = remote.pushes[1].single()
        assertEquals(2L, second.baseVersion)
        assertEquals("Grey heron", second.observation.title)
        assertEquals("my notes", second.observation.notes)
        val stored = assertNotNull(dao.getById("a"))
        assertEquals(SyncState.SYNCED, stored.syncState)
        assertEquals(3L, stored.serverVersion)
    }

    @Test
    fun uploadRejectedWithARealConflictWaitsForTheUser() = runTest {
        val base = dto("a", title = "Heron")
        dao.upsert(
            entity(id = "a", title = "Grey heron", syncState = SyncState.PENDING, serverVersion = 1, baseSnapshot = snapshot(base)),
        )
        remote.pushHandler = {
            val current = dto("a", title = "Purple heron", updatedAt = 110)
            BatchResponse(listOf(ChangeResult("a", ChangeStatus.CONFLICT, 2, current)))
        }

        engine.run()

        assertEquals(1, remote.pushes.size)
        val stored = assertNotNull(dao.getById("a"))
        assertEquals(SyncState.CONFLICT, stored.syncState)
        assertEquals("Grey heron", stored.title)
    }
}
