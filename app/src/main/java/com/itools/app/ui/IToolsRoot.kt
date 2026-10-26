package com.itools.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.itools.app.ui.editor.EditorScreen
import com.itools.app.ui.home.HomeScreen
import com.itools.app.ui.settings.SettingsScreen

@Composable
fun IToolsRoot() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenProject = { id -> navController.navigate("editor/$id") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable(
            route = "editor/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) {
            EditorScreen(onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
