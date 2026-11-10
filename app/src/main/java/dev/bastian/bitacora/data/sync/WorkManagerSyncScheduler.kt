package dev.bastian.bitacora.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dev.bastian.bitacora.domain.SyncScheduler
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorkManagerSyncScheduler(private val context: Context) : SyncScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override fun requestSync() = enqueue(ExistingWorkPolicy.KEEP)

    override fun retryNow() = enqueue(ExistingWorkPolicy.REPLACE)

    override val isSyncing: Flow<Boolean>
        get() = workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.UNIQUE_NAME)
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }

    private fun enqueue(policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(SyncWorker.UNIQUE_NAME, policy, request)
    }

    private companion object {
        const val BACKOFF_SECONDS = 15L
    }
}
