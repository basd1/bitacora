package dev.bastian.bitacora.ui

import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.SyncErrorItem
import dev.bastian.bitacora.domain.SyncStatus
import dev.bastian.bitacora.testing.FakeNetworkMonitor
import dev.bastian.bitacora.testing.FakeObservationRepository
import dev.bastian.bitacora.testing.FakeSyncScheduler
import dev.bastian.bitacora.testing.FakeSyncStatusRepository
import dev.bastian.bitacora.testing.MainDispatcherRule
import dev.bastian.bitacora.testing.observation
import dev.bastian.bitacora.ui.list.ListViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private val statusRepository = FakeSyncStatusRepository()
    private val network = FakeNetworkMonitor()
    private val scheduler = FakeSyncScheduler()
    private val viewModel by lazy { ListViewModel(repository, statusRepository, network, scheduler) }

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

    @Test
    fun reportsWhenTheDeviceIsOffline() = runTest {
        network.online.value = false
        val state = viewModel.uiState.first { it.isLoaded && !it.isOnline }
        assertFalse(state.isOnline)
    }

    @Test
    fun exposesPendingConflictAndErrorCounts() = runTest {
        statusRepository.current.value = SyncStatus(
            pending = 3,
            conflicts = 2,
            errors = listOf(SyncErrorItem("1", "Heron", "HTTP 400")),
        )
        val state = viewModel.uiState.first { it.isLoaded && it.pendingCount == 3 }
        assertEquals(2, state.conflictCount)
        assertEquals(1, state.errorCount)
    }

    @Test
    fun reportsWhileASyncIsRunning() = runTest {
        scheduler.syncing.value = true
        val state = viewModel.uiState.first { it.isLoaded && it.isSyncing }
        assertTrue(state.isSyncing)
    }

    @Test
    fun manualSyncAsksTheStatusRepositoryToRetry() = runTest {
        viewModel.syncNow()
        assertEquals(1, statusRepository.retries)
    }
}
