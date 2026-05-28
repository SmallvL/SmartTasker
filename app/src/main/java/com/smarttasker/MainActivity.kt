package com.smarttasker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.smarttasker.core.bridge.CoreBridgeManager
import com.smarttasker.data.database.AppDatabase
import com.smarttasker.data.repository.TaskRepository
import com.smarttasker.data.repository.RunRepository
import com.smarttasker.data.repository.RouteRepository
import com.smarttasker.data.repository.SettingsRepository
import com.smarttasker.data.repository.TraceEventRepository
import com.smarttasker.service.TaskExecutionService
import com.smarttasker.ui.navigation.MainNavigation
import com.smarttasker.ui.theme.SmartTaskerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getInstance(applicationContext)
        val taskRepo = TaskRepository(db.taskDao())
        val runRepo = RunRepository(db.runRecordDao())
        val routeRepo = RouteRepository(db.routeDao())
        val settingsRepo = SettingsRepository(applicationContext)
        val traceEventRepo = TraceEventRepository(db.traceDao())
        
        // Initialize CoreBridge
        val coreBridgeManager = CoreBridgeManager.getInstance(applicationContext)
        coreBridgeManager.useDirectBridge()
        
        // Task execution service
        val executionService = TaskExecutionService(
            context = applicationContext,
            routeRepository = routeRepo,
            runRepository = runRepo
        )

        setContent {
            SmartTaskerTheme {
                MainNavigation(
                    taskRepo = taskRepo,
                    runRepo = runRepo,
                    routeRepo = routeRepo,
                    settingsRepo = settingsRepo,
                    traceEventRepo = traceEventRepo,
                    coreBridgeManager = coreBridgeManager,
                    executionService = executionService
                )
            }
        }
    }
}
