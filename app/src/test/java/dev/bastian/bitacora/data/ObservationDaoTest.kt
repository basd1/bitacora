package dev.bastian.bitacora.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.bastian.bitacora.data.db.BitacoraDatabase
import dev.bastian.bitacora.data.db.ObservationDao
import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.testing.entity
import dev.bastian.bitacora.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ObservationDaoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: BitacoraDatabase
    private lateinit var dao: ObservationDao

    @Before
    fun setUp() {
        database = inMemoryDatabase(context)
        dao = database.observationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertInsertsAndThenReplaces() = runTest {
        dao.upsert(entity(id = "a", title = "first"))
        dao.upsert(entity(id = "a", title = "second"))
        assertEquals("second", dao.getById("a")?.title)
    }

    @Test
    fun listIsOrderedByObservationDateDescending() = runTest {
        dao.upsert(entity(id = "old", observedAt = 10))
        dao.upsert(entity(id = "new", observedAt = 30))
        dao.upsert(entity(id = "mid", observedAt = 20))
        val ids = dao.observeFiltered(null, "").first().map { it.id }
        assertEquals(listOf("new", "mid", "old"), ids)
    }

    @Test
    fun deletedRecordsAreHiddenFromTheList() = runTest {
        dao.upsert(entity(id = "kept"))
        dao.upsert(entity(id = "gone", deleted = true))
        assertEquals(listOf("kept"), dao.observeFiltered(null, "").first().map { it.id })
        assertNotNull(dao.getById("gone"))
    }

    @Test
    fun filtersByCategory() = runTest {
        dao.upsert(entity(id = "bird", category = Category.FAUNA))
        dao.upsert(entity(id = "oak", category = Category.FLORA))
        val result = dao.observeFiltered(Category.FLORA.name, "").first()
        assertEquals(listOf("oak"), result.map { it.id })
    }

    @Test
    fun searchMatchesTitleOrNotesIgnoringCase() = runTest {
        dao.upsert(entity(id = "t", title = "Grey Heron"))
        dao.upsert(entity(id = "n", title = "Other", notes = "seen near the HERON colony"))
        dao.upsert(entity(id = "x", title = "Unrelated"))
        val result = dao.observeFiltered(null, "heron").first().map { it.id }.sorted()
        assertEquals(listOf("n", "t"), result)
    }

    @Test
    fun updateCreatesTheRecordWhenAbsent() = runTest {
        dao.update("a") { existing ->
            assertNull(existing)
            entity(id = "a", title = "created")
        }
        assertEquals("created", dao.getById("a")?.title)
    }

    @Test
    fun updateWithNullLeavesTheRecordUntouched() = runTest {
        dao.upsert(entity(id = "a", title = "original"))
        dao.update("a") { null }
        assertEquals("original", dao.getById("a")?.title)
    }

    @Test
    fun recordsSurviveClosingAndReopeningTheDatabase() = runTest {
        val file = Room.databaseBuilder(context, BitacoraDatabase::class.java, "persistence-test.db")
            .allowMainThreadQueries()
            .build()
        file.observationDao().upsert(entity(id = "kept", title = "still here"))
        file.close()

        val reopened = Room.databaseBuilder(context, BitacoraDatabase::class.java, "persistence-test.db")
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("still here", reopened.observationDao().getById("kept")?.title)
        } finally {
            reopened.close()
            context.deleteDatabase("persistence-test.db")
        }
    }
}
