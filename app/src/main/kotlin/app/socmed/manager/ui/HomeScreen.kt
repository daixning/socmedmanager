@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.socmed.manager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.socmed.core.Analytics
import app.socmed.core.AnalyticsFilter
import app.socmed.core.Metric
import app.socmed.core.Platform
import app.socmed.core.Rates
import app.socmed.core.Status
import app.socmed.manager.data.Days
import java.time.LocalDate

@Composable
fun HomeScreen(goTo: (Int) -> Unit) {
    val env = LocalEnv.current
    val d = LocalData.current
    val zone = d.zone
    val now = System.currentTimeMillis()
    val today = LocalDate.now(zone)
    var addIdea by remember { mutableStateOf(false) }
    var addPost by remember { mutableStateOf(false) }
    var addEvent by remember { mutableStateOf(false) }
    var addMetrics by remember { mutableStateOf(false) }

    val entries = d.planEntries()
    val todayEntries = entries.filter { it.atMillis != null && Days.dayOf(it.atMillis, zone) == today && it.status != Status.CANCELLED }
    val deadlinesToday = d.contents.filter { !it.archived && Status.isOpen(it.status) && it.deadlineDay == today.toEpochDay() }
    val tasksToday = d.tasks.filter { !it.done && it.dueDay == today.toEpochDay() }
    val todayEvents = d.events.filter { it.startAt != null && Days.dayOf(it.startAt, zone) == today }
    val overdue = entries.filter { d.isOverdue(it, now) }.sortedBy { it.atMillis }
    val overdueDeadlines = d.contents.filter { !it.archived && Status.isOpen(it.status) && it.status != Status.ON_HOLD && (it.deadlineDay ?: Long.MAX_VALUE) < today.toEpochDay() }
    val overdueTasks = d.tasks.filter { !it.done && (it.dueDay ?: Long.MAX_VALUE) < today.toEpochDay() && it.ownerType == "event" }
    val ready = d.contents.filter { !it.archived && it.status == Status.READY }.sortedBy { d.plannedOf(it) ?: Long.MAX_VALUE }
    val upcomingEvents = d.events.filter { (it.startAt ?: 0L) >= now - 3_600_000L && it.status != "CANCELLED" }.sortedBy { it.startAt }.take(3)
    val ym = java.time.YearMonth.from(today)
    val monthEntries = entries.filter { it.status != Status.CANCELLED && it.atMillis != null && java.time.YearMonth.from(Days.dayOf(it.atMillis, zone)) == ym }
    val monthPublished = monthEntries.count { it.status == Status.PUBLISHED }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text("Hello 👋", style = MaterialTheme.typography.headlineMedium)
            Text(Fmt.shortDay(today) + " · " + zone.id, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickButton("Idea", Icons.Filled.Lightbulb) { addIdea = true }
            QuickButton("Post", Icons.Filled.PostAdd) { addPost = true }
            QuickButton("Event", Icons.Filled.Event) { addEvent = true }
            QuickButton("Metrics", Icons.Filled.Insights) { addMetrics = true }
        }

        if (!d.loaded) EmptyState("Loading…")
        else if (d.contents.isEmpty() && d.events.isEmpty()) {
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Welcome! Start with an idea.", style = MaterialTheme.typography.titleMedium)
                    Text("Tap Idea above and type a title — that's enough. Move it from inspiration → planning → production → publishing → review at your own pace. Everything stays on this phone.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { env.launch { env.repo.loadSample() } }) { Text("Load optional sample data to explore") }
                    Text("Sample data is clearly marked and removable in Settings.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        HomeCard("Today") {
            val empty = todayEntries.isEmpty() && deadlinesToday.isEmpty() && tasksToday.isEmpty() && todayEvents.isEmpty()
            if (empty) Text("Nothing scheduled today.", style = MaterialTheme.typography.bodyMedium)
            todayEvents.forEach { e -> RowLine("Event", e.name, Fmt.time(e.startAt!!, zone)) { env.nav.push("event/${e.id}") } }
            todayEntries.forEach { e -> RowLine("Post", e.content.title.ifBlank { "Untitled" }, Fmt.time(e.atMillis!!, zone) + " · " + d.statusLabel(e.status)) { env.nav.push("post/${e.content.id}") } }
            deadlinesToday.forEach { c -> RowLine("Deadline", c.title.ifBlank { "Untitled" }, "production due today") { env.nav.push("post/${c.id}") } }
            tasksToday.forEach { t -> RowLine("Task", t.text, if (t.ownerType == "event") d.eventById[t.ownerId]?.name.orEmpty() else d.contentById[t.ownerId]?.title.orEmpty()) { if (t.ownerType == "event") env.nav.push("event/${t.ownerId}") else env.nav.push("post/${t.ownerId}") } }
        }

        if (overdue.isNotEmpty() || overdueDeadlines.isNotEmpty() || overdueTasks.isNotEmpty()) HomeCard("Overdue") {
            Text("Nothing is moved or marked automatically. Decide what to do with each.", style = MaterialTheme.typography.bodySmall)
            overdue.take(6).forEach { e -> RowLine("Post", e.content.title.ifBlank { "Untitled" }, "planned ${Fmt.shortDateTime(e.atMillis, zone)}", true) { env.nav.push("post/${e.content.id}") } }
            overdueDeadlines.take(4).forEach { c -> RowLine("Deadline", c.title.ifBlank { "Untitled" }, "was due ${Fmt.day(c.deadlineDay)}", true) { env.nav.push("post/${c.id}") } }
            overdueTasks.take(4).forEach { t -> RowLine("Task", t.text, "was due ${Fmt.day(t.dueDay)}", true) { env.nav.push("event/${t.ownerId}") } }
        }

        HomeCard("Ready to publish") {
            if (ready.isEmpty()) Text("No posts are marked Ready.", style = MaterialTheme.typography.bodyMedium)
            ready.take(5).forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ContentThumb(c, Modifier.size(48.dp), RoundedCornerShape(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Fmt.shortDateTime(d.plannedOf(c), zone), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { env.nav.push("post/${c.id}") }) { Text("Open") }
                }
            }
        }

        HomeCard("Upcoming events") {
            if (upcomingEvents.isEmpty()) Text("No upcoming events.", style = MaterialTheme.typography.bodyMedium)
            upcomingEvents.forEach { e -> RowLine("Event", e.name, Fmt.dateTime(e.startAt, zone)) { env.nav.push("event/${e.id}") } }
            TextButton(onClick = { goTo(2) }) { Text("All events") }
        }

        HomeCard("Publishing progress · ${Fmt.month(ym.year, ym.monthValue)}") {
            Text("$monthPublished of ${monthEntries.size} planned posts published", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(progress = { if (monthEntries.isEmpty()) 0f else monthPublished.toFloat() / monthEntries.size }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
            Text("A post counts as published only when you mark it so.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Compact performance summary from manually entered metrics (real data; sample only if nothing else exists).
        val useSample = !d.hasReal && d.hasSample
        val posts = d.perf(useSample)
        val f = AnalyticsFilter(today.minusDays(29).toEpochDay(), today.toEpochDay())
        val pub = Analytics.published(posts, f, zone)
        val best = Analytics.top(pub, Metric.REACH, null, 1).firstOrNull()
        val eng = pub.mapNotNull { p -> Analytics.chooseSnap(p, null)?.let { Rates.compute(Rates.all[0], it.values).value } }
        HomeCard("Performance · last 30 days" + if (useSample) " (SAMPLE)" else "") {
            Text("${pub.size} published", style = MaterialTheme.typography.titleSmall)
            if (best != null) Text("Top by reach: ${best.first.title} (${Fmt.num(best.second)})", style = MaterialTheme.typography.bodyMedium)
            if (eng.isNotEmpty()) Text("Median engagement by reach: ${Fmt.pct(Analytics.median(eng))} (${eng.size} posts)", style = MaterialTheme.typography.bodyMedium)
            if (best == null) Text("No metrics entered yet. Tap Metrics to add some.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { goTo(3) }) { Text("Open analytics") }
        }
        Gap(24.dp)
    }
    if (addIdea) QuickAddDialog({ addIdea = false })
    if (addPost) QuickAddDialog({ addPost = false }, plannedDate = today)
    if (addEvent) EventNameDialog(null) { addEvent = false }
    if (addMetrics) MetricsPickerDialog(!d.hasReal && d.hasSample) { addMetrics = false }
}

@Composable
private fun QuickButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) { Icon(icon, null, Modifier.size(18.dp)); Text("  $text") }
}

@Composable
private fun HomeCard(title: String, content: @Composable () -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun RowLine(kind: String, title: String, detail: String, alert: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickableCompat(onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LabelChip(kind)
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (alert) OverdueFlag()
    }
}

private fun Modifier.clickableCompat(onClick: () -> Unit) = this.then(Modifier.clickable(onClick = onClick))
