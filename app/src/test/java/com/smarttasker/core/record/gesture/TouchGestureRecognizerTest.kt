package com.smarttasker.core.record.gesture

import com.smarttasker.core.record.gesture.TouchGestureRecognizer.RecognizedGesture
import com.smarttasker.core.record.parser.RawInputParser.RawInputEvent
import org.junit.Assert.*
import org.junit.Test

class TouchGestureRecognizerTest {

    private fun recognizer(dpi: Int = 440) = TouchGestureRecognizer(dpi)

    @Test
    fun `short tap at fixed point is recognized as Tap`() {
        val r = recognizer()
        assertNull(r.feed(RawInputEvent.TouchDown(0, 500, 800, 50, 1000L)))
        val g = r.feed(RawInputEvent.TouchUp(1100L))
        assertTrue(g is RecognizedGesture.Tap)
        g as RecognizedGesture.Tap
        assertEquals(500, g.x)
        assertEquals(800, g.y)
    }

    @Test
    fun `hold for over 200ms with no movement is LongPress`() {
        val r = recognizer()
        r.feed(RawInputEvent.TouchDown(0, 500, 800, 50, 1000L))
        val g = r.feed(RawInputEvent.TouchUp(1300L)) // 300ms > 200ms threshold
        assertTrue(g is RecognizedGesture.LongPress)
        g as RecognizedGesture.LongPress
        assertEquals(300L, g.durationMs)
    }

    @Test
    fun `large movement is recognized as Swipe`() {
        val r = recognizer() // 440 dpi: tapThreshold = 44px, swipeMin = 88px
        r.feed(RawInputEvent.TouchDown(0, 100, 500, 50, 1000L))
        r.feed(RawInputEvent.TouchMove(500, 500, 1100L)) // moved 400px, well over 88px
        val g = r.feed(RawInputEvent.TouchUp(1200L))
        assertTrue(g is RecognizedGesture.Swipe)
        g as RecognizedGesture.Swipe
        assertEquals(100, g.startX)
        assertEquals(500, g.endX)
        assertEquals(500, g.startY)
        assertEquals(500, g.endY)
    }

    @Test
    fun `movement less than tap threshold but duration short is still Tap`() {
        val r = recognizer()
        r.feed(RawInputEvent.TouchDown(0, 500, 500, 50, 1000L))
        r.feed(RawInputEvent.TouchMove(515, 505, 1050L)) // moved 15px < 44px threshold
        val g = r.feed(RawInputEvent.TouchUp(1100L))
        assertTrue(g is RecognizedGesture.Tap)
    }

    @Test
    fun `KeyDown then KeyUp short is KeyPress`() {
        val r = recognizer()
        r.feed(RawInputEvent.KeyDown(158, "BACK", 1000L))
        val g = r.feed(RawInputEvent.KeyUp(158, "BACK", 1200L)) // 200ms < 500ms
        assertTrue(g is RecognizedGesture.KeyPress)
        g as RecognizedGesture.KeyPress
        assertEquals("BACK", g.keyName)
    }

    @Test
    fun `KeyDown held over 500ms is KeyLongPress`() {
        val r = recognizer()
        r.feed(RawInputEvent.KeyDown(102, "HOME", 1000L))
        val g = r.feed(RawInputEvent.KeyUp(102, "HOME", 1600L)) // 600ms > 500ms
        assertTrue(g is RecognizedGesture.KeyLongPress)
        g as RecognizedGesture.KeyLongPress
        assertEquals(600L, g.durationMs)
    }

    @Test
    fun `TouchUp without prior TouchDown is ignored`() {
        val r = recognizer()
        assertNull(r.feed(RawInputEvent.TouchUp(1000L)))
    }

    @Test
    fun `KeyUp without prior KeyDown is ignored`() {
        val r = recognizer()
        assertNull(r.feed(RawInputEvent.KeyUp(102, "HOME", 1000L)))
    }

    @Test
    fun `reset clears in-flight state`() {
        val r = recognizer()
        r.feed(RawInputEvent.TouchDown(0, 500, 800, 50, 1000L))
        r.reset()
        // After reset, TouchUp is ignored
        assertNull(r.feed(RawInputEvent.TouchUp(1100L)))
    }

    @Test
    fun `dpi scales tap and swipe thresholds`() {
        val lowDpi = TouchGestureRecognizer(screenDpi = 160)  // 16dp=16px, 32dp=32px
        val highDpi = TouchGestureRecognizer(screenDpi = 640) // 16dp=64px, 32dp=128px
        // A 30px movement is > low-dpi tap threshold (16) but < high-dpi tap threshold (64)
        lowDpi.feed(RawInputEvent.TouchDown(0, 100, 100, 50, 1000L))
        lowDpi.feed(RawInputEvent.TouchMove(130, 100, 1050L))
        val lowG = lowDpi.feed(RawInputEvent.TouchUp(1100L))
        assertTrue("30px movement at 160dpi should exceed tap threshold", lowG is RecognizedGesture.Swipe)

        highDpi.feed(RawInputEvent.TouchDown(0, 100, 100, 50, 1000L))
        highDpi.feed(RawInputEvent.TouchMove(130, 100, 1050L))
        val highG = highDpi.feed(RawInputEvent.TouchUp(1100L))
        assertTrue("30px movement at 640dpi is below tap threshold", highG is RecognizedGesture.Tap)
    }
}
