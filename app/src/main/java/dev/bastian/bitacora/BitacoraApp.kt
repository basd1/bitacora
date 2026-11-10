package dev.bastian.bitacora

import android.app.Application
import dev.bastian.bitacora.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BitacoraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@BitacoraApp)
            modules(appModules)
        }
    }
}