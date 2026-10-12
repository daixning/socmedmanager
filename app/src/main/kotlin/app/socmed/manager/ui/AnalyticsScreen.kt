@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.socmed.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.socmed.core.Analytics
import app.socmed.core.AnalyticsFilter
import app.socmed.core.Format
import app.socmed.core.Insight
import app.socmed.core.Metric
import app.socmed.core.Platform
import app.socmed.core.Rates
import app.socmed.core.Status
import app.socmed.manager.data.FollowerLogEntity
import app.socmed.manager.data.newId
import app.socmed.manager.data.isSampleId
import java.time.LocalDate

@Composable
fun AnalyticsScreen() {
    val env = LocalEnv.current
    val d = LocalData.current
    val zone = d.zone
    val now = System.currentTimeMillis()
    val today = LocalDate.now(zone)
    var sample by rememberSaveable { mutableStateOf(false) }
    val useSample = sample || (!d.hasReal && d.hasSample)
    var f by remember { mutableStateOf(AnalyticsFilter(fromDay = today.minusDays(89).toEpochDay(), toDay = today.toEpochDay())) }
    var metric by rememberSaveable { mutableStateOf(Metric.REACH) }
    var monthly by rememberSaveable { mutableStateOf(false) }
    var useMedian by rememberSaveable { mutableStateOf(true) }
    var asOfMode by rememberSaveable { mutableStateOf(false) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var addFollowers by remember { mutableStateOf(false) }
    var pickPost by remember { mutableStateOf(false) }

    val allPosts = d.perf(useSample)
    val ff = f.copy(snapshotAsOfDay = if (asOfMode) f.toDay else null)
    val published = Analytics.published(allPosts, ff, zone)
    val progress = Analytics.progress(allPosts, ff, zone, now)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Analytics", style = MaterialTheme.typography.headlineMedium)
        Text("Everything here comes from metrics you type in or import from a CSV. Nothing is fetched from social platforms.", style = MaterialTheme.typography.bodyMedium)
        if (d.hasSample) {
            Text("Data set", style = MaterialTheme.typography.titleSmall)
            SingleChips(listOf("real" to "My data", "sample" to "Sample data"), if (useSample) "sample" else "real", { if (it.isNotEmpty()) sample = it == "sample" }, allowClear = false)
            if (useSample) SoftCard(Modifier.fillMaxWidth()) { Text("SAMPLE DATA — these numbers are made up and are kept separate from your real analytics.", Modifier.padding(12.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickPost = true }) { Text("Add metrics") }
            OutlinedButton(onClick = { env.nav.push("csvimport") }) { Text("Import CSV") }
        }

        ExpandCard("Filters", summary = Analytics.rangeLabel(f), startExpanded = false) {
            Text("Date range (by publishing date)", style = MaterialTheme.typography.titleSmall)
            SingleChips(listOf("30" to "30 days", "90" to "90 days", "year" to "This year", "all" to "All time"), "", { p ->
                f = when (p) {
                    "30" -> f.copy(fromDay = today.minusDays(29).toEpochDay(), toDay = today.toEpochDay())
                    "90" -> f.copy(fromDay = today.minusDays(89).toEpochDay(), toDay = today.toEpochDay())
                    "year" -> f.copy(fromDay = LocalDate.of(today.year, 1, 1).toEpochDay(), toDay = today.toEpochDay())
                    else -> f.copy(fromDay = null, toDay = null)
                }
            })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerField("From", f.fromDay, { f = f.copy(fromDay = it) }, Modifier.weight(1f))
                DatePickerField("To", f.toDay, { f = f.copy(toDay = it) }, Modifier.weight(1f))
            }
            Text("Platform", style = MaterialTheme.typography.titleSmall)
            MultiChips(Platform.entries.map { it.name to it.label }, f.platforms, { f = f.copy(platforms = it) })
            Text("Format", style = MaterialTheme.typography.titleSmall)
            MultiChips(Format.entries.map { it.name to it.label }, f.formats, { f = f.copy(formats = it) })
            val pillars = allPosts.map { it.pillar }.filter { it.isNotBlank() }.distinct().sorted()
            if (pillars.isNotEmpty()) { Text("Content pillar", style = MaterialTheme.typography.titleSmall); MultiChips(pillars.map { it to it }, f.pillars, { f = f.copy(pillars = it) }) }
            val camps = allPosts.map { it.campaign }.filter { it.isNotBlank() }.distinct().sorted()
            if (camps.isNotEmpty()) { Text("Campaign", style = MaterialTheme.typography.titleSmall); MultiChips(camps.map { it to it }, f.campaigns, { f = f.copy(campaigns = it) }) }
            val evs = d.events.filter { e -> allPosts.any { it.eventId == e.id } }
            if (evs.isNotEmpty()) { Text("Event", style = MaterialTheme.typography.titleSmall); MultiChips(evs.map { it.id to it.name }, f.eventIds, { f = f.copy(eventIds = it) }) }
            Text("Which snapshot to use for each post", style = MaterialTheme.typography.titleSmall)
            SingleChips(listOf("latest" to "Latest snapshot", "asof" to "Latest on/before the “To” date"), if (asOfMode) "asof" else "latest", { if (it.isNotEmpty()) asOfMode = it == "asof" }, allowClear = false)
            TextButton(onClick = { f = AnalyticsFilter(f.fromDay, f.toDay) }) { Text("Clear attribute filters") }
        }

        // ------------------------------------------------ publishing activity
        SectionTitle("Publishing activity")
        Text("What you posted — independent of how it performed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${published.size} published post version${if (published.size == 1) "" else "s"} in ${Analytics.rangeLabel(f)}", style = MaterialTheme.typography.titleSmall)
                Text("Planned vs published: ${progress.published} of ${progress.planned} planned versions published" + if (progress.overdue > 0) " · ${progress.overdue} overdue (not moved)" else "", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { if (progress.planned == 0) 0f else progress.published.toFloat() / progress.planned }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
                Text("By platform", style = MaterialTheme.typography.labelLarge)
                Bars(Analytics.distribution(published) { Platform.from(it.platform).label.takeIf { _ -> it.platform.isNotBlank() } ?: "Unspecified" })
                Text("By category", style = MaterialTheme.typography.labelLarge)
                Bars(Analytics.distribution(published) { it.pillar })
            }
        }

        // ------------------------------------------------ audience performance
        SectionTitle("Audience performance")
        Text("Metric", style = MaterialTheme.typography.labelLarge)
        SingleChips(Metric.entries.map { it.name to it.label }, metric.name, { if (it.isNotEmpty()) metric = Metric.valueOf(it) }, allowClear = false)
        val withData = published.mapNotNull { p -> Analytics.metricValue(p, metric, ff.snapshotAsOfDay)?.let { p to it } }
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${metric.label}: ${withData.size} of ${published.size} published posts have this metric.", style = MaterialTheme.typography.bodyMedium)
                if (withData.isEmpty()) Text("No data yet. Use Add metrics or import a CSV. Blank means unknown — it is never counted as zero.", style = MaterialTheme.typography.bodySmall)
                else {
                    val vals = withData.map { it.second }
                    Text("Average ${Fmt.num(vals.average())} · Median ${Fmt.num(Analytics.median(vals))} · Highest ${Fmt.num(vals.max())}", style = MaterialTheme.typography.titleSmall)
                    Text("Each post counts once, using its ${if (asOfMode) "latest snapshot on or before the “To” date" else "latest snapshot"}. Snapshots are never added together.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text("Best-performing posts · ${metric.label}", style = MaterialTheme.typography.titleSmall)
        Analytics.top(published, metric, ff.snapshotAsOfDay).forEachIndexed { i, (p, v) ->
            SoftCard(Modifier.fillMaxWidth(), onClick = { env.nav.push("post/${p.contentId}") }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(p.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(Platform.from(p.platform).label.takeIf { p.platform.isNotBlank() }, Format.labelOf(p.format).takeIf { p.format.isNotBlank() }).filterNotNull().joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(Fmt.num(v), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        if (withData.isEmpty()) Text("—", style = MaterialTheme.typography.bodySmall)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Compare groups by", style = MaterialTheme.typography.titleSmall)
            SingleChips(listOf("median" to "Median", "mean" to "Average"), if (useMedian) "median" else "mean", { if (it.isNotEmpty()) useMedian = it == "median" }, allowClear = false)
        }
        GroupTable("By format", Analytics.byGroup(published, metric, ff.snapshotAsOfDay) { Format.labelOf(it.format) }, useMedian)
        GroupTable("By content pillar", Analytics.byGroup(published, metric, ff.snapshotAsOfDay) { it.pillar }, useMedian)

        // ------------------------------------------------ engagement rates
        SectionTitle("Engagement rates")
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Rates.all.forEach { def ->
                    val results = published.mapNotNull { p -> Analytics.chooseSnap(p, ff.snapshotAsOfDay)?.let { Rates.compute(def, it.values) } }
                    val ok = results.mapNotNull { it.value }
                    Column {
                        Text(def.name, style = MaterialTheme.typography.titleSmall)
                        Text("Formula: ${def.formula}", style = MaterialTheme.typography.bodySmall)
                        Text("Denominator: ${def.denominator.label.lowercase()}", style = MaterialTheme.typography.bodySmall)
                        Text(if (ok.isEmpty()) "Not calculated — ${results.firstOrNull { it.reason != null }?.reason ?: "no snapshots yet"}" else "Median ${Fmt.pct(Analytics.median(ok))} · average ${Fmt.pct(ok.average())} · from ${ok.size} of ${published.size} posts (others lack the required values)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // ------------------------------------------------ trends and comparison
        SectionTitle("Trend over time · ${metric.label}")
        SingleChips(listOf("w" to "Weekly", "m" to "Monthly"), if (monthly) "m" else "w", { if (it.isNotEmpty()) monthly = it == "m" }, allowClear = false)
        val trend = Analytics.trend(published, metric, ff.snapshotAsOfDay, zone, monthly)
        if (trend.isEmpty() || trend.all { it.withData == 0 }) Text("Not enough metrics yet to draw a trend.", style = MaterialTheme.typography.bodySmall)
        else {
            val max = trend.maxOf { it.value }.coerceAtLeast(1.0)
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                trend.takeLast(14).forEach { t ->
                    Box(Modifier.weight(1f).fillMaxHeight((t.value / max).toFloat().coerceIn(0.03f, 1f)).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(MaterialTheme.colorScheme.primary))
                }
            }
            trend.takeLast(14).forEach { t -> Text("${t.label}: ${Fmt.num(t.value)} (${t.withData} of ${t.posts} posts have data)", style = MaterialTheme.typography.bodySmall) }
        }
        val cmp = Analytics.compareEquivalent(allPosts, ff, metric, zone, useMedian)
        SectionTitle("Equivalent period comparison")
        if (cmp == null) Text("Choose a start and end date to compare with the period right before it.", style = MaterialTheme.typography.bodySmall)
        else SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val len = Analytics.rangeDays(ff)
                Text("This period vs the previous $len days · ${if (useMedian) "median" else "average"} ${metric.label.lowercase()}", style = MaterialTheme.typography.bodySmall)
                Text("This period: ${Fmt.num(cmp.current)} (${cmp.currentN} posts)", style = MaterialTheme.typography.titleSmall)
                Text("Previous: ${Fmt.num(cmp.previous)} (${cmp.previousN} posts)", style = MaterialTheme.typography.titleSmall)
                cmp.pct?.let { Text("Change: ${if (it >= 0) "+" else ""}${Fmt.pct(it)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                if (cmp.current == null || cmp.previous == null) Text("One of the periods has no data, so no comparison is shown.", style = MaterialTheme.typography.bodySmall)
            }
        }

        // ------------------------------------------------ followers
        SectionTitle("Logged follower growth") { AddButton("Log", { addFollowers = true }) }
        val logs = d.followerLogs.filter { isSampleId(it.id) == useSample }
        val growth = Analytics.followerGrowth(logs.map { Triple(it.platform, it.dayEpoch, it.followers) }, f.fromDay, f.toDay)
        if (growth.isEmpty()) Text("Log your follower count on two or more dates (per platform) to see growth. Numbers are typed in by you.", style = MaterialTheme.typography.bodySmall)
        growth.forEach { g -> Text("${Platform.from(g.platform).label}: ${Fmt.num(g.first.toDouble())} → ${Fmt.num(g.last.toDouble())} (${if (g.gained >= 0) "+" else ""}${g.gained}) between ${Fmt.day(g.firstDay)} and ${Fmt.day(g.lastDay)}", style = MaterialTheme.typography.bodyMedium) }
        logs.sortedByDescending { it.dayEpoch }.take(5).forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${Fmt.day(l.dayEpoch)} · ${Platform.from(l.platform).label}: ${Fmt.num(l.followers.toDouble())}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { env.launch { env.repo.deleteFollowerLog(l.id) } }) { Text("Remove") }
            }
        }

        // ------------------------------------------------ insights
        SectionTitle("Insights")
        Text("Simple rules over your own numbers — no AI service, nothing leaves your phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val insights = Analytics.insights(allPosts, ff, zone)
        if (insights.isEmpty()) Text("Not enough data for an insight yet. Add metrics for at least a few published posts in different formats or pillars.", style = MaterialTheme.typography.bodySmall)
        insights.forEach { InsightCard(it) }
        Gap(24.dp)
    }

    if (addFollowers) FollowerDialog(useSample) { addFollowers = false }
    if (pickPost) MetricsPickerDialog(useSample) { pickPost = false }
}

@Composable
private fun Bars(data: List<Pair<String, Int>>) {
    if (data.isEmpty()) { Text("—", style = MaterialTheme.typography.bodySmall); return }
    val max = data.maxOf { it.second }.coerceAtLeast(1)
    data.forEach { (label, n) ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.width(104.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(progress = { n.toFloat() / max }, Modifier.weight(1f).height(8.dp).clip(CircleShape))
            Text("$n", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun GroupTable(title: String, rows: List<app.socmed.core.GroupStat>, median: Boolean) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (rows.isEmpty()) Text("No data", style = MaterialTheme.typography.bodySmall)
            else {
                Row { Text("Group", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall); Text("Posts", Modifier.width(48.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End); Text("Average", Modifier.width(72.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End); Text("Median", Modifier.width(72.dp), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End) }
                rows.sortedByDescending { if (median) it.median else it.mean }.forEach { r ->
                    Row {
                        Text(r.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text("${r.n}", Modifier.width(48.dp), textAlign = TextAlign.End)
                        Text(Fmt.num(r.mean), Modifier.width(72.dp), textAlign = TextAlign.End, fontWeight = if (!median) FontWeight.Bold else FontWeight.Normal)
                        Text(Fmt.num(r.median), Modifier.width(72.dp), textAlign = TextAlign.End, fontWeight = if (median) FontWeight.Bold else FontWeight.Normal)
                    }
                    if (r.n < 3) Text("  Limited evidence: only ${r.n} post${if (r.n == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InsightCard(i: Insight) {
    val env = LocalEnv.current
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(i.text, style = MaterialTheme.typography.bodyLarge)
            Text("Metric: median ${i.metric.label.lowercase()} · Range: ${i.rangeLabel} · Sample: ${i.sampleSize} posts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (i.limited) LabelChip("Limited evidence — treat as a hint, not a conclusion")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { saveIdea(env, i, false) }) { Text("Save as idea") }
                OutlinedButton(onClick = { saveIdea(env, i, true) }) { Text("Save as experiment") }
            }
        }
    }
}

private fun saveIdea(env: AppEnv, i: Insight, experiment: Boolean) = env.launch {
    val title = if (experiment) "Experiment: ${i.ideaTitle}" else i.ideaTitle
    val c = env.repo.createContent(title, Status.IDEA, collection = if (experiment) "Experiments" else "From insights")
    env.repo.update(c.id) {
        it.copy(
            description = (if (experiment) "Hypothesis to test: " else "") + i.text + "\n\nBased on ${i.sampleSize} posts, ${i.rangeLabel}." + if (i.limited) " (Limited evidence.)" else "",
            tags = if (experiment) "experiment,insight" else "insight",
        )
    }
    env.toast(if (experiment) "Saved as an experiment idea" else "Saved as an idea")
}

@Composable
private fun FollowerDialog(sample: Boolean, onDismiss: () -> Unit) {
    val env = LocalEnv.current
    val d = LocalData.current
    var platform by remember { mutableStateOf(Platform.INSTAGRAM.name) }
    var day by remember { mutableStateOf<Long?>(LocalDate.now(d.zone).toEpochDay()) }
    var count by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Log follower count") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField("Platform", Platform.entries.map { it.name to it.label }, platform, { platform = it })
                DatePickerField("Date", day, { day = it })
                OutlinedTextField(count, { count = it.filter(Char::isDigit) }, label = { Text("Followers") }, singleLine = true)
                if (sample) Text("Note: this will be saved as real data unless you are in the Sample data view.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = count.isNotBlank() && day != null, onClick = {
                val t = System.currentTimeMillis()
                env.launch { env.repo.saveFollowerLog(FollowerLogEntity((if (sample) "sample-" else "") + newId(), platform, day!!, count.toLong(), "", t, t)) }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
