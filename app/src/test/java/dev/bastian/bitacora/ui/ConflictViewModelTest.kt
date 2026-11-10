package dev.bastian.bitacora.ui

import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.ConflictDetails
import dev.bastian.bitacora.domain.Field
import dev.bastian.bitacora.domain.Side
import dev.bastian.bitacora.domain.SyncableFields
import dev.bastian.bitacora.testing.FakeObservationRepository
import dev.bastian.bitacora.testing.MainDispatcherRule
import dev.bastian.bitacora.ui.conflict.ConflictValue
import dev.bastian.bitacora.ui.conflict.ConflictViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConflictViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeObservationRepository()
    private val viewModel = ConflictViewModel(repository)

    private val base = SyncableFields("Heron", "notes", Category.FAUNA, null, null, 100L, false)

    private fun conflict(local: SyncableFields, remote: SyncableFields) =
        ConflictDetails(id = "a", base = base, local = local, remote = remote, remoteVersion = 4)

    @Test
    fun conflictingFieldsAreListedWithLocalPreselected() = runTest {
        repository.conflict = conflict(
            local = base.copy(title = "Grey heron", notes = "my notes"),
            remote = base.copy(title = "Purple heron"),
        )

        viewModel.load("a")

        val field = viewModel.uiState.value.fields.single()
        assertEquals(Field.TITLE, field.field)
        assertEquals(ConflictValue.Text("Grey heron"), field.local)
        assertEquals(ConflictValue.Text("Purple heron"), field.remote)
        assertEquals(Side.LOCAL, field.choice)
    }

    @Test
    fun resolvingAppliesTheChosenSideAndKeepsTheMergedFields() = runTest {
        repository.conflict = conflict(
            local = base.copy(title = "Grey heron", notes = "my notes"),
            remote = base.copy(title = "Purple heron"),
        )
        viewModel.load("a")

        viewModel.choose(Field.TITLE, Side.REMOTE)
        viewModel.resolve()

        val (id, resolved) = repository.resolutions.single()
        assertEquals("a", id)
        assertEquals("Purple heron", resolved.title)
        assertEquals("my notes", resolved.notes)
        assertTrue(viewModel.uiState.value.resolved)
    }

    @Test
    fun keepingLocalValuesResolvesWithTheLocalVersion() = runTest {
        repository.conflict = conflict(
            local = base.copy(title = "Grey heron"),
            remote = base.copy(title = "Purple heron"),
        )
        viewModel.load("a")

        viewModel.resolve()

        assertEquals("Grey heron", repository.resolutions.single().second.title)
    }

    @Test
    fun missingConflictIsReportedAsNotFound() = runTest {
        viewModel.load("a")
        assertTrue(viewModel.uiState.value.notFound)
        assertFalse(viewModel.uiState.value.resolved)
    }

    @Test
    fun aConflictThatNoLongerClashesIsAppliedWithoutAsking() = runTest {
        repository.conflict = conflict(local = base, remote = base.copy(notes = "remote notes"))

        viewModel.load("a")

        assertEquals("remote notes", repository.resolutions.single().second.notes)
        assertTrue(viewModel.uiState.value.resolved)
    }

    @Test
    fun emptyValuesAreShownAsEmpty() = runTest {
        repository.conflict = conflict(
            local = base.copy(notes = ""),
            remote = base.copy(notes = "remote notes"),
        ).let { it.copy(base = it.base.copy(notes = "original")) }

        viewModel.load("a")

        assertEquals(ConflictValue.Empty, viewModel.uiState.value.fields.single().local)
    }
}
