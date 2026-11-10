package dev.bastian.bitacora.di

import androidx.room.Room
import dev.bastian.bitacora.data.system.FilePhotoStorage
import dev.bastian.bitacora.data.system.FrameworkLocationProvider
import dev.bastian.bitacora.domain.LocationProvider
import dev.bastian.bitacora.domain.PhotoStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.named
import dev.bastian.bitacora.data.db.BitacoraDatabase
import dev.bastian.bitacora.data.repo.DefaultObservationRepository
import dev.bastian.bitacora.data.system.SystemClock
import dev.bastian.bitacora.data.system.UuidGenerator
import dev.bastian.bitacora.domain.Clock
import dev.bastian.bitacora.domain.IdGenerator
import dev.bastian.bitacora.domain.ObservationRepository
import dev.bastian.bitacora.ui.edit.EditViewModel
import dev.bastian.bitacora.ui.list.ListViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val IO_DISPATCHER = named("io")

val dataModule = module {
    single<CoroutineDispatcher>(IO_DISPATCHER) { Dispatchers.IO }
    single<PhotoStorage> { FilePhotoStorage(androidContext(), get(IO_DISPATCHER)) }
    single<LocationProvider> { FrameworkLocationProvider(androidContext()) }
    single<Clock> { SystemClock() }
    single<IdGenerator> { UuidGenerator() }
    single { Room.databaseBuilder(androidContext(), BitacoraDatabase::class.java, "bitacora.db").build() }
    single { get<BitacoraDatabase>().observationDao() }
    single { DefaultObservationRepository(get(), get(), get()) } bind ObservationRepository::class
}

val uiModule = module {
    viewModelOf(::ListViewModel)
    viewModelOf(::EditViewModel)
}

val appModules = listOf(dataModule, uiModule)