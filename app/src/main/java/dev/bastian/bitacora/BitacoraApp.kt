package dev.bastian.bitacora

import android.app.Application
import dev.bastian.bitacora.data.sync.ReconnectSyncTrigger
import dev.bastian.bitacora.di.appModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class BitacoraApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reconnectSyncTrigger: ReconnectSyncTrigger by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BitacoraApp)
            // Initializes WorkManager with a worker factory that builds workers through Koin.
            workManagerFactory()
            modules(appModules)
        }
        // Regaining the network is one of the sync triggers.
        applicationScope.launch { reconnectSyncTrigger.run() }
    }
}
