package dev.bastian.bitacora.ui.conflict

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bastian.bitacora.R
import dev.bastian.bitacora.domain.Field
import dev.bastian.bitacora.domain.Side
import dev.bastian.bitacora.ui.common.formatDateTime
import dev.bastian.bitacora.ui.common.labelRes
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictScreen(
    id: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConflictViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.load(id) }
    LaunchedEffect(state.resolved, state.notFound) {
        if (state.resolved || state.notFound) onClose()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.conflict_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = stringResource(R.string.conflict_intro),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.fields, key = { it.field }) { field ->
                    FieldConflictCard(field = field, onChoose = { side -> viewModel.choose(field.field, side) })
                }
            }
            Button(
                onClick = viewModel::resolve,
                enabled = !state.isLoading && !state.isResolving && state.fields.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Text(stringResource(R.string.conflict_apply))
            }
        }
    }
}

@Composable
private fun FieldConflictCard(field: ConflictFieldUi, onChoose: (Side) -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(field.field.fieldLabelRes()), style = MaterialTheme.typography.titleSmall)
            Column(modifier = Modifier.selectableGroup()) {
                ChoiceRow(
                    label = stringResource(R.string.conflict_keep_mine),
                    value = field.local,
                    selected = field.choice == Side.LOCAL,
                    onSelect = { onChoose(Side.LOCAL) },
                )
                ChoiceRow(
                    label = stringResource(R.string.conflict_take_theirs),
                    value = field.remote,
                    selected = field.choice == Side.REMOTE,
                    onSelect = { onChoose(Side.REMOTE) },
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, value: ConflictValue, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(valueLabel(value), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun valueLabel(value: ConflictValue): String = when (value) {
    ConflictValue.Empty -> stringResource(R.string.conflict_value_empty)
    is ConflictValue.Text -> value.value
    is ConflictValue.CategoryValue -> stringResource(value.category.labelRes())
    is ConflictValue.Moment -> formatDateTime(value.millis)
}

@StringRes
private fun Field.fieldLabelRes(): Int = when (this) {
    Field.TITLE -> R.string.field_title
    Field.NOTES -> R.string.field_notes
    Field.CATEGORY -> R.string.field_category
    Field.LATITUDE -> R.string.field_latitude
    Field.LONGITUDE -> R.string.field_longitude
    Field.OBSERVED_AT -> R.string.field_observed_at
}
