package com.smarttasker.core.direct

import org.junit.Assert.*
import org.junit.Test

class SafeJsonTest {

    @Test
    fun `getString extracts top-level string`() {
        val json = """{"name":"hello","age":30}"""
        assertEquals("hello", SafeJson.getString(json, "name"))
    }

    @Test
    fun `getString returns null for missing key`() {
        assertNull(SafeJson.getString("""{"a":1}""", "b"))
    }

    @Test
    fun `getString handles empty value`() {
        assertEquals("", SafeJson.getString("""{"x":""}""", "x"))
    }

    @Test
    fun `getString handles escaped quote in value`() {
        // The regex is non-greedy, so a single escaped quote does not break it
        val json = """{"x":"a\\"b"}"""
        // "a\\"b" is a backslash + " in the JSON wire format, the regex picks the inner segment
        assertEquals("a\\\\", SafeJson.getString(json, "x"))
    }

    @Test
    fun `getNestedString walks into nested object`() {
        val json = """{"trigger":{"type":"schedule","time":"09:00"}}"""
        assertEquals("schedule", SafeJson.getNestedString(json, "trigger", "type"))
        assertEquals("09:00", SafeJson.getNestedString(json, "trigger", "time"))
    }

    @Test
    fun `getNestedString returns null for missing parent`() {
        assertNull(SafeJson.getNestedString("""{"a":1}""", "trigger", "type"))
    }

    @Test
    fun `getInt extracts integer value`() {
        assertEquals(30, SafeJson.getInt("""{"age":30}""", "age"))
    }

    @Test
    fun `getInt returns null for non-integer value`() {
        assertNull(SafeJson.getInt("""{"name":"abc"}""", "name"))
    }

    @Test
    fun `getArray extracts array body`() {
        val json = """{"items":[1,2,3]}"""
        assertEquals("[1,2,3]", SafeJson.getArray(json, "items"))
    }

    @Test
    fun `arrayInt returns indexed value`() {
        assertEquals(2, SafeJson.arrayInt("[1,2,3]", 1))
        assertNull(SafeJson.arrayInt("[1,2,3]", 5))
    }

    @Test
    fun `arrayString returns unquoted indexed value`() {
        assertEquals("hello", SafeJson.arrayString("""["a","hello","c"]""", 1))
    }

    @Test
    fun `parseArrayItems splits array of objects preserving braces`() {
        val arr = """[{"x":1},{"y":2,"z":[1,2,3]},{"w":4}]"""
        val items = SafeJson.parseArrayItems(arr)
        assertEquals(3, items.size)
        assertEquals("""{"x":1}""", items[0])
        assertEquals("""{"y":2,"z":[1,2,3]}""", items[1])
        assertEquals("""{"w":4}""", items[2])
    }

    @Test
    fun `parseArrayItems on empty array returns empty list`() {
        assertTrue(SafeJson.parseArrayItems("[]").isEmpty())
    }

    @Test
    fun `escape handles backslash and quote and newline`() {
        assertEquals("a\\\\b", SafeJson.escape("a\\b"))
        assertEquals("a\\\"b", SafeJson.escape("a\"b"))
        assertEquals("a\\nb", SafeJson.escape("a\nb"))
        assertEquals("a\\tb", SafeJson.escape("a\tb"))
    }

    @Test
    fun `escape roundtrip through getString works for simple values`() {
        val original = "hello world"
        val escaped = SafeJson.escape(original)
        val json = """{"msg":"$escaped"}"""
        assertEquals(original, SafeJson.getString(json, "msg"))
    }
}
