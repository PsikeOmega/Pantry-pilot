package com.psike.pantrypilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import androidx.navigation.NavGraph.Companion.findStartDestination

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PantryPilotApp()
        }
    }
}

@Composable
fun PantryPilotApp() {
    MaterialTheme {
        val navController = rememberNavController()

        Scaffold(
            bottomBar = {
                NavigationBar {
                    val currentRoute =
                        navController.currentBackStackEntryAsState().value?.destination?.route

                    listOf(
                        Screen.Checklist,
                        Screen.Shopping,
                        Screen.Settings
                    ).forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {},
                            label = { Text(screen.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Checklist.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Checklist.route) { ChecklistScreen() }
                composable(Screen.Shopping.route) { ShoppingScreen() }
                composable(Screen.Settings.route) { SettingsScreen() }
            }
        }
    }
}

sealed class Screen(val route: String, val label: String) {
    object Checklist : Screen("checklist", "Checklist")
    object Shopping : Screen("shopping", "Shopping")
    object Settings : Screen("settings", "Settings")
}

@Composable
fun ChecklistScreen() {
    Text("Checklist screen")
}

@Composable
fun ShoppingScreen() {
    Text("Shopping list screen")
}

@Composable
fun SettingsScreen() {
    Text("Settings screen")
}
