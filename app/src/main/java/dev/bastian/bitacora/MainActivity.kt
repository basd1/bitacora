package dev.bastian.bitacora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.bastian.bitacora.ui.BitacoraNavHost
import dev.bastian.bitacora.ui.theme.BitacoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BitacoraTheme {
                BitacoraNavHost()
            }
        }
    }
}
