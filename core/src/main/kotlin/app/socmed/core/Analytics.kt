package app.socmed.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/** A platform version of a post, flattened for analytics. */
data class PostPerf(
    val versionId: String,
    val contentId: String,
    val title: String,
    val platform: String,
    val format: String,
    val pillar: String,
    val campaign: String,
    val eventId: String?,
    val plannedAt: Long?,
    val publishedAt: Long?,
    val status: String,
    val snapshots: List<Snap>,
)

data class AnalyticsFilter(
    val fromDay: Long? = null,
    val toDay: Long? = null,
    val platforms: Set<String> = emptySet(),
    val formats: Set<String> = emptySet(),
    val pillars: Set<String> = emptySet(),
    val campaigns: Set<String> = emptySet(),
    val eventIds: Set<String> = emptySet(),
    /** null = latest snapshot; otherwise the latest snapshot taken on or before this day. */
    val snapshotAsOfDay: Long? = null,
) {
    fun matchesAttributes(p: PostPerf) =
        (platforms.isEmpty() || p.platform in platforms) &&
            (formats.isEmpty() || p.format in formats) &&
            (pillars.isEmpty() || p.pillar in pillars) &&
            (campaigns.isEmpty() || p.campaign in campaigns) &&
            (eventIds.isEmpty() || p.eventId in eventIds)
}

object Rates {
    data class Def(val id: String, val name: String, val formula: String, val numerator: List<Metric>, val denominator: Metric)
    data class Result(val def: Def, val value: Double?, val reason: String?)

    private val engagementParts = listOf(Metric.LIKES, Metric.COMMENTS, Metric.SHARES, Metric.SAVES)
    val all = listOf(
        Def("eng_reach", "Engagement by reach", "(likes + comments + shares + saves) ÷ reach × 100", engagementParts, Metric.REACH),
        Def("eng_views", "Engagement by views", "(likes + comments + shares + saves) ÷ views × 100", engagementParts, Metric.VIEWS),
        Def("eng_impr", "Engagement by impressions", "(likes + comments + shares + saves) ÷ impressions × 100", engagementParts, Metric.IMPRESSIONS),
        Def("save_reach", "Save rate", "saves ÷ reach × 100", listOf(Metric.SAVES), Metric.REACH),
        Def("ctr_impr", "Link click rate", "link clicks ÷ impressions × 100", listOf(Metric.LINK_CLICKS), Metric.IMPRESSIONS),
    )

    /** Returns a percentage, or null with a reason when any needed value is missing or the denominator is zero. */
    fun compute(def: Def, v: Map<Metric, Double>): Result {
        val missing = (def.numerator + def.denominator).filter { v[it] == null }
        if (missing.isNotEmpty()) return Result(def, null, "Missing: " + missing.joinToString { it.label.lowercase() })
        val d = v.getValue(def.denominator)
        if (d <= 0.0) return Result(def, null, "${def.denominator.label} is zero")
        return Result(def, def.numerator.sumOf { v.getValue(it) } / d * 100.0, null)
    }
}

data class GroupStat(val name: String, val n: Int, val mean: Double, val median: Double)
data class TrendPoint(val label: String, val startDay: Long, val posts: Int, val withData: Int, val value: Double)
data class Compare(val current: Double?, val previous: Double?, val currentN: Int, val previousN: Int) {
    val delta get() = if (current != null && previous != null) current - previous else null
    val pct get() = if (current != null && previous != null && previous != 0.0) (current - previous) / previous * 100 else null
}
data class Insight(
    val id: String,
    val text: String,
    val metric: Metric,
    val rangeLabel: String,
    val sampleSize: Int,
    val limited: Boolean,
    val ideaTitle: String,
)

object Analytics {
    fun median(xs: List<Double>): Double? {
        if (xs.isEmpty()) return null
        val s = xs.sorted(); val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2
    }

    fun day(millis: Long, zone: ZoneId): Long = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    fun chooseSnap(p: PostPerf, asOfDay: Long?): Snap? =
        p.snapshots.filter { asOfDay == null || it.dayEpoch <= asOfDay }.maxWithOrNull(compareBy({ it.dayEpoch }, { it.id }))

    /** Published (by actual publishing date) and attribute-matching posts inside the date range. */
    fun published(posts: List<PostPerf>, f: AnalyticsFilter, zone: ZoneId): List<PostPerf> = posts.filter { p ->
        p.status == Status.PUBLISHED && f.matchesAttributes(p) && inRange(p.publishedAt ?: p.plannedAt, f, zone)
    }

    private fun inRange(millis: Long?, f: AnalyticsFilter, zone: ZoneId): Boolean {
        if (millis == null) return f.fromDay == null && f.toDay == null
        val d = day(millis, zone)
        return (f.fromDay == null || d >= f.fromDay) && (f.toDay == null || d <= f.toDay)
    }

    data class Progress(val planned: Int, val published: Int, val overdue: Int)

    /** Publishing activity: of the versions whose planned date is in range, how many are published. */
    fun progress(posts: List<PostPerf>, f: AnalyticsFilter, zone: ZoneId, nowMillis: Long): Progress {
        val inPlan = posts.filter { it.status != Status.CANCELLED && f.matchesAttributes(it) && inRange(it.plannedAt ?: it.publishedAt, f, zone) }
        val published = inPlan.count { it.status == Status.PUBLISHED }
        val overdue = inPlan.count { it.status != Status.PUBLISHED && it.status != Status.ON_HOLD && (it.plannedAt ?: Long.MAX_VALUE) < nowMillis }
        return Progress(inPlan.size, published, overdue)
    }

    fun distribution(posts: List<PostPerf>, key: (PostPerf) -> String): List<Pair<String, Int>> =
        posts.groupBy { key(it).ifBlank { "Uncategorised" } }.map { it.key to it.value.size }.sortedByDescending { it.second }

    fun metricValue(p: PostPerf, metric: Metric, asOfDay: Long?): Double? = chooseSnap(p, asOfDay)?.values?.get(metric)

    fun top(posts: List<PostPerf>, metric: Metric, asOfDay: Long?, n: Int = 5): List<Pair<PostPerf, Double>> =
        posts.mapNotNull { p -> metricValue(p, metric, asOfDay)?.let { p to it } }.sortedByDescending { it.second }.take(n)

    fun byGroup(posts: List<PostPerf>, metric: Metric, asOfDay: Long?, key: (PostPerf) -> String): List<GroupStat> =
        posts.mapNotNull { p -> metricValue(p, metric, asOfDay)?.let { key(p).ifBlank { "Uncategorised" } to it } }
            .groupBy({ it.first }, { it.second })
            .map { (k, v) -> GroupStat(k, v.size, v.average(), median(v)!!) }
            .sortedByDescending { it.median }

    /** Sum of each post's selected snapshot value, bucketed by publishing week or month. Snapshots of one post are never added. */
    fun trend(posts: List<PostPerf>, metric: Metric, asOfDay: Long?, zone: ZoneId, monthly: Boolean): List<TrendPoint> {
        val buckets = posts.filter { it.publishedAt != null }.groupBy { p ->
            val d = Instant.ofEpochMilli(p.publishedAt!!).atZone(zone).toLocalDate()
            if (monthly) d.withDayOfMonth(1) else d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }
        return buckets.toSortedMap().map { (start, ps) ->
            val vals = ps.mapNotNull { metricValue(it, metric, asOfDay) }
            TrendPoint(
                if (monthly) "${start.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${start.year}" else "W${start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} ${start.year}",
                start.toEpochDay(), ps.size, vals.size, vals.sum(),
            )
        }
    }

    /** Compares the filter's range with the equally long range immediately before it. */
    fun compareEquivalent(posts: List<PostPerf>, f: AnalyticsFilter, metric: Metric, zone: ZoneId, useMedian: Boolean): Compare? {
        val from = f.fromDay ?: return null
        val to = f.toDay ?: return null
        val len = to - from + 1
        val prev = f.copy(fromDay = from - len, toDay = from - 1)
        fun agg(ff: AnalyticsFilter): Pair<Double?, Int> {
            val vals = published(posts, ff, zone).mapNotNull { metricValue(it, metric, f.snapshotAsOfDay) }
            return (if (useMedian) median(vals) else vals.takeIf { it.isNotEmpty() }?.average()) to vals.size
        }
        val (c, cn) = agg(f); val (p, pn) = agg(prev)
        return Compare(c, p, cn, pn)
    }

    data class FollowerGrowth(val platform: String, val first: Long, val last: Long, val firstDay: Long, val lastDay: Long) {
        val gained get() = last - first
    }

    fun followerGrowth(logs: List<Triple<String, Long, Long>>, fromDay: Long?, toDay: Long?): List<FollowerGrowth> =
        logs.filter { (fromDay == null || it.second >= fromDay) && (toDay == null || it.second <= toDay) }
            .groupBy { it.first }
            .mapNotNull { (pf, l) ->
                val s = l.sortedBy { it.second }
                if (s.size < 2) null else FollowerGrowth(pf, s.first().third, s.last().third, s.first().second, s.last().second)
            }

    fun rangeLabel(f: AnalyticsFilter): String {
        fun d(e: Long) = LocalDate.ofEpochDay(e).toString()
        return when {
            f.fromDay != null && f.toDay != null -> "${d(f.fromDay)} to ${d(f.toDay)}"
            f.fromDay != null -> "since ${d(f.fromDay)}"
            f.toDay != null -> "until ${d(f.toDay)}"
            else -> "all time"
        }
    }

    private fun fmt(d: Double) = if (d == Math.rint(d)) d.toLong().toString() else String.format(java.util.Locale.US, "%.1f", d)

    /**
     * Transparent, rule-based insights. Each one compares the median of a metric for one group against the median of
     * everything else. Evidence is flagged as limited when any side has fewer than 3 posts or the total is below 6.
     */
    fun insights(posts: List<PostPerf>, f: AnalyticsFilter, zone: ZoneId, max: Int = 4): List<Insight> {
        val pub = published(posts, f, zone)
        val range = rangeLabel(f)
        val out = ArrayList<Pair<Double, Insight>>()
        val metrics = listOf(Metric.SAVES, Metric.SHARES, Metric.COMMENTS, Metric.LIKES, Metric.REACH)
        data class Dim(val name: String, val plural: String, val key: (PostPerf) -> String, val label: (String) -> String)
        val dims = listOf(
            Dim("format", "formats", { it.format }, { Format.labelOf(it) }),
            Dim("pillar", "pillars", { it.pillar }, { it }),
            Dim("platform", "platforms", { it.platform }, { Platform.from(it).label }),
        )
        for (dim in dims) for (m in metrics) {
            val vals = pub.mapNotNull { p -> metricValue(p, m, f.snapshotAsOfDay)?.let { p to it } }
                .filter { dim.key(it.first).isNotBlank() }
            val groups = vals.groupBy({ dim.key(it.first) }, { it.second })
            if (groups.size < 2) continue
            val best = groups.maxByOrNull { median(it.value)!! }!!
            val rest = vals.filter { dim.key(it.first) != best.key }.map { it.second }
            val bm = median(best.value)!!; val rm = median(rest) ?: continue
            if (bm <= rm || bm <= 0) continue
            val limited = best.value.size < 3 || rest.size < 3 || vals.size < 6
            val ratio = if (rm > 0) bm / rm else 2.0
            val label = dim.label(best.key)
            val text = "Your $label posts had a higher median ${m.label.lowercase()} (${fmt(bm)}) than your other ${dim.plural} (${fmt(rm)}) in this period."
            out += ratio * (if (limited) 0.5 else 1.0) to Insight(
                "${dim.name}:${best.key}:${m.name}", text, m, range, vals.size, limited,
                "Make more $label content: it earned higher median ${m.label.lowercase()}",
            )
        }
        return out.sortedByDescending { it.first }.map { it.second }.distinctBy { it.id.substringBeforeLast(':') }.take(max)
    }

    /** Whole days between two epoch days, inclusive range length. */
    fun rangeDays(f: AnalyticsFilter): Long? = if (f.fromDay != null && f.toDay != null) ChronoUnit.DAYS.between(LocalDate.ofEpochDay(f.fromDay), LocalDate.ofEpochDay(f.toDay)) + 1 else null
}
