package com.smarttasker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smarttasker.ui.screens.HomeScreen
import com.smarttasker.ui.screens.TaskScreen
import com.smarttasker.ui.screens.TaskEditorScreen
import com.smarttasker.ui.screens.ScriptEditorScreen
import com.smarttasker.ui.screens.StepEditorScreen
import com.smarttasker.ui.screens.TaskTestScreen
import com.smarttasker.ui.screens.ScriptImportExportScreen
import com.smarttasker.ui.screens.SettingsScreen
import com.smarttasker.ui.theme.SmartTaskerTheme
import dagger.hilt.android.AndroidEntryPoint

// 导航项
sealed class NavigationItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : NavigationItem(
        route = "home",
        title = "首页",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )
    
    object Task : NavigationItem(
        route = "task",
        title = "任务",
        selectedIcon = Icons.Filled.List,
        unselectedIcon = Icons.Outlined.List
    )
    
    object Settings : NavigationItem(
        route = "settings",
        title = "设置",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartTaskerTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    
    // 导航项列表
    val navigationItems = listOf(
        NavigationItem.Home,
        NavigationItem.Task,
        NavigationItem.Settings
    )
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                navigationItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { 
                        it.route == item.route 
                    } == true
                    
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.title
                            )
                        },
                        label = { Text(item.title) },
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavigationItem.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavigationItem.Home.route) {
                HomeScreen()
            }
            composable(NavigationItem.Task.route) {
                TaskScreen(
                    onNavigateToTaskEditor = { taskId ->
                        if (taskId != null) {
                            navController.navigate("task_editor/$taskId")
                        } else {
                            navController.navigate("task_editor")
                        }
                    },
                    onNavigateToScriptEditor = { scriptId ->
                        if (scriptId != null) {
                            navController.navigate("script_editor/$scriptId")
                        } else {
                            navController.navigate("script_editor")
                        }
                    },
                    onNavigateToTaskTest = { taskId ->
                        navController.navigate("task_test/$taskId")
                    },
                    onNavigateToScriptImportExport = {
                        navController.navigate("script_import_export")
                    }
                )
            }
            composable(NavigationItem.Settings.route) {
                SettingsScreen()
            }
            composable("task_editor/{taskId}") { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId")
                TaskEditorScreen(
                    taskId = taskId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("task_editor") {
                TaskEditorScreen(
                    taskId = null,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("script_editor/{scriptId}") { backStackEntry ->
                val scriptId = backStackEntry.arguments?.getString("scriptId")
                ScriptEditorScreen(
                    scriptId = scriptId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToStepEditor = { sId, stepIndex ->
                        navController.navigate("step_editor/$sId/$stepIndex")
                    }
                )
            }
            composable("script_editor") {
                ScriptEditorScreen(
                    scriptId = null,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToStepEditor = { sId, stepIndex ->
                        navController.navigate("step_editor/$sId/$stepIndex")
                    }
                )
            }
            composable("step_editor/{scriptId}/{stepIndex}") { backStackEntry ->
                val scriptId = backStackEntry.arguments?.getString("scriptId") ?: ""
                val stepIndex = backStackEntry.arguments?.getString("stepIndex")?.toIntOrNull() ?: 0
                StepEditorScreen(
                    scriptId = scriptId,
                    stepIndex = stepIndex,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("task_test/{taskId}") { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                TaskTestScreen(
                    taskId = taskId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("script_import_export") {
                ScriptImportExportScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
