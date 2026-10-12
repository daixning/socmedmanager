package app.socmed.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Fields an analytics CSV column can be mapped to. */
enum class ImportField(val label: String, val aliases: List<String>) {
    POST_ID("Post / version ID", listOf("post_id", "version_id", "id")),
    TITLE("Post title", listOf("title", "post", "content", "post_title")),
    PLATFORM("Platform", listOf("platform", "channel", "network")),
    DATE("Snapshot date", listOf("date", "snapshot_date", "captured_on", "as_of")),
    VIEWS("Views", listOf("views", "video_views", "plays")),
    REACH("Reach", listOf("reach", "accounts_reached")),
    IMPRESSIONS("Impressions", listOf("impressions")),
    LIKES("Likes", listOf("likes", "reactions")),
    COMMENTS("Comments", listOf("comments")),
    SHARES("Shares", listOf("shares", "reposts")),
    SAVES("Saves", listOf("saves", "bookmarks")),
    LINK_CLICKS("Link clicks", listOf("link_clicks", "clicks", "link clicks")),
    INQUIRIES("Inquiries", listOf("inquiries", "enquiries", "leads", "dms")),
    FOLLOWERS_GAINED("Followers gained", listOf("followers_gained", "new_followers", "follows")),
    WATCH_TIME_SEC("Watch time (sec)", listOf("watch_time_sec", "watch_time", "watch time")),
    RETENTION_PCT("Retention (%)", listOf("retention_pct", "retention", "avg_retention")),
    SPEND("Spend", listOf("spend", "cost", "ad_spend")),
    REVENUE("Revenue", listOf("revenue", "sales")),
    NOTE("Note", listOf("note", "notes"));

    val metric: Metric? get() = Metric.from(name)
}

data class ImportRow(
    val line: Int,
    val postId: String?,
    val title: String?,
    val platform: String?,
    val dayEpoch: Long?,
    val values: Map<Metric, Double>,
    val note: String,
    val errors: List<String>,
) { val valid get() = errors.isEmpty() }

object CsvImport {
    private fun norm(s: String) = s.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    fun guessMapping(headers: List<String>): Map<Int, ImportField> {
        val out = LinkedHashMap<Int, ImportField>()
        val used = HashSet<ImportField>()
        headers.forEachIndexed { i, h ->
            val n = norm(h)
            val f = ImportField.entries.firstOrNull { it !in used && (norm(it.name) == n || it.aliases.any { a -> norm(a) == n }) }
            if (f != null) { out[i] = f; used += f }
        }
        return out
    }

    fun parseNumber(raw: String): Double? {
        var s = raw.trim().removeSuffix("%").trim().replace(" ", "").replace(" ", "")
        if (s.isEmpty() || s == "-" || s.equals("n/a", true) || s.equals("null", true)) return null
        s = s.removePrefix("$").removePrefix("₱").removePrefix("€")
        val mult = when {
            s.endsWith("k", true) -> 1_000.0
            s.endsWith("m", true) -> 1_000_000.0
            else -> 1.0
        }
        if (mult != 1.0) s = s.dropLast(1)
        // "1,234" thousands separators, or "1,5" decimal comma when there is no dot.
        s = if (s.contains(',') && s.contains('.')) s.replace(",", "") else if (s.count { it == ',' } == 1 && s.substringAfter(',').length != 3) s.replace(',', '.') else s.replace(",", "")
        return s.toDoubleOrNull()?.times(mult)
    }

    fun parseDay(raw: String, dayFirst: Boolean): Long? {
        val s = raw.trim().substringBefore('T').substringBefore(' ')
        val patterns = listOf("yyyy-M-d", "yyyy/M/d") + if (dayFirst) listOf("d/M/yyyy", "d-M-yyyy", "d.M.yyyy") else listOf("M/d/yyyy", "M-d-yyyy")
        for (p in patterns) try {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern(p)).toEpochDay()
        } catch (_: Exception) {}
        return null
    }

    fun rows(table: List<List<String>>, mapping: Map<Int, ImportField>, dayFirst: Boolean, defaultDay: Long): List<ImportRow> {
        if (table.size < 2) return emptyList()
        return table.drop(1).mapIndexed { idx, cells ->
            val errors = ArrayList<String>()
            var postId: String? = null; var title: String? = null; var platform: String? = null
            var day: Long? = defaultDay; var note = ""
            val values = LinkedHashMap<Metric, Double>()
            for ((col, field) in mapping) {
                val raw = cells.getOrNull(col)?.trim().orEmpty()
                when (field) {
                    ImportField.POST_ID -> postId = raw.ifBlank { null }
                    ImportField.TITLE -> title = raw.ifBlank { null }
                    ImportField.PLATFORM -> platform = raw.ifBlank { null }
                    ImportField.NOTE -> note = raw
                    ImportField.DATE -> if (raw.isNotBlank()) {
                        day = parseDay(raw, dayFirst); if (day == null) errors += "Unreadable date \"$raw\""
                    }
                    else -> if (raw.isNotBlank()) {
                        val n = parseNumber(raw)
                        if (n == null) errors += "${field.label}: \"$raw\" is not a number" else if (n < 0) errors += "${field.label} is negative" else values[field.metric!!] = n
                    }
                }
            }
            if (postId == null && title == null) errors += "No post ID or title to match"
            if (values.isEmpty() && errors.isEmpty()) errors += "No metric values"
            ImportRow(idx + 2, postId, title, platform, day, values, note, errors)
        }
    }
}
