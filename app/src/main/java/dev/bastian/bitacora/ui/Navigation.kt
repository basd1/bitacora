package dev.bastian.bitacora.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.bastian.bitacora.ui.list.ListScreen
import kotlinx.serialization.Serializable

@Serializable
data object ListRoute

@Composable
fun BitacoraNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ListRoute) {
        composable<ListRoute> { ListScreen() }
    }
}
