package com.smarttasker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smarttasker.core.bridge.CoreBridgeManager
import com.smarttasker.core.bridge.CoreStatus
import com.smarttasker.data.entity.TaskEntity
import com.smarttasker.data.entity.RunRecordEntity
import com.smarttasker.data.repository.TaskRepository
import com.smarttasker.data.repository.RunRepository
import com.smarttasker.data.repository.RouteRepository
import com.smarttasker.data.repository.SettingsRepository
import com.smarttasker.data.repository.TraceEventRepository
import com.smarttasker.service.TaskExecutionService
import com.smarttasker.ui.home.HomeScreen
import com.smarttasker.ui.tasks.TaskDetailScreen
import com.smarttasker.ui.runs.RunListScreen
import com.smarttasker.ui.settings.SettingsScreen
import com.smarttasker.ui.create.CreateTaskScreen
import com.smarttasker.ui.trialrun.TrialRunScreen
import com.smarttasker.ui.trialrun.TrialStepStatus
import com.smarttasker.ui.trialrun.TrialModeSelectScreen
import com.smarttasker.ui.trialrun.ManualRecordingScreen
import com.smarttasker.ui.trialrun.RouteLearningResultScreen
import com.smarttasker.ui.routestudio.RouteStudioScreen
import com.smarttasker.ui.routeeditor.RouteEditorViewModel
import com.smarttasker.ui.routeeditor.RouteEditorScreen
import com.smarttasker.ui.stats.StatsViewModel
import com.smarttasker.ui.stats.StatsScreen
import com.smarttasker.ui.trace.TraceExplainerScreen
import com.smarttasker.ui.permission.PermissionDoctorScreen
import com.smarttasker.ui.safety.SafetyGuard
import com.smarttasker.ui.safety.SafetyConfirmDialog
import com.smarttasker.ui.settings.SafetyPolicyScreen
import com.smarttasker.ui.settings.CostBudgetScreen
import com.smarttasker.ui.settings.ModelConfigScreen
import com.smarttasker.ui.settings.PromptSettingsScreen
import com.smarttasker.ui.settings.CoreControlScreen
import com.smarttasker.ui.settings.DebugLogScreen
import com.smarttasker.ui.tasks.TasksPage
import com.smarttasker.ui.tasks.TaskViewModel
import com.smarttasker.ui.settings.DeviceInfoScreen
import com.smarttasker.ui.settings.ImportExportScreen
import com.smarttasker.ui.settings.AboutScreen
import com.smarttasker.schedule.AlarmScheduler
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
) {
    object Home : Screen("home", "首页", Icons.Outlined.Home, Icons.Filled.Home)
    object Tasks : Screen("tasks", "任务", Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle)
    object Runs : Screen("runs", "记录", Icons.Outlined.History, Icons.Filled.History)
    object Settings : Screen("settings", "设置", Icons.Outlined.Settings, Icons.Filled.Settings)
    object Stats : Screen("stats", "统计", Icons.Outlined.BarChart, Icons.Filled.BarChart)
}

val bottomNavItems = listOf(Screen.Home, Screen.Tasks, Screen.Runs, Screen.Stats, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    taskRepo: TaskRepository,
    runRepo: RunRepository,
    routeRepo: RouteRepository,
    settingsRepo: SettingsRepository,
    traceEventRepo: TraceEventRepository,
    coreBridgeManager: CoreBridgeManager,
    executionService: TaskExecutionService
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val scope = rememberCoroutineScope()

    // TaskViewModel for the tasks tab
    val taskViewModel = remember { TaskViewModel(taskRepo) }

    val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = { Icon(if (selected) screen.selectedIcon else screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title, fontSize = 11.sp) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        val coreStatus by coreBridgeManager.coreStatus.collectAsState()
        val isCoreDisconnected = coreStatus is CoreStatus.Stopped || coreStatus is CoreStatus.Error

        Column(
            modifier = if (showBottomBar) Modifier.padding(padding) else Modifier
        ) {
            // Global warning banner when Core is not connected
            if (isCoreDisconnected && showBottomBar) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = androidx.compose.ui.graphics.Color(0xFFFFF3CD),
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color(0xFF856404),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "Core 未连接 — 任务将无法执行",
                            fontSize = 13.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF856404),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "去连接",
                            fontSize = 13.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF0066CC),
                            modifier = Modifier.clickable {
                                navController.navigate("core_control")
                            }
                        )
                    }
                }
            }

            NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.weight(1f)
        ) {
            // ===== Home =====
            composable(Screen.Home.route) {
                HomeScreen(
                    taskRepo = taskRepo,
                    runRepo = runRepo,
                    coreBridgeManager = coreBridgeManager,
                    onCreateTask = { input ->
                        // Navigate to create with pre-filled input
                        navController.navigate("create?input=${java.net.URLEncoder.encode(input, "UTF-8")}")
                    },
                    onTaskClick = { navController.navigate("task_detail/${it.taskId}") },
                    onNavigateToTaskList = {
                        navController.navigate(Screen.Tasks.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            // ===== Tasks =====
            composable(Screen.Tasks.route) {
                val tasks by taskViewModel.filteredTasks.collectAsState()
                val counts by taskViewModel.taskCounts.collectAsState()
                val filter by taskViewModel.currentFilter.collectAsState()

                TasksPage(
                    tasks = tasks,
                    currentFilter = filter,
                    taskCounts = counts,
                    onFilterChange = { taskViewModel.setFilter(it) },
                    onTaskClick = { taskId ->
                        navController.navigate("task_detail/$taskId")
                    },
                    onCreateTask = {
                        navController.navigate("create?input=")
                    },
                    onQuickTask = {
                        navController.navigate("create?input=")
                    }
                )
            }

            // ===== Runs =====
            composable(Screen.Runs.route) {
                RunListScreen(
                    runRepo = runRepo,
                    onRunClick = { run ->
                        if (run.status == "failed") {
                            navController.navigate("trace_explainer/${run.runId}")
                        }
                    }
                )
            }
             // ===== Stats =====
             composable(Screen.Stats.route) {
                 val statsCtx = androidx.compose.ui.platform.LocalContext.current
                 val statsViewModel = remember {
                     StatsViewModel(
                         application = statsCtx.applicationContext as android.app.Application,
                         runRepo = runRepo,
                         taskRepo = taskRepo
                     )
                 }
                 StatsScreen(
                     viewModel = statsViewModel,
                     onBack = { navController.popBackStack() }
                 )
             }

             // ===== Settings =====
             composable("settings") {
                SettingsScreen(
                    onNavigateToPermissions = { navController.navigate("permissions") },
                    onNavigateToSafetyPolicy = { navController.navigate("safety_policy") },
                    onNavigateToCostBudget = { navController.navigate("cost_budget") },
                    onNavigateToModelConfig = { navController.navigate("model_config") },
                    onNavigateToPromptSettings = { navController.navigate("prompt_settings") },
                    onNavigateToCoreStart = { navController.navigate("core_control") },
                    onNavigateToDeviceInfo = { navController.navigate("device_info") },
                    onNavigateToImportExport = { navController.navigate("import_export") },
                    onNavigateToAbout = { navController.navigate("about") },
                    onNavigateToDebugLog = { navController.navigate("debug_log") }
                )
            }

            // ===== Create Task =====
            composable(
                "create?input={input}",
                arguments = listOf(navArgument("input") {
                    type = NavType.StringType
                    defaultValue = ""
                })
            ) { backStackEntry ->
                val initialInput = backStackEntry.arguments?.getString("input") ?: ""
                CreateTaskScreen(
                    taskRepo = taskRepo,
                    coreBridgeManager = coreBridgeManager,
                    settingsRepo = settingsRepo,
                    initialInput = java.net.URLDecoder.decode(initialInput, "UTF-8"),
                    onTaskCreated = { task ->
                        scope.launch {
                            taskRepo.insertTask(task)
                            navController.navigate("trial/${task.taskId}") {
                                popUpTo("create?input={input}") { inclusive = true }
                            }
                        }
                    },
                    onCancel = { navController.popBackStack() }
                )
            }

            // ===== Task Detail =====
            composable(
                "task_detail/{taskId}",
                arguments = listOf(navArgument("taskId") { type = NavType.StringType })
            ) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                TaskDetailScreen(
                    taskId = taskId,
                    taskRepo = taskRepo,
                    runRepo = runRepo,
                    routeRepo = routeRepo,
                    onBack = { navController.popBackStack() },
                    onOpenRouteStudio = { routeId, taskName -> navController.navigate("route_studio/$routeId/${java.net.URLEncoder.encode(taskId, "UTF-8")}?taskName=${java.net.URLEncoder.encode(taskName, "UTF-8")}") },
                    onStartTrial = { task -> navController.navigate("trial/${task.taskId}") }
                )
            }

            // ===== Trial Run =====
            composable(
                "trial/{taskId}",
                arguments = listOf(navArgument("taskId") { type = NavType.StringType })
            ) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                var task by remember { mutableStateOf<TaskEntity?>(null) }

                LaunchedEffect(taskId) {
                    try {
                        com.smarttasker.util.DebugLog.i("Nav", "TrialRun: loading task $taskId")
                        task = taskRepo.getTaskById(taskId)
                        com.smarttasker.util.DebugLog.i("Nav", "TrialRun: task loaded = ${task != null}")
                    } catch (e: Exception) {
                        com.smarttasker.util.DebugLog.e("Nav", "TrialRun: load error: ${e.message}")
                    }
                }

                if (task != null) {
                    var selectedMode by remember { mutableStateOf<String?>(null) }

                    when (selectedMode) {
                        null -> {
                            // Mode selection screen
                            TrialModeSelectScreen(
                                taskName = task!!.name,
                                onAiMode = { selectedMode = "ai" },
                                onManualMode = { selectedMode = "manual" },
                                onCancel = { navController.popBackStack() }
                            )
                        }
                        "ai" -> {
                            TrialRunScreen(
                                task = task!!,
                                executionService = executionService,
                                onComplete = { steps ->
                                    scope.launch {
                                        val typeSummaries = steps
                                            .filter { it.status == TrialStepStatus.SUCCESS }
                                            .map { step ->
                                                // Infer step type from summary
                                                val type = when {
                                                    step.summary.contains("打开") -> "open_app"
                                                    step.summary.contains("输入") || step.summary.contains("搜索") -> "input"
                                                    step.summary.contains("滑动") -> "swipe"
                                                    step.summary.contains("等待") -> "wait"
                                                    step.summary.contains("返回") -> "back"
                                                    else -> "tap"
                                                }
                                                type to step.summary
                                            }
                                        val routeId = if (typeSummaries.isNotEmpty()) {
                                            routeRepo.saveFromTrialSteps(task!!.taskId, typeSummaries)
                                        } else ""
                                        navController.navigate("learning_result/${task!!.taskId}?routeId=$routeId") {
                                            popUpTo("trial/${task!!.taskId}") { inclusive = true }
                                        }
                                    }
                                },
                                onCancel = { navController.popBackStack() }
                            )
                        }
                        "manual" -> {
                            ManualRecordingScreen(
                                task = task!!,
                                onRecordingComplete = { routeDraft ->
                                    scope.launch {
                                        val routeId = routeRepo.saveFromDraft(routeDraft, task!!.taskId)
                                        navController.navigate("learning_result/${task!!.taskId}?routeId=$routeId") {
                                            popUpTo("trial/${task!!.taskId}") { inclusive = true }
                                        }
                                    }
                                },
                                onCancel = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }

            // ===== Learning Result =====
            composable(
                "learning_result/{taskId}?routeId={routeId}",
                arguments = listOf(
                    navArgument("taskId") { type = NavType.StringType },
                    navArgument("routeId") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                val routeId = backStackEntry.arguments?.getString("routeId") ?: ""
                var task by remember { mutableStateOf<TaskEntity?>(null) }
                val ctx = androidx.compose.ui.platform.LocalContext.current
                LaunchedEffect(taskId) { task = taskRepo.getTaskById(taskId) }

                if (task != null) {
                    RouteLearningResultScreen(
                        task = task!!,
                        routeId = routeId,
                        routeRepo = routeRepo,
                        onSaveAndEnable = {
                            scope.launch {
                                // Publish route first
                                val route = routeRepo.getRouteById(routeId)
                                if (route != null) {
                                    routeRepo.publishRoute(route)
                                }
                                taskRepo.activateTask(task!!)
                                // Schedule alarm if this is a scheduled task
                                if (task!!.triggerType == "schedule") {
                                    AlarmScheduler.scheduleAlarm(ctx, task!!, routeId)
                                }
                                navController.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = true } }
                            }
                        },
                        onOpenRouteStudio = { rid ->
                            navController.navigate("route_studio/$rid/${task!!.taskId}?taskName=${java.net.URLEncoder.encode(task!!.name, "UTF-8")}")
                        },
                        onDiscard = {
                            scope.launch {
                                if (routeId.isNotEmpty()) {
                                    // Clean up DB
                                    routeRepo.deleteRoute(routeId)
                                    // Clean up draft files
                                    try {
                                        com.smarttasker.core.record.RouteDraftStore(ctx).delete(routeId)
                                    } catch (_: Exception) {}
                                }
                                navController.popBackStack()
                            }
                        }
                    )
                }
            }

            // ===== Route Studio =====
            composable(
                "route_studio/{routeId}/{taskId}?taskName={taskName}",
                arguments = listOf(
                    navArgument("routeId") { type = NavType.StringType },
                    navArgument("taskId") { type = NavType.StringType },
                    navArgument("taskName") { type = NavType.StringType; defaultValue = "" }
                )
            ) { backStackEntry ->
                val routeId = backStackEntry.arguments?.getString("routeId") ?: ""
                val taskName = java.net.URLDecoder.decode(
                    backStackEntry.arguments?.getString("taskName") ?: "", "UTF-8"
                ).ifEmpty { backStackEntry.arguments?.getString("taskId") ?: "" }
                RouteStudioScreen(
                    routeId = routeId,
                    taskName = taskName,
                    routeRepo = routeRepo,
                    runRepo = runRepo,
                    coreBridgeManager = coreBridgeManager,
                    onBack = { navController.popBackStack() },
                    onOpenRouteEditor = { rid -> navController.navigate("route_editor/$rid") }
                )
                }

                // ===== Route Editor =====
                composable(
                 "route_editor/{routeId}",
                 arguments = listOf(
                     navArgument("routeId") { type = NavType.StringType }
                 )
                ) { backStackEntry ->
                    val routeId = backStackEntry.arguments?.getString("routeId") ?: ""
                    val editorCtx = androidx.compose.ui.platform.LocalContext.current
                    val routeEditorViewModel = remember {
                        RouteEditorViewModel(
                            application = editorCtx.applicationContext as android.app.Application,
                            routeRepo = routeRepo,
                            routeId = routeId
                        )
                    }
                 RouteEditorScreen(
                     viewModel = routeEditorViewModel,
                     onNavigateBack = { navController.popBackStack() }
                 )
                }

                // ===== Permission Doctor =====
            composable("permissions") {
                PermissionDoctorScreen(
                    coreBridgeManager = coreBridgeManager,
                    onBack = { navController.popBackStack() }
                )
            }

            // ===== Safety Policy =====
            composable("safety_policy") {
                SafetyPolicyScreen(
                    settingsRepo = settingsRepo,
                    onBack = { navController.popBackStack() }
                )
            }

            // ===== Trace Explainer =====
            composable(
                "trace_explainer/{runId}",
                arguments = listOf(navArgument("runId") { type = NavType.StringType })
            ) { backStackEntry ->
                val runId = backStackEntry.arguments?.getString("runId") ?: return@composable
                var runData by remember { mutableStateOf<RunRecordEntity?>(null) }
                
                LaunchedEffect(runId) {
                    runData = runRepo.getRunById(runId)
                }
                
                if (runData != null) {
                    val traceEvents by traceEventRepo.getEventsForRun(runId).collectAsState(initial = emptyList())
                    TraceExplainerScreen(
                        run = runData!!,
                        traceEvents = traceEvents,
                        onOpenRouteStudio = {
                            navController.navigate("route_studio/${runData!!.taskId}")
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // ===== Cost Budget =====
            composable("cost_budget") {
                CostBudgetScreen(
                    runRepo = runRepo,
                    settingsRepo = settingsRepo,
                    onBack = { navController.popBackStack() }
                )
            }

            // ===== Model Config =====
            composable("model_config") {
                ModelConfigScreen(
                    settingsRepo = settingsRepo,
                    onBack = { navController.popBackStack() }
                )
            }

            // ===== Prompt Settings =====
            composable("prompt_settings") {
                PromptSettingsScreen(
                    settingsRepo = settingsRepo,
                    onBack = { navController.popBackStack() }
                )
            }

            // ===== Core Control =====
            composable("core_control") {
                CoreControlScreen(
                    coreBridgeManager = coreBridgeManager,
                    settingsRepo = settingsRepo,
                    onBack = { navController.popBackStack() }
                )
            }

            // ===== Debug Log =====
            composable("debug_log") {
                DebugLogScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // ===== Device Info =====
            composable("device_info") {
                DeviceInfoScreen(
                    coreBridgeManager = coreBridgeManager,
                    onBack = { navController.popBackStack() }
                )
            }

            // ===== Import/Export =====
            composable("import_export") {
                ImportExportScreen(
                    settingsRepo = settingsRepo,
                    onBack = { navController.popBackStack() }
                )
            }

            // ===== About =====
            composable("about") {
                AboutScreen(onBack = { navController.popBackStack() })
            }
        } // NavHost
    } // Column
    } // Scaffold
}
