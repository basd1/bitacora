package dev.bastian.bitacora.ui

import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.TitleError
import dev.bastian.bitacora.testing.FakeObservationRepository
import dev.bastian.bitacora.testing.MainDispatcherRule
import dev.bastian.bitacora.testing.observation
import dev.bastian.bitacora.ui.edit.EditViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeObservationRepository()
    private val viewModel = EditViewModel(repository)

    @Test
    fun newFormStartsEmptyAndLoaded() {
        viewModel.load(null)
        val state = viewModel.uiState.value
        assertTrue(state.isNew)
        assertFalse(state.isLoading)
        assertEquals("", state.title)
    }

    @Test
    fun blankTitleIsRejectedAndNothingIsSaved() {
        viewModel.load(null)
        viewModel.onTitleChange("   ")
        viewModel.save()
        assertEquals(TitleError.EMPTY, viewModel.uiState.value.titleError)
        assertTrue(repository.savedDrafts.isEmpty())
    }

    @Test
    fun editingTheTitleClearsItsError() {
        viewModel.load(null)
        viewModel.save()
        viewModel.onTitleChange("Heron")
        assertNull(viewModel.uiState.value.titleError)
    }

    @Test
    fun validFormIsSavedAsANewObservation() = runTest {
        viewModel.load(null)
        viewModel.onTitleChange("Heron")
        viewModel.onNotesChange("by the river")
        viewModel.onCategoryChange(Category.FAUNA)
        viewModel.save()
        val draft = repository.savedDrafts.single()
        assertNull(draft.id)
        assertEquals("Heron", draft.title)
        assertEquals(Category.FAUNA, draft.category)
        assertTrue(viewModel.uiState.value.saved)
    }

    @Test
    fun loadingAnExistingObservationFillsTheForm() = runTest {
        repository.seed(observation(id = "x", title = "Oak", notes = "tall", category = Category.FLORA))
        viewModel.load("x")
        val state = viewModel.uiState.value
        assertFalse(state.isNew)
        assertEquals("Oak", state.title)
        assertEquals("tall", state.notes)
        assertEquals(Category.FLORA, state.category)
    }

    @Test
    fun savingAnExistingObservationKeepsItsId() = runTest {
        repository.seed(observation(id = "x", title = "Oak"))
        viewModel.load("x")
        viewModel.onTitleChange("Old oak")
        viewModel.save()
        assertEquals("x", repository.savedDrafts.single().id)
    }

    @Test
    fun missingObservationIsReportedAsNotFound() = runTest {
        viewModel.load("missing")
        assertTrue(viewModel.uiState.value.notFound)
    }

    @Test
    fun deleteReportsTheDeletedId() = runTest {
        repository.seed(observation(id = "x"))
        viewModel.load("x")
        viewModel.delete()
        assertEquals("x", viewModel.uiState.value.deletedId)
    }

    @Test
    fun loadingAgainAfterConsumingTheResultStartsClean() = runTest {
        viewModel.load(null)
        viewModel.onTitleChange("Heron")
        viewModel.save()
        viewModel.consumeResult()
        viewModel.load(null)
        assertEquals("", viewModel.uiState.value.title)
        assertFalse(viewModel.uiState.value.saved)
    }
}
