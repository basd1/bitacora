package dev.bastian.bitacora.di

import androidx.room.Room
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

val dataModule = module {
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