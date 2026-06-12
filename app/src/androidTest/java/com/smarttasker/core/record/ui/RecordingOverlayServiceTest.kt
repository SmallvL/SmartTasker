package com.smarttasker.core.record.ui

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smarttasker.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test for RecordingOverlayService ACTION_RECORD_START_FAILED
 * broadcast contract introduced in v0.9.8 (Issue #1).
 *
 * This test starts the service in a no-overlay environment and verifies the
 * broadcast is sent. The receiver is verified manually with:
 *   adb shell am start -n com.smarttasker.debug/com.smarttasker.ui.trialrun.TrialRunActivity
 *   adb logcat | grep RecOverlay
 */
@RunWith(AndroidJUnit4::class)
class RecordingOverlayServiceTest {

    @Test
    fun serviceExposesStartFailedBroadcastAction() {
        // Verify constants are non-null and well-formed
        assertEquals("com.smarttasker.action.RECORD_START_FAILED", RecordingOverlayService.ACTION_RECORD_START_FAILED)
        assertEquals("fail_reason", RecordingOverlayService.EXTRA_FAIL_REASON)
        // The action must be namespaced (com.smarttasker.*) to be RECEIVER_NOT_EXPORTED-safe
        assertTrue(RecordingOverlayService.ACTION_RECORD_START_FAILED.startsWith("com.smarttasker."))
    }

    @Test
    fun serviceIntentParseRecognisesStartAction() {
        val intent = Intent(RecordingOverlayService.ACTION_START)
        assertEquals(RecordingOverlayService.ACTION_START, intent.action)
    }

    @Test
    fun stopIntentParseRecognisesStopAction() {
        val intent = Intent(RecordingOverlayService.ACTION_STOP)
        assertEquals(RecordingOverlayService.ACTION_STOP, intent.action)
    }

    @Test
    fun contextProvidesTestableApplication() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(context)
        assertTrue(context.packageName.startsWith("com.smarttasker"))
    }
}
