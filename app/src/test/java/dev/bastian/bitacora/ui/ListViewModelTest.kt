package dev.bastian.bitacora.ui

import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.testing.FakeObservationRepository
import dev.bastian.bitacora.testing.MainDispatcherRule
import dev.bastian.bitacora.testing.observation
import dev.bastian.bitacora.ui.list.ListViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeObservationRepository().apply {
        seed(
            observation(id = "1", title = "Grey Heron", category = Category.FAUNA),
            observation(id = "2", title = "Oak", notes = "old tree", category = Category.FLORA),
        )
    }
    private val viewModel = ListViewModel(repository)

    @Test
    fun showsAllObservationsOnceLoaded() = runTest {
        val state = viewModel.uiState.first { it.isLoaded }
        assertEquals(listOf("1", "2"), state.items.map { it.id })
    }

    @Test
    fun filtersByQuery() = runTest {
        viewModel.onQueryChange("heron")
        val state = viewModel.uiState.first { it.isLoaded && it.query == "heron" }
        assertEquals(listOf("1"), state.items.map { it.id })
    }

    @Test
    fun filtersByCategory() = runTest {
        viewModel.onCategoryChange(Category.FLORA)
        val state = viewModel.uiState.first { it.isLoaded && it.category == Category.FLORA }
        assertEquals(listOf("2"), state.items.map { it.id })
    }

    @Test
    fun deleteRemovesTheRowAndOffersUndo() = runTest {
        viewModel.uiState.first { it.isLoaded }
        viewModel.delete("1")
        val state = viewModel.uiState.first { it.lastDeletedId != null }
        assertEquals("1", state.lastDeletedId)
        assertEquals(listOf("2"), state.items.map { it.id })
    }

    @Test
    fun undoRestoresTheRowAndClearsThePrompt() = runTest {
        viewModel.uiState.first { it.isLoaded }
        viewModel.delete("1")
        viewModel.undoDelete()
        val state = viewModel.uiState.first { it.isLoaded && it.items.size == 2 }
        assertNull(state.lastDeletedId)
    }

    @Test
    fun dismissingThePromptKeepsTheDeletion() = runTest {
        viewModel.uiState.first { it.isLoaded }
        viewModel.delete("1")
        viewModel.onDeletedPromptShown()
        val state = viewModel.uiState.first { it.isLoaded && it.lastDeletedId == null }
        assertEquals(listOf("2"), state.items.map { it.id })
    }
}
