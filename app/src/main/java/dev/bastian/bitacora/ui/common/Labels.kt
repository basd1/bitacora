package dev.bastian.bitacora.ui.common

import androidx.annotation.StringRes
import dev.bastian.bitacora.R
import dev.bastian.bitacora.domain.Category
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

fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
