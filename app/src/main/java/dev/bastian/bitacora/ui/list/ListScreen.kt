package dev.bastian.bitacora.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bastian.bitacora.R
import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.Observation
import dev.bastian.bitacora.domain.SyncState
import dev.bastian.bitacora.ui.common.formatDateTime
import dev.bastian.bitacora.ui.common.icon
import dev.bastian.bitacora.ui.common.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    state: ListUiState,
    selectedId: String?,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (Category?) -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
    onUndoDelete: () -> Unit,
    onDeletedPromptShown: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.observation_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(state.lastDeletedId) {
        if (state.lastDeletedId == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) onUndoDelete() else onDeletedPromptShown()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.list_title)) },
                actions = {
                    IconButton(onClick = onSyncNow, enabled = !state.isSyncing) {
                        Icon(Icons.Default.Sync, contentDescription = stringResource(R.string.action_sync_now))
                    }
                    IconButton(onClick = onOpenStatus) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.action_open_status))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isSyncing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            SyncBanners(state = state, onOpenStatus = onOpenStatus)
            SearchField(initial = state.query, onQueryChange = onQueryChange)
            CategoryFilters(selected = state.category, onSelected = onCategoryChange)
            when {
                !state.isLoaded -> Unit
                state.items.isEmpty() -> {
                    val filtering = state.query.isNotBlank() || state.category != null
                    EmptyMessage(
                        title = stringResource(if (filtering) R.string.list_no_results_title else R.string.list_empty_title),
                        body = stringResource(if (filtering) R.string.list_no_results_body else R.string.list_empty_body),
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Keeps the last row clear of the floating action button.
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(state.items, key = { it.id }) { observation ->
                        ObservationRow(
                            observation = observation,
                            selected = observation.id == selectedId,
                            onClick = { onOpen(observation.id) },
                            onDelete = { onDelete(observation.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(initial: String, onQueryChange: (String) -> Unit) {
    // The text lives in the UI so typing never waits on the state round trip.
    var text by rememberSaveable { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onQueryChange(it)
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        label = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = {
                    text = ""
                    onQueryChange("")
                }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear_search))
                }
            }
        },
    )
}

@Composable
private fun CategoryFilters(selected: Category?, onSelected: (Category?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelected(null) },
            label = { Text(stringResource(R.string.filter_all)) },
        )
        Category.entries.forEach { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelected(if (selected == category) null else category) },
                label = { Text(stringResource(category.labelRes())) },
            )
        }
    }
}

@Composable
private fun ObservationRow(
    observation: Observation,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    ListItem(
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.action_open_observation), onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = container),
        headlineContent = { Text(observation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.row_subtitle,
                    stringResource(observation.category.labelRes()),
                    formatDateTime(observation.observedAt),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val problem = observation.syncState == SyncState.CONFLICT || observation.syncState == SyncState.ERROR
                Icon(
                    imageVector = observation.syncState.icon(),
                    contentDescription = stringResource(observation.syncState.labelRes()),
                    tint = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        },
    )
}

@Composable
private fun SyncBanners(state: ListUiState, onOpenStatus: () -> Unit) {
    if (!state.isOnline) {
        Banner(
            text = stringResource(R.string.banner_offline),
            icon = { Icon(Icons.Default.CloudOff, contentDescription = null) },
            onClick = onOpenStatus,
        )
    }
    if (state.conflictCount > 0) {
        Banner(text = stringResource(R.string.banner_conflicts, state.conflictCount), onClick = onOpenStatus)
    }
    if (state.errorCount > 0) {
        Banner(text = stringResource(R.string.banner_errors, state.errorCount), onClick = onOpenStatus)
    }
}

@Composable
private fun Banner(text: String, onClick: () -> Unit, icon: (@Composable () -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .clickable(onClickLabel = stringResource(R.string.action_open_status), onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            icon?.invoke()
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyMessage(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(text = body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}
