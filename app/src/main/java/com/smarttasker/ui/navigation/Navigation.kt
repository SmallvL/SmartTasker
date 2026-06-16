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
import com.smarttasker.ui.theme.SmartColors
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
import com.smarttasker.ui.trialrun.AiExecutionScreen
import com.smarttasker.ui.trialrun.TrialRunScreen
import com.smarttasker.ui.trialrun.TrialStepStatus
import com.smarttasker.ui.trialrun.TrialModeSelectScreen
import com.smarttasker.ui.trialrun.ManualRecordingScreen
import com.smarttasker.ui.trialrun.RouteLearningResultScreen
import com.smarttasker.ui.routestudio.RouteStudioScreen
import com.smarttasker.ui.routeeditor.RouteEditorViewModel
import com.smarttasker.ui.routeeditor.RouteEditorScreen
import com.smarttasker.data.repository.TemplateRepository
import com.smarttasker.ui.templates.TemplateListScreen
import com.smarttasker.ui.templates.TemplateDetailScreen
import com.smarttasker.ui.trace.TraceExplainerScreen
import com.smarttasker.ui.permission.PermissionDoctorScreen
import com.smarttasker.ui.safety.SafetyGuard
import com.smarttasker.ui.safety.SafetyConfirmDialog
import com.smarttasker.ui.settings.DebugLogScreen
import com.smarttasker.ui.tasks.TasksPage
import com.smarttasker.ui.tasks.TaskViewModel
import com.smarttasker.schedule.AlarmScheduler
import com.smarttasker.core.playback.RoutePlaybackService
import android.content.Intent
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
    object Templates : Screen("templates", "模板", Icons.Outlined.Description, Icons.Filled.Description)
    object Settings : Screen("settings", "设置", Icons.Outlined.Settings, Icons.Filled.Settings)
}

val bottomNavItems = listOf(Screen.Home, Screen.Tasks, Screen.Runs, Screen.Templates, Screen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    taskRepo: TaskRepository,
    runRepo: RunRepository,
    routeRepo: RouteRepository,
    templateRepo: TemplateRepository,
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
                    contentColor = SmartColors.accent(),
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
                                selectedIconColor = SmartColors.accent(),
                                selectedTextColor = SmartColors.accent(),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = SmartColors.accent().copy(alpha = 0.12f)
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
                                navController.navigate("settings")
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
                    executionService = executionService,
                    onCreateTask = { input ->
                        // Navigate to create with pre-filled input
                        navController.navigate("create?input=${java.net.URLEncoder.encode(input, "UTF-8")}")
                    },
                    onAiExecute = { input, appName ->
                        scope.launch {
                            val task = TaskEntity(
                                taskId = "task_${System.currentTimeMillis()}",
                                name = input.ifEmpty { "AI任务" },
                                description = input,
                                targetAppName = appName,
                                triggerType = "manual",
                                status = "draft",
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                            taskRepo.insertTask(task)
                            navController.navigate("ai_execution/${task.taskId}")
                        }
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
                    },
                    onNavigateToTrace = { runId ->
                        navController.navigate("trace_explainer/$runId")
                    },
                    onDeleteTask = { task ->
                        scope.launch {
                            taskRepo.deleteTask(task)
                        }
                    },
                    onToggleTask = { task ->
                        scope.launch {
                            val newStatus = when (task.status) {
                                "active" -> "paused"
                                "paused" -> "active"
                                "draft" -> "active"
                                else -> "active"
                            }
                            taskRepo.updateTask(task.copy(status = newStatus, updatedAt = System.currentTimeMillis()))
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

            // ===== Runs (with Stats tab) =====
            composable(Screen.Runs.route) {
                RunListScreen(
                    runRepo = runRepo,
                    taskRepo = taskRepo,
                    onRunClick = { run ->
                        if (run.status == "failed") {
                            navController.navigate("trace_explainer/${run.runId}")
                        }
                    }
                )
            }

            // ===== Templates =====
            composable(Screen.Templates.route) {
                TemplateListScreen(
                    templateRepo = templateRepo,
                    routeRepo = routeRepo,
                    onTemplateClick = { templateId ->
                        navController.navigate("template_detail/$templateId")
                    },
                    onCreateFromTemplate = { templateId ->
                        navController.navigate("create?input=&templateId=$templateId")
                    }
                )
            }

            // ===== Template Detail =====
            composable(
                "template_detail/{templateId}",
                arguments = listOf(navArgument("templateId") { type = NavType.StringType })
            ) { backStackEntry ->
                val templateId = backStackEntry.arguments?.getString("templateId") ?: ""
                TemplateDetailScreen(
                    templateId = templateId,
                    templateRepo = templateRepo,
                    routeRepo = routeRepo,
                    onBack = { navController.popBackStack() },
                    onCreateTask = { tid ->
                        navController.navigate("create?input=&templateId=$tid")
                    }
                )
            }

             // ===== Settings =====
             composable("settings") {
                SettingsScreen(
                    settingsRepo = settingsRepo,
                    runRepo = runRepo,
                    coreBridgeManager = coreBridgeManager,
                    onNavigateToPermissions = { navController.navigate("permissions") },
                    onNavigateToDebugLog = { navController.navigate("debug_log") }
                )
            }

            // ===== Create Task =====
            composable(
                "create?input={input}&templateId={templateId}",
                arguments = listOf(
                    navArgument("input") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("templateId") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val initialInput = backStackEntry.arguments?.getString("input") ?: ""
                val templateId = backStackEntry.arguments?.getString("templateId") ?: ""

                CreateTaskScreen(
                    taskRepo = taskRepo,
                    coreBridgeManager = coreBridgeManager,
                    settingsRepo = settingsRepo,
                    templateRepo = templateRepo,
                    routeRepo = routeRepo,
                    initialInput = java.net.URLDecoder.decode(initialInput, "UTF-8"),
                    templateId = templateId,
                    onTaskCreated = { task, fromTemplate ->
                        scope.launch {
                            taskRepo.insertTask(task)
                            if (fromTemplate) {
                                // 模板创建的任务已有路线，直接跳转到任务详情
                                navController.navigate("task_detail/${task.taskId}") {
                                    popUpTo("create?input={input}&templateId={templateId}") { inclusive = true }
                                }
                            } else {
                                // 非模板任务需要试跑学习路线
                                navController.navigate("trial/${task.taskId}") {
                                    popUpTo("create?input={input}&templateId={templateId}") { inclusive = true }
                                }
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
                    templateRepo = templateRepo,
                    onBack = { navController.popBackStack() },
                    onOpenRouteStudio = { routeId, taskName -> navController.navigate("route_studio/$routeId/${java.net.URLEncoder.encode(taskId, "UTF-8")}?taskName=${java.net.URLEncoder.encode(taskName, "UTF-8")}") },
                    onStartTrial = { task -> navController.navigate("trial/${task.taskId}") },
                    onRunClick = { run ->
                        if (run.status == "failed") {
                            navController.navigate("trace_explainer/${run.runId}")
                        }
                    }
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
                            AiExecutionScreen(
                                task = task!!,
                                executionService = executionService,
                                onComplete = { success, routeId ->
                                    if (success && routeId != null) {
                                        navController.navigate("learning_result/${task!!.taskId}?routeId=$routeId") {
                                            popUpTo("trial/${task!!.taskId}") { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate("learning_result/${task!!.taskId}?routeId=") {
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

            // ===== AI Execution =====
            composable(
                "ai_execution/{taskId}",
                arguments = listOf(navArgument("taskId") { type = NavType.StringType })
            ) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                var task by remember { mutableStateOf<TaskEntity?>(null) }
                LaunchedEffect(taskId) { task = taskRepo.getTaskById(taskId) }

                if (task != null) {
                    AiExecutionScreen(
                        task = task!!,
                        executionService = executionService,
                        onComplete = { success, routeId ->
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                            }
                        },
                        onCancel = { navController.popBackStack() }
                    )
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
                                // For manual tasks, just save and go home — don't auto-execute
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
                    val runForStudio = runData!!  // capture non-null
                    val scopeForRoute = rememberCoroutineScope()
                    // Collect latest published route for this task
                    val taskRoutes by routeRepo.getRouteVersions(runForStudio.taskId).collectAsState(initial = emptyList())
                    // Resolve task name on demand (RunRecordEntity does not store it)
                    var resolvedTaskName by remember { mutableStateOf("任务") }
                    LaunchedEffect(runForStudio.taskId) {
                        if (runForStudio.taskId.isNotEmpty()) {
                            val t = taskRepo.getTaskById(runForStudio.taskId)
                            if (t != null) resolvedTaskName = t.name
                        }
                    }
                    TraceExplainerScreen(
                        run = runData!!,
                        traceEvents = traceEvents,
                        onOpenRouteStudio = {
                            val tId = runForStudio.taskId
                            if (tId.isNotEmpty()) {
                                val published = taskRoutes.firstOrNull { it.status == "published" }
                                val targetRoute = published ?: taskRoutes.firstOrNull()
                                val targetRouteId = targetRoute?.routeId ?: tId
                                val encTaskName = java.net.URLEncoder.encode(resolvedTaskName.ifEmpty { "任务" }, "UTF-8")
                                navController.navigate("route_studio/$targetRouteId/$tId?taskName=$encTaskName")
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // ===== Debug Log =====
            composable("debug_log") {
                DebugLogScreen(
                    onBack = { navController.popBackStack() }
                )
            }

        } // NavHost
    } // Column
    } // Scaffold
}
