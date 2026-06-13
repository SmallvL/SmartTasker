package com.smarttasker.core.record.parser

import com.smarttasker.core.record.parser.RawInputParser.RawInputEvent
import org.junit.Assert.*
import org.junit.Test

class RawInputParserTest {

    private fun parser() = RawInputParser()

    @Test
    fun `parseLine returns null for empty input`() {
        assertNull(parser().parseLine(""))
        assertNull(parser().parseLine("   "))
    }

    @Test
    fun `parseLine returns null for non-event line`() {
        assertNull(parser().parseLine("add device 1: /dev/input/event0"))
    }

    @Test
    fun `parseLine returns null for malformed event`() {
        assertNull(parser().parseLine("[  12345.678901] /dev/input/event0: NOT_A_TYPE 0001 0001"))
    }

    @Test
    fun `TouchDown emitted on first SYN_REPORT after tracking id`() {
        val p = parser()
        assertNull(p.parseLine("[  12345.000100] /dev/input/event0: EV_ABS  ABS_MT_TRACKING_ID  00000001")) // TRACKING_ID = 1
        assertNull(p.parseLine("[  12345.000200] /dev/input/event0: EV_ABS  ABS_MT_POSITION_X  0000012c")) // X = 300
        assertNull(p.parseLine("[  12345.000300] /dev/input/event0: EV_ABS  ABS_MT_POSITION_Y  000002ee")) // Y = 750
        val down = p.parseLine("[  12345.000400] /dev/input/event0: EV_SYN  SYN_REPORT  00000000")
        assertTrue(down is RawInputEvent.TouchDown)
        down as RawInputEvent.TouchDown
        assertEquals(1, down.trackingId)
        assertEquals(300, down.x)
        assertEquals(750, down.y)
    }

    @Test
    fun `TouchMove emitted on subsequent SYN_REPORT while touching`() {
        val p = parser()
        p.parseLine("[  1.000000] /dev/input/event0: EV_ABS  ABS_MT_TRACKING_ID  00000001") // TRACKING_ID = 1
        p.parseLine("[  1.000100] /dev/input/event0: EV_ABS  ABS_MT_POSITION_X  00000064") // X = 100
        p.parseLine("[  1.000200] /dev/input/event0: EV_ABS  ABS_MT_POSITION_Y  000000c8") // Y = 200
        val first = p.parseLine("[  1.000300] /dev/input/event0: EV_SYN  SYN_REPORT  00000000")
        assertTrue(first is RawInputEvent.TouchDown)

        p.parseLine("[  1.000400] /dev/input/event0: EV_ABS  ABS_MT_POSITION_X  00000096") // X = 150
        p.parseLine("[  1.000500] /dev/input/event0: EV_ABS  ABS_MT_POSITION_Y  0000012c") // Y = 300
        val second = p.parseLine("[  1.000600] /dev/input/event0: EV_SYN  SYN_REPORT  00000000")
        assertTrue(second is RawInputEvent.TouchMove)
        second as RawInputEvent.TouchMove
        assertEquals(150, second.x)
        assertEquals(300, second.y)
    }

    @Test
    fun `TouchUp emitted on TRACKING_ID negative`() {
        val p = parser()
        p.parseLine("[  1.000000] /dev/input/event0: EV_ABS  ABS_MT_TRACKING_ID  00000001")
        val up = p.parseLine("[  1.000500] /dev/input/event0: EV_ABS  ABS_MT_TRACKING_ID  ffffffff")
        assertTrue(up is RawInputEvent.TouchUp)
    }

    @Test
    fun `KeyDown then KeyUp parse correctly`() {
        val p = parser()
        val down = p.parseLine("[  1.000000] /dev/input/event0: EV_KEY  0000009e  DOWN")
        assertTrue(down is RawInputEvent.KeyDown)
        down as RawInputEvent.KeyDown
        assertEquals(158, down.keyCode)
        assertEquals("BACK", down.keyName)

        val up = p.parseLine("[  1.000500] /dev/input/event0: EV_KEY  0000009e  UP")
        assertTrue(up is RawInputEvent.KeyUp)
        up as RawInputEvent.KeyUp
        assertEquals("BACK", up.keyName)
    }

    @Test
    fun `BTN_TOUCH events are ignored`() {
        val p = parser()
        val event = p.parseLine("[  1.000000] /dev/input/event0: EV_KEY  BTN_TOUCH  DOWN")
        assertNull(event) // BTN_TOUCH is ignored
    }

    @Test
    fun `reset clears parser state`() {
        val p = parser()
        p.parseLine("[  1.000000] /dev/input/event0: EV_ABS  ABS_MT_TRACKING_ID  00000001")
        p.reset()
        // After reset, no isTouching → SYN_REPORT becomes plain SynReport
        val plain = p.parseLine("[  2.000000] /dev/input/event0: EV_SYN  SYN_REPORT  00000000")
        assertTrue(plain is RawInputEvent.SynReport)
    }

    @Test
    fun `SynReport without touching yields SynReport event`() {
        val plain = parser().parseLine("[  1.000000] /dev/input/event0: EV_SYN  SYN_REPORT  00000000")
        assertTrue(plain is RawInputEvent.SynReport)
    }

    @Test
    fun `timestamp is converted to milliseconds`() {
        val p = parser()
        val event = p.parseLine("[  12345.678901] /dev/input/event0: EV_KEY  0000009e  DOWN")
        assertTrue(event is RawInputEvent.KeyDown)
        event as RawInputEvent.KeyDown
        assertEquals(12345678L, event.timestamp)
    }
}
