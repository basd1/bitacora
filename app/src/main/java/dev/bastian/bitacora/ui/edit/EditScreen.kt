package dev.bastian.bitacora.ui.edit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.bastian.bitacora.R
import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.CoordinateError
import dev.bastian.bitacora.domain.SyncState
import dev.bastian.bitacora.domain.TitleError
import dev.bastian.bitacora.ui.common.findActivity
import dev.bastian.bitacora.ui.common.labelRes
import dev.bastian.bitacora.ui.common.openAppSettings
import java.io.File
import org.koin.androidx.compose.koinViewModel

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    id: String?,
    onClose: () -> Unit,
    onDeleted: (String) -> Unit,
    onResolveConflict: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.load(id) }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.consumeResult()
            onClose()
        }
    }
    LaunchedEffect(state.deletedId) {
        state.deletedId?.let { deletedId ->
            viewModel.consumeResult()
            onDeleted(deletedId)
        }
    }
    LaunchedEffect(state.notFound) {
        if (state.notFound) onClose()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isNew) R.string.edit_title_new else R.string.edit_title_existing))
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancel()
                        onClose()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                    IconButton(onClick = viewModel::save, enabled = !state.isLoading && !state.isSaving) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            EditForm(
                state = state,
                viewModel = viewModel,
                onResolveConflict = { id?.let(onResolveConflict) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun EditForm(
    state: EditUiState,
    viewModel: EditViewModel,
    onResolveConflict: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.syncState == SyncState.CONFLICT) {
            ConflictBanner(onResolve = onResolveConflict)
        }
        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_title)) },
            singleLine = true,
            isError = state.titleError != null,
            supportingText = state.titleError?.let { error ->
                { Text(stringResource(if (error == TitleError.EMPTY) R.string.error_title_empty else R.string.error_title_too_long)) }
            },
        )
        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_notes)) },
            minLines = 3,
            isError = state.notesError,
            supportingText = if (state.notesError) {
                { Text(stringResource(R.string.error_notes_too_long)) }
            } else {
                null
            },
        )
        Text(stringResource(R.string.field_category), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Category.entries.forEach { category ->
                FilterChip(
                    selected = state.category == category,
                    onClick = { viewModel.onCategoryChange(category) },
                    label = { Text(stringResource(category.labelRes())) },
                )
            }
        }
        PhotoSection(state = state, viewModel = viewModel)
        LocationSection(state = state, viewModel = viewModel)
    }
}

@Composable
private fun PhotoSection(state: EditUiState, viewModel: EditViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onPhotoPicked(uri?.toString())
    }
    val pickPhoto = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    Text(stringResource(R.string.field_photo), style = MaterialTheme.typography.labelLarge)
    val photoPath = state.photoPath
    if (photoPath != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(photoPath))
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.photo_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(PHOTO_HEIGHT),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickPhoto() }) { Text(stringResource(R.string.action_change_photo)) }
            TextButton(onClick = viewModel::onPhotoRemoved) { Text(stringResource(R.string.action_remove_photo)) }
        }
    } else {
        OutlinedButton(onClick = { pickPhoto() }) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.action_add_photo), modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (state.photoError) {
        Text(
            text = stringResource(R.string.error_photo_import),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LocationSection(state: EditUiState, viewModel: EditViewModel) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val granted = grants.values.any { it }
        val activity = context.findActivity()
        // Right after a denial, "no rationale" means the system will not ask again.
        val permanentlyDenied = !granted && activity != null &&
            LOCATION_PERMISSIONS.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        viewModel.onLocationPermissionResult(granted, permanentlyDenied)
    }

    Text(stringResource(R.string.field_location), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.latitude,
            onValueChange = viewModel::onLatitudeChange,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.field_latitude)) },
            singleLine = true,
            isError = state.coordinatesError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = state.longitude,
            onValueChange = viewModel::onLongitudeChange,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.field_longitude)) },
            singleLine = true,
            isError = state.coordinatesError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
    state.coordinatesError?.let { error ->
        Text(
            text = stringResource(
                when (error) {
                    CoordinateError.INCOMPLETE -> R.string.error_coordinates_incomplete
                    CoordinateError.NOT_A_NUMBER -> R.string.error_coordinates_invalid
                    CoordinateError.OUT_OF_RANGE -> R.string.error_coordinates_range
                },
            ),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    val fetching = state.locationStatus == LocationStatus.FETCHING
    OutlinedButton(
        onClick = {
            if (hasLocationPermission(context)) {
                viewModel.fetchLocation()
            } else {
                permissionLauncher.launch(LOCATION_PERMISSIONS)
            }
        },
        enabled = !fetching,
    ) {
        if (fetching) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(stringResource(R.string.action_use_location), modifier = Modifier.padding(start = 8.dp))
    }
    LocationStatusMessage(status = state.locationStatus, onOpenSettings = { context.openAppSettings() })
}

@Composable
private fun LocationStatusMessage(status: LocationStatus, onOpenSettings: () -> Unit) {
    val message = when (status) {
        LocationStatus.IDLE, LocationStatus.FETCHING -> return
        LocationStatus.DENIED -> R.string.location_denied
        LocationStatus.PERMANENTLY_DENIED -> R.string.location_denied_permanently
        LocationStatus.PROVIDER_DISABLED -> R.string.location_provider_disabled
        LocationStatus.UNAVAILABLE -> R.string.location_unavailable
    }
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    if (status == LocationStatus.PERMANENTLY_DENIED) {
        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_open_settings)) }
    }
}

private fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private val PHOTO_HEIGHT = 200.dp

@Composable
private fun ConflictBanner(onResolve: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.conflict_banner_body),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onResolve) { Text(stringResource(R.string.action_resolve_conflict)) }
        }
    }
}
