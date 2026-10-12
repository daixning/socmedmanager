package app.socmed.core

object Csv {
    /** RFC 4180 style writer. Fields are quoted when needed. Uses CRLF line endings. */
    fun write(rows: List<List<String>>): String = buildString {
        for (r in rows) {
            append(r.joinToString(",") { quote(it) })
            append("\r\n")
        }
    }

    fun quote(s: String): String {
        // Neutralise spreadsheet formula injection for text that starts with a formula trigger.
        val safe = if (s.isNotEmpty() && s[0] in "=@" ) "'$s" else s
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun detectDelimiter(text: String): Char {
        val firstLine = text.removePrefix("﻿").lineSequence().firstOrNull() ?: return ','
        return listOf(',', ';', '\t').maxByOrNull { d -> firstLine.count { it == d } }
            ?.takeIf { d -> firstLine.contains(d) } ?: ','
    }

    fun parse(input: String, delimiter: Char = detectDelimiter(input)): List<List<String>> {
        val text = input.removePrefix("﻿")
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        var fieldStarted = false
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { sb.append('"'); i++ } else inQuotes = false
                } else sb.append(c)
            } else when {
                c == '"' && sb.isEmpty() -> { inQuotes = true; fieldStarted = true }
                c == delimiter -> { row.add(sb.toString()); sb.setLength(0); fieldStarted = true }
                c == '\r' -> { /* handled with \n */ }
                c == '\n' -> {
                    row.add(sb.toString()); sb.setLength(0)
                    rows.add(row); row = ArrayList(); fieldStarted = false
                }
                else -> { sb.append(c); fieldStarted = true }
            }
            i++
        }
        if (fieldStarted || sb.isNotEmpty() || row.isNotEmpty()) { row.add(sb.toString()); rows.add(row) }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }
}
