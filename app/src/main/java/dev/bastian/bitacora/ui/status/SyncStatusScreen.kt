package dev.bastian.bitacora.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bastian.bitacora.R
import dev.bastian.bitacora.ui.common.formatDateTime
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SyncStatusViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val status = state.status

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.status_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isSyncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.status_syncing), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = stringResource(if (state.isOnline) R.string.status_online else R.string.status_offline),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = status.lastSyncAt?.let { stringResource(R.string.status_last_sync, formatDateTime(it)) }
                    ?: stringResource(R.string.status_never_synced),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(stringResource(R.string.status_pending, status.pending), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.status_conflicts, status.conflicts), style = MaterialTheme.typography.bodyLarge)

            status.lastError?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.status_last_error, error),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            if (status.errors.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.status_errors_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                status.errors.forEach { item ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            Text(item.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (status.pending == 0 && status.conflicts == 0 && status.errors.isEmpty() && status.lastError == null) {
                Text(stringResource(R.string.status_all_synced), style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = viewModel::retry,
                enabled = !state.isSyncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.status_retry))
            }
        }
    }
}
