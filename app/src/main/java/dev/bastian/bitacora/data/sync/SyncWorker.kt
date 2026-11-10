package dev.bastian.bitacora.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val engine: SyncEngine,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (engine.run()) {
        is SyncResult.Success -> Result.success()
        // Every failure is treated as transient; WorkManager applies the exponential backoff.
        is SyncResult.Failure -> if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "bitacora-sync"
        const val MAX_ATTEMPTS = 8
    }
}
