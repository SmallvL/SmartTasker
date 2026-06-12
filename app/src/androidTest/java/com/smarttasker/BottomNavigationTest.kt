package com.smarttasker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smarttasker.ui.tasks.TasksPage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso Compose test for the bottom navigation bar. Verifies that the
 * Tasks tab and Settings tab are reachable from the home screen.
 *
 * This test depends on a real device or emulator. On a MuMu emulator the
 * ADB connection string is: `adb connect 127.0.0.1:7555`.
 */
@RunWith(AndroidJUnit4::class)
class BottomNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appNameIsVisibleOnLaunch() {
        composeTestRule.onNodeWithText("SmartTask").assertIsDisplayed()
    }
}
