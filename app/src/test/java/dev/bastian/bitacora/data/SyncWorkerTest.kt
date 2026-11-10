package dev.bastian.bitacora.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import dev.bastian.bitacora.data.db.BitacoraDatabase
import dev.bastian.bitacora.data.sync.SyncEngine
import dev.bastian.bitacora.data.sync.SyncWorker
import dev.bastian.bitacora.domain.SyncState
import dev.bastian.bitacora.testing.FakeClock
import dev.bastian.bitacora.testing.FakeRemote
import dev.bastian.bitacora.testing.InMemorySyncStore
import dev.bastian.bitacora.testing.entity
import dev.bastian.bitacora.testing.inMemoryDatabase
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: BitacoraDatabase
    private val remote = FakeRemote()

    @Before
    fun setUp() {
        database = inMemoryDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun worker(runAttemptCount: Int = 0): SyncWorker {
        val engine = SyncEngine(database.observationDao(), remote, InMemorySyncStore(), FakeClock())
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = SyncWorker(appContext, workerParameters, engine)
        }
        return TestListenableWorkerBuilder.from(context, SyncWorker::class.java)
            .setWorkerFactory(factory)
            .setRunAttemptCount(runAttemptCount)
            .build()
    }

    @Test
    fun successfulPassReportsSuccessAndSyncsPendingRecords() = runTest {
        database.observationDao().upsert(entity(id = "a"))
        val result = worker().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(SyncState.SYNCED, database.observationDao().getById("a")?.syncState)
    }

    @Test
    fun failedPassAsksForARetryWithBackoff() = runTest {
        database.observationDao().upsert(entity(id = "a"))
        remote.failure = IOException("offline")
        val result = worker(runAttemptCount = 1).doWork()
        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(SyncState.PENDING, database.observationDao().getById("a")?.syncState)
    }

    @Test
    fun givesUpAfterTooManyAttempts() = runTest {
        remote.failure = IOException("offline")
        val result = worker(runAttemptCount = SyncWorker.MAX_ATTEMPTS).doWork()
        assertTrue(result is ListenableWorker.Result.Failure)
    }
}
