package dev.bastian.bitacora.testing

import android.content.Context
import androidx.room.Room
import dev.bastian.bitacora.data.db.BitacoraDatabase
import dev.bastian.bitacora.data.db.ObservationEntity
import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.Clock
import dev.bastian.bitacora.domain.IdGenerator
import dev.bastian.bitacora.domain.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class FakeClock(var time: Long = 1_000L) : Clock {
    override fun now(): Long = time
}

class SequentialIds : IdGenerator {
    private var counter = 0
    override fun newId(): String = "id-${++counter}"
}

/** Ids that stay unique across several simulated devices. */
class PrefixedIds(private val prefix: String) : IdGenerator {
    private var counter = 0
    override fun newId(): String = "$prefix-${++counter}"
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

fun inMemoryDatabase(context: Context): BitacoraDatabase =
    Room.inMemoryDatabaseBuilder(context, BitacoraDatabase::class.java)
        .allowMainThreadQueries()
        .build()

fun entity(
    id: String = "a",
    title: String = "Title",
    notes: String = "",
    category: Category = Category.OTHER,
    latitude: Double? = null,
    longitude: Double? = null,
    photoPath: String? = null,
    observedAt: Long = 100L,
    updatedAt: Long = 100L,
    serverVersion: Long = 0L,
    syncState: SyncState = SyncState.PENDING,
    deleted: Boolean = false,
    baseSnapshot: String? = null,
    remoteSnapshot: String? = null,
    errorMessage: String? = null,
) = ObservationEntity(
    id = id,
    title = title,
    notes = notes,
    category = category,
    latitude = latitude,
    longitude = longitude,
    photoPath = photoPath,
    observedAt = observedAt,
    updatedAt = updatedAt,
    serverVersion = serverVersion,
    syncState = syncState,
    deleted = deleted,
    baseSnapshot = baseSnapshot,
    remoteSnapshot = remoteSnapshot,
    errorMessage = errorMessage,
)
