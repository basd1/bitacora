package dev.bastian.bitacora.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.bastian.bitacora.R
import dev.bastian.bitacora.ui.conflict.ConflictScreen
import dev.bastian.bitacora.ui.edit.EditScreen
import dev.bastian.bitacora.ui.list.ListScreen
import dev.bastian.bitacora.ui.list.ListUiState
import dev.bastian.bitacora.ui.list.ListViewModel
import dev.bastian.bitacora.ui.status.SyncStatusScreen
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Serializable
data object ListRoute

@Serializable
data class EditRoute(val id: String? = null)

@Serializable
data class ConflictRoute(val id: String)

@Serializable
data object SyncStatusRoute

private const val DELETED_ID_KEY = "deletedId"

/** From this width on the list and the editor are shown side by side. */
private const val TWO_PANE_MIN_WIDTH_DP = 600

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
                onResolveConflict = { id -> navController.navigate(ConflictRoute(id)) },
            )
        }
        composable<SyncStatusRoute> {
            SyncStatusScreen(onBack = { navController.popBackStack() })
        }
        composable<ConflictRoute> { entry ->
            ConflictScreen(
                id = entry.toRoute<ConflictRoute>().id,
                onClose = { navController.popBackStack() },
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
    val twoPane = LocalConfiguration.current.screenWidthDp >= TWO_PANE_MIN_WIDTH_DP
    if (twoPane) {
        ListDetail(navController = navController, viewModel = viewModel, state = state)
    } else {
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
            onSyncNow = viewModel::syncNow,
            onOpenStatus = { navController.navigate(SyncStatusRoute) },
        )
    }
}

/** List on the left, editor on the right. The selection survives rotation. */
@Composable
private fun ListDetail(navController: NavController, viewModel: ListViewModel, state: ListUiState) {
    var paneOpen by rememberSaveable { mutableStateOf(false) }
    var paneId by rememberSaveable { mutableStateOf<String?>(null) }
    val closePane = {
        paneOpen = false
        paneId = null
    }
    Row(modifier = Modifier.fillMaxSize()) {
        ListScreen(
            state = state,
            selectedId = if (paneOpen) paneId else null,
            onQueryChange = viewModel::onQueryChange,
            onCategoryChange = viewModel::onCategoryChange,
            onOpen = { id ->
                paneId = id
                paneOpen = true
            },
            onNew = {
                paneId = null
                paneOpen = true
            },
            onDelete = { id ->
                if (id == paneId) closePane()
                viewModel.delete(id)
            },
            onUndoDelete = viewModel::undoDelete,
            onDeletedPromptShown = viewModel::onDeletedPromptShown,
            onSyncNow = viewModel::syncNow,
            onOpenStatus = { navController.navigate(SyncStatusRoute) },
            modifier = Modifier.weight(LIST_PANE_WEIGHT),
        )
        VerticalDivider()
        Box(modifier = Modifier.weight(DETAIL_PANE_WEIGHT).fillMaxSize()) {
            if (paneOpen) {
                EditScreen(
                    id = paneId,
                    onClose = closePane,
                    onDeleted = { id ->
                        closePane()
                        viewModel.announceDeleted(id)
                    },
                    onResolveConflict = { id -> navController.navigate(ConflictRoute(id)) },
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.detail_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private const val LIST_PANE_WEIGHT = 0.4f
private const val DETAIL_PANE_WEIGHT = 0.6f
