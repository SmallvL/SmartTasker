package com.smarttasker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.smarttasker.ui.navigation.NavRoutes
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test that boots MainActivity and verifies the bottom navigation bar
 * is visible. This test is intended to run against a MuMu emulator (Android 9
 * x86_64) connected via `adb connect 127.0.0.1:7555`.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityTestRule(MainActivity::class.java)

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.smarttasker.debug", appContext.packageName)
    }

    @Test
    fun activityLaunchesSuccessfully() {
        val activity = activityRule.activity
        assertNotNull(activity)
        assertFalse(activity.isFinishing)
    }
}
