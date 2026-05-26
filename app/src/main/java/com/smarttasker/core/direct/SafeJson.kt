package com.smarttasker.core.direct

/**
 * Safe JSON parsing utilities - no org.json dependency.
 */
object SafeJson {

    fun getString(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*?)\""
        return Regex(pattern).find(json)?.groupValues?.get(1)
    }

    fun getNestedString(json: String, parent: String, child: String): String? {
        val p = "\"$parent\"\\s*:\\s*\\{"
        val m = Regex(p).find(json) ?: return null
        val sub = extractBalanced(json, m.range.last) ?: return null
        return getString(sub, child)
    }

    fun getInt(json: String, key: String): Int? {
        return Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun getArray(json: String, key: String): String? {
        val m = Regex("\"$key\"\\s*:\\s*\\[").find(json) ?: return null
        return extractBalanced(json, m.range.last, '[', ']')
    }

    fun arrayInt(array: String, index: Int): Int? {
        val items = array.trim().removeSurrounding("[", "]").split(",")
        return if (index < items.size) items[index].trim().toIntOrNull() else null
    }

    fun arrayString(array: String, index: Int): String? {
        val items = array.trim().removeSurrounding("[", "]").split(",")
        if (index >= items.size) return null
        val item = items[index].trim()
        return if (item.length >= 2 && item.startsWith("\"") && item.endsWith("\"")) item.substring(1, item.length - 1) else item
    }

    fun parseArrayItems(array: String): List<String> {
        val results = mutableListOf<String>()
        val inner = array.trim().removeSurrounding("[", "]").trim()
        if (inner.isEmpty()) return results
        var depth = 0; var start = -1
        for (i in inner.indices) {
            when (inner[i]) {
                '{' -> { if (depth == 0) start = i; depth++ }
                '}' -> { depth--; if (depth == 0 && start >= 0) { results.add(inner.substring(start, i + 1)); start = -1 } }
            }
        }
        return results
    }

    private fun extractBalanced(json: String, pos: Int, open: Char = '{', close: Char = '}'): String? {
        if (pos >= json.length || json[pos] != open) return null
        var depth = 0
        for (i in pos until json.length) {
            when (json[i]) { open -> depth++; close -> { depth--; if (depth == 0) return json.substring(pos, i + 1) } }
        }
        return null
    }

    fun escape(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    }
}
