package dev.bastian.bitacora.ui.common

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import dev.bastian.bitacora.R
import dev.bastian.bitacora.domain.Category
import dev.bastian.bitacora.domain.SyncState
import java.text.DateFormat
import java.util.Date

@StringRes
fun Category.labelRes(): Int = when (this) {
    Category.FAUNA -> R.string.category_fauna
    Category.FLORA -> R.string.category_flora
    Category.TERRAIN -> R.string.category_terrain
    Category.WEATHER -> R.string.category_weather
    Category.OTHER -> R.string.category_other
}

@StringRes
fun SyncState.labelRes(): Int = when (this) {
    SyncState.SYNCED -> R.string.sync_state_synced
    SyncState.PENDING -> R.string.sync_state_pending
    SyncState.CONFLICT -> R.string.sync_state_conflict
    SyncState.ERROR -> R.string.sync_state_error
}

fun SyncState.icon(): ImageVector = when (this) {
    SyncState.SYNCED -> Icons.Default.CloudDone
    SyncState.PENDING -> Icons.Default.CloudUpload
    SyncState.CONFLICT -> Icons.Default.Warning
    SyncState.ERROR -> Icons.Default.Error
}

fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
