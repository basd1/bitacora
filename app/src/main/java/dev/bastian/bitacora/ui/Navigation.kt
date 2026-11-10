package dev.bastian.bitacora.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.bastian.bitacora.ui.edit.EditScreen
import dev.bastian.bitacora.ui.list.ListScreen
import dev.bastian.bitacora.ui.list.ListViewModel
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Serializable
data object ListRoute

@Serializable
data class EditRoute(val id: String? = null)

private const val DELETED_ID_KEY = "deletedId"

@Composable
fun BitacoraNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ListRoute) {
        composable<ListRoute> { entry -> ListDestination(navController, entry) }
        composable<EditRoute> { entry ->
            EditScreen(
                id = entry.toRoute<EditRoute>().id,
                onClose = { navController.popBackStack() },
                onDeleted = { id ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(DELETED_ID_KEY, id)
                    navController.popBackStack()
                },
            )
        }
    }
}

@Composable
private fun ListDestination(navController: NavController, entry: NavBackStackEntry) {
    val viewModel: ListViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val deletedElsewhere by entry.savedStateHandle
        .getStateFlow<String?>(DELETED_ID_KEY, null)
        .collectAsStateWithLifecycle()
    LaunchedEffect(deletedElsewhere) {
        deletedElsewhere?.let { id ->
            viewModel.announceDeleted(id)
            entry.savedStateHandle[DELETED_ID_KEY] = null
        }
    }
    ListScreen(
        state = state,
        selectedId = null,
        onQueryChange = viewModel::onQueryChange,
        onCategoryChange = viewModel::onCategoryChange,
        onOpen = { id -> navController.navigate(EditRoute(id)) },
        onNew = { navController.navigate(EditRoute()) },
        onDelete = viewModel::delete,
        onUndoDelete = viewModel::undoDelete,
        onDeletedPromptShown = viewModel::onDeletedPromptShown,
    )
}
