package dev.bastian.bitacora.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bastian.bitacora.data.db.BitacoraDatabase
import dev.bastian.bitacora.data.repo.DefaultObservationRepository
import dev.bastian.bitacora.data.sync.KtorSyncRemote
import dev.bastian.bitacora.data.sync.SyncEngine
import dev.bastian.bitacora.data.sync.SyncRemote
import dev.bastian.bitacora.data.sync.SyncResult
import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.Field
import dev.bastian.bitacora.domain.MergeResult
import dev.bastian.bitacora.domain.ObservationDraft
import dev.bastian.bitacora.domain.Side
import dev.bastian.bitacora.domain.SyncState
import dev.bastian.bitacora.domain.mergeThreeWay
import dev.bastian.bitacora.server.ObservationStore
import dev.bastian.bitacora.server.module
import dev.bastian.bitacora.shared.BatchResponse
import dev.bastian.bitacora.shared.ChangeDto
import dev.bastian.bitacora.shared.PullResponse
import dev.bastian.bitacora.testing.FakeClock
import dev.bastian.bitacora.testing.FakeSyncScheduler
import dev.bastian.bitacora.testing.InMemorySyncStore
import dev.bastian.bitacora.testing.PrefixedIds
import dev.bastian.bitacora.testing.inMemoryDatabase
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.io.IOException
import kotlin.test.assertNotNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real sync engines on two simulated devices against the real server application running in
 * process (Ktor `testApplication`, no sockets).
 */
@RunWith(RobolectricTestRunner::class)
class SyncIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databases = mutableListOf<BitacoraDatabase>()
    private val clock = FakeClock(1_000L)

    @After
    fun tearDown() {
        databases.forEach { it.close() }
    }

    private inner class Device(remote: SyncRemote, idPrefix: String) {
        val database = inMemoryDatabase(context).also { databases += it }
        val repository = DefaultObservationRepository(
            database.observationDao(),
            clock,
            PrefixedIds(idPrefix),
            FakeSyncScheduler(),
        )
        val engine = SyncEngine(database.observationDao(), remote, InMemorySyncStore(), clock)

        suspend fun sync(): SyncResult {
            clock.time += 10
            return engine.run()
        }

        suspend fun create(title: String, notes: String = ""): String {
            clock.time += 10
            return repository.save(
                ObservationDraft(null, title, notes, Category.FAUNA, null, null, null, null),
            )
        }

        suspend fun edit(id: String, title: String? = null, notes: String? = null, delete: Boolean = false) {
            clock.time += 10
            val current = assertNotNull(repository.get(id))
            if (delete) {
                repository.delete(id)
                return
            }
            repository.save(
                ObservationDraft(
                    id = id,
                    title = title ?: current.title,
                    notes = notes ?: current.notes,
                    category = current.category,
                    latitude = current.latitude,
                    longitude = current.longitude,
                    photoPath = current.photoPath,
                    observedAt = current.observedAt,
                ),
            )
        }

        suspend fun entity(id: String) = assertNotNull(database.observationDao().getById(id))
    }

    @Test
    fun twoDevicesEditingDifferentFieldsConvergeToTheMergedRecord() = testApplication {
        application { module(ObservationStore()) }
        val remote = KtorSyncRemote(createClient { install(ContentNegotiation) { json() }; expectSuccess = true }, "")
        val a = Device(remote, "a")
        val b = Device(remote, "b")

        val id = a.create("Heron", "by the river")
        assertEquals(SyncResult.Success, a.sync())
        b.sync()

        a.edit(id, title = "Grey heron")
        b.edit(id, notes = "seen at dawn")
        a.sync()
        b.sync()
        a.sync()

        val finalA = a.entity(id)
        val finalB = b.entity(id)
        assertEquals("Grey heron", finalA.title)
        assertEquals("seen at dawn", finalA.notes)
        assertEquals(finalA.title, finalB.title)
        assertEquals(finalA.notes, finalB.notes)
        assertEquals(SyncState.SYNCED, finalA.syncState)
        assertEquals(SyncState.SYNCED, finalB.syncState)
        assertEquals(finalA.serverVersion, finalB.serverVersion)
    }

    @Test
    fun twoDevicesEditingTheSameFieldRaiseAConflictThatTheUserResolves() = testApplication {
        application { module(ObservationStore()) }
        val remote = KtorSyncRemote(createClient { install(ContentNegotiation) { json() }; expectSuccess = true }, "")
        val a = Device(remote, "a")
        val b = Device(remote, "b")

        val id = a.create("Heron")
        a.sync()
        b.sync()

        a.edit(id, title = "Grey heron")
        b.edit(id, title = "Purple heron")
        a.sync()
        b.sync()

        assertEquals(SyncState.CONFLICT, b.entity(id).syncState)
        val details = assertNotNull(b.repository.getConflict(id))
        val merge = mergeThreeWay(details.base, details.local, details.remote) as MergeResult.Conflicted
        assertEquals(setOf(Field.TITLE), merge.conflicts)

        // Device B keeps its own value.
        b.repository.resolveConflict(id, merge.resolve(details.remote, mapOf(Field.TITLE to Side.LOCAL)))
        b.sync()
        a.sync()

        assertEquals("Purple heron", b.entity(id).title)
        assertEquals("Purple heron", a.entity(id).title)
        assertEquals(SyncState.SYNCED, a.entity(id).syncState)
        assertEquals(SyncState.SYNCED, b.entity(id).syncState)
    }

    @Test
    fun editAgainstDeletionRestoresTheRecordOnBothDevices() = testApplication {
        application { module(ObservationStore()) }
        val remote = KtorSyncRemote(createClient { install(ContentNegotiation) { json() }; expectSuccess = true }, "")
        val a = Device(remote, "a")
        val b = Device(remote, "b")

        val id = a.create("Heron")
        a.sync()
        b.sync()

        a.edit(id, delete = true)
        b.edit(id, notes = "edited offline")
        a.sync()
        b.sync()
        a.sync()

        assertFalse(a.entity(id).deleted)
        assertFalse(b.entity(id).deleted)
        assertEquals("edited offline", a.entity(id).notes)
        assertEquals("edited offline", b.entity(id).notes)
    }

    @Test
    fun uploadWhoseResponseWasLostIsNeitherDuplicatedNorLost() = testApplication {
        application { module(ObservationStore()) }
        val real = KtorSyncRemote(createClient { install(ContentNegotiation) { json() }; expectSuccess = true }, "")
        var dropNextResponse = true
        val flaky = object : SyncRemote {
            override suspend fun pull(since: Long): PullResponse = real.pull(since)

            override suspend fun push(changes: List<ChangeDto>): BatchResponse {
                val response = real.push(changes)
                if (dropNextResponse) {
                    // The server applied the batch, but the process dies before the client sees the answer.
                    dropNextResponse = false
                    throw IOException("connection reset after the server applied the batch")
                }
                return response
            }
        }
        val a = Device(flaky, "a")

        val id = a.create("Heron")
        assertTrue(a.sync() is SyncResult.Failure)
        assertEquals(SyncState.PENDING, a.entity(id).syncState)

        assertEquals(SyncResult.Success, a.sync())

        assertEquals(SyncState.SYNCED, a.entity(id).syncState)
        val onServer = real.pull(0)
        assertEquals(1, onServer.items.size)
        assertEquals(1L, onServer.latestVersion)
        assertEquals(1L, a.entity(id).serverVersion)
    }
}
