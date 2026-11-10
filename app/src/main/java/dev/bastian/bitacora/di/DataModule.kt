package dev.bastian.bitacora.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import dev.bastian.bitacora.BuildConfig
import dev.bastian.bitacora.data.db.BitacoraDatabase
import dev.bastian.bitacora.data.repo.DefaultObservationRepository
import dev.bastian.bitacora.data.sync.DataStoreSyncStore
import dev.bastian.bitacora.data.sync.KtorSyncRemote
import dev.bastian.bitacora.data.sync.SyncEngine
import dev.bastian.bitacora.data.sync.SyncRemote
import dev.bastian.bitacora.data.sync.SyncStore
import dev.bastian.bitacora.data.sync.SyncWorker
import dev.bastian.bitacora.data.sync.WorkManagerSyncScheduler
import dev.bastian.bitacora.data.sync.createSyncHttpClient
import dev.bastian.bitacora.data.system.FilePhotoStorage
import dev.bastian.bitacora.data.system.FrameworkLocationProvider
import dev.bastian.bitacora.data.system.SystemClock
import dev.bastian.bitacora.data.system.UuidGenerator
import dev.bastian.bitacora.domain.Clock
import dev.bastian.bitacora.domain.IdGenerator
import dev.bastian.bitacora.domain.LocationProvider
import dev.bastian.bitacora.domain.ObservationRepository
import dev.bastian.bitacora.domain.PhotoStorage
import dev.bastian.bitacora.domain.SyncScheduler
import dev.bastian.bitacora.ui.edit.EditViewModel
import dev.bastian.bitacora.ui.list.ListViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val IO_DISPATCHER = named("io")

val dataModule = module {
    single<CoroutineDispatcher>(IO_DISPATCHER) { Dispatchers.IO }
    single<Clock> { SystemClock() }
    single<IdGenerator> { UuidGenerator() }
    single { Room.databaseBuilder(androidContext(), BitacoraDatabase::class.java, "bitacora.db").build() }
    single { get<BitacoraDatabase>().observationDao() }
    single<PhotoStorage> { FilePhotoStorage(androidContext(), get(IO_DISPATCHER)) }
    single<LocationProvider> { FrameworkLocationProvider(androidContext()) }
    single { DefaultObservationRepository(get(), get(), get(), get()) } bind ObservationRepository::class
}

val syncModule = module {
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.create(
            produceFile = { File(androidContext().filesDir, "datastore/sync.preferences_pb") },
        )
    }
    single<SyncStore> { DataStoreSyncStore(get()) }
    single<HttpClient> { createSyncHttpClient(CIO.create()) }
    single<SyncRemote> { KtorSyncRemote(get(), BuildConfig.SERVER_URL) }
    single<SyncScheduler> { WorkManagerSyncScheduler(androidContext()) }
    single { SyncEngine(get(), get(), get(), get()) }
    workerOf(::SyncWorker)
}

val uiModule = module {
    viewModelOf(::ListViewModel)
    viewModelOf(::EditViewModel)
}

val appModules = listOf(dataModule, syncModule, uiModule)
