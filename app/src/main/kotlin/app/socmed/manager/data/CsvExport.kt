package app.socmed.manager.data

import app.socmed.core.Csv
import app.socmed.core.Format
import app.socmed.core.Metric
import app.socmed.core.Platform
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** CSV exports. These are for spreadsheets and re-import of metrics only; they are not a backup. */
object CsvExport {
    private fun ts(ms: Long?, zone: ZoneId) = ms?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime().toString().replace('T', ' ') }.orEmpty()
    private fun day(d: Long?) = d?.let { LocalDate.ofEpochDay(it).toString() }.orEmpty()
    private fun num(d: Double?) = d?.let { if (it == Math.rint(it)) it.toLong().toString() else it.toString() }.orEmpty()

    fun content(d: AppData, includeSample: Boolean): String {
        val zone = d.zone
        val rows = ArrayList<List<String>>()
        rows += listOf("content_id", "version_id", "title", "platform", "format", "status", "pillar", "campaign", "event", "event_phase", "tags", "audience", "goal",
            "priority", "target_date", "production_deadline", "planned_at", "published_at", "published_url", "caption", "hook", "call_to_action", "hashtags", "visuals_needed", "archived", "notes")
        for (c in d.contents.sortedBy { it.plannedAt ?: Long.MAX_VALUE }) {
            if (!includeSample && isSampleId(c.id)) continue
            val ev = c.eventId?.let { d.eventById[it]?.name }.orEmpty()
            val vs = d.versionsOf(c.id)
            fun row(v: VersionEntity?) = listOf(
                c.id, v?.id.orEmpty(), c.title, v?.let { Platform.from(it.platform).label }.orEmpty(), Format.labelOf(v?.format?.ifBlank { c.format } ?: c.format).takeIf { c.format.isNotBlank() || v?.format?.isNotBlank() == true }.orEmpty(),
                d.statusLabel(v?.status ?: c.status), c.pillar, c.campaign, ev, c.eventPhase, c.tags, c.audience, c.goal, c.priority.lowercase(),
                day(c.targetDay), day(c.deadlineDay), ts(v?.plannedAt ?: c.plannedAt, zone), ts(v?.publishedAt ?: c.publishedAt, zone), v?.url?.ifBlank { c.publishedUrl } ?: c.publishedUrl,
                v?.caption?.ifBlank { c.caption } ?: c.caption, c.hook, c.cta, v?.hashtags?.ifBlank { c.hashtags } ?: c.hashtags, c.visualsNeeded.toString(), c.archived.toString(), c.notes,
            )
            if (vs.isEmpty()) rows += row(null) else vs.forEach { rows += row(it) }
        }
        return Csv.write(rows)
    }

    fun analytics(d: AppData, includeSample: Boolean): String {
        val rows = ArrayList<List<String>>()
        rows += listOf("post_id", "title", "platform", "date") + Metric.entries.map { it.name.lowercase() } + listOf("note")
        for (s in d.snapshots.sortedWith(compareBy({ it.dayEpoch }, { it.id }))) {
            if (!includeSample && isSampleId(s.contentId)) continue
            val c = d.contentById[s.contentId] ?: continue
            val v = d.versionById[s.versionId]
            val vals = s.toValues()
            rows += listOf(s.versionId, c.title, v?.let { Platform.from(it.platform).label }.orEmpty(), day(s.dayEpoch)) +
                Metric.entries.map { num(vals[it]) } + listOf(s.note)
        }
        return Csv.write(rows)
    }
}
