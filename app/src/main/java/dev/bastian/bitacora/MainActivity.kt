package dev.bastian.bitacora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.bastian.bitacora.domain.SyncScheduler
import dev.bastian.bitacora.ui.BitacoraNavHost
import dev.bastian.bitacora.ui.theme.BitacoraTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val syncScheduler: SyncScheduler by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BitacoraTheme {
                BitacoraNavHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Opening the app is one of the sync triggers.
        syncScheduler.requestSync()
    }
}
