@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.socmed.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.socmed.core.Anchored
import app.socmed.core.EventPhase
import app.socmed.core.EventShift
import app.socmed.core.Platform
import app.socmed.core.Status
import app.socmed.manager.data.AssetRole
import app.socmed.manager.data.EventEntity
import java.time.LocalDate
import java.time.ZoneId

private val EVENT_STATUSES = listOf("PLANNING" to "Planning", "CONFIRMED" to "Confirmed", "DONE" to "Done", "CANCELLED" to "Cancelled")

@Composable
fun EventsScreen() {
    val env = LocalEnv.current
    val d = LocalData.current
    val zone = d.zone
    val now = System.currentTimeMillis()
    var showPast by rememberSaveable { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    val upcoming = d.events.filter { it.startAt == null || it.startAt >= now - 86_400_000L }.sortedBy { it.startAt ?: Long.MAX_VALUE }
    val past = d.events.filter { it.startAt != null && it.startAt < now - 86_400_000L }.sortedByDescending { it.startAt }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Events & campaigns", style = MaterialTheme.typography.headlineMedium)
                Text("Plan the content before, during and after each event.", style = MaterialTheme.typography.bodyMedium)
                Gap()
                SingleChips(listOf("u" to "Upcoming (${upcoming.size})", "p" to "Past (${past.size})"), if (showPast) "p" else "u", { if (it.isNotEmpty()) showPast = it == "p" }, allowClear = false)
            }
            val list = if (showPast) past else upcoming
            if (list.isEmpty()) item { EmptyState(if (showPast) "No past events." else "No upcoming events. Tap New event to plan one.") }
            items(list, key = { it.id }) { e -> EventCard(e, zone, now) }
        }
        ExtendedFloatingActionButton(onClick = { add = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New event") })
    }
    if (add) EventNameDialog(null) { add = false }
}

@Composable
fun EventCard(e: EventEntity, zone: ZoneId, now: Long) {
    val env = LocalEnv.current
    val d = LocalData.current
    val tasks = d.tasksOf("event", e.id)
    val linked = d.contentsOfEvent(e.id)
    val days = e.startAt?.let { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(zone), app.socmed.core.Cal.localDate(it, zone)) }
    SoftCard(Modifier.fillMaxWidth(), onClick = { env.nav.push("event/${e.id}") }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(e.name.ifBlank { "Untitled event" }, style = MaterialTheme.typography.titleMedium)
            if (e.theme.isNotBlank()) Text(e.theme, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Text(Fmt.dateTime(e.startAt, zone) + if (e.location.isNotBlank()) " · ${e.location}" else "", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LabelChip(EVENT_STATUSES.firstOrNull { it.first == e.status }?.second ?: e.status)
                if (days != null) LabelChip(when { days == 0L -> "Today"; days > 0 -> "In $days day${if (days == 1L) "" else "s"}"; else -> "${-days} day${if (days == -1L) "" else "s"} ago" })
            }
            Text("${tasks.count { it.done }}/${tasks.size} tasks done · ${linked.size} linked post${if (linked.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EventScreen(id: String) {
    val env = LocalEnv.current
    val d = LocalData.current
    val zone = d.zone
    val e = d.eventById[id]
    var confirmDelete by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf<Pair<Long, List<app.socmed.core.ShiftProposal>>?>(null) }
    var linkExisting by remember { mutableStateOf<EventPhase?>(null) }
    var newContent by remember { mutableStateOf<EventPhase?>(null) }
    if (e == null) { DetailScaffold("Event", { env.nav.pop() }) { pad -> EmptyState("This event was deleted.", Modifier.padding(pad)) }; return }
    val linked = d.contentsOfEvent(id)
    val eventDate = e.startAt?.let { app.socmed.core.Cal.localDate(it, zone) }

    fun proposals(newStart: Long): List<app.socmed.core.ShiftProposal> {
        val newDate = app.socmed.core.Cal.localDate(newStart, zone)
        val anchored = ArrayList<Anchored>()
        d.tasksOf("event", id).forEach { t -> t.relativeDays?.let { anchored += Anchored(t.id, "task", "Task: ${t.text}", it, t.dueDay?.let(LocalDate::ofEpochDay)) } }
        linked.forEach { c ->
            c.relPublishDays?.let { anchored += Anchored(c.id, "publish", "Publish: ${c.title.ifBlank { "Untitled" }}", it, c.plannedAt?.let { p -> app.socmed.core.Cal.localDate(p, zone) }) }
            c.relDeadlineDays?.let { anchored += Anchored(c.id, "deadline", "Production deadline: ${c.title.ifBlank { "Untitled" }}", it, c.deadlineDay?.let(LocalDate::ofEpochDay)) }
        }
        return EventShift.propose(newDate, anchored)
    }

    DetailScaffold(e.name.ifBlank { "Event" }, { env.nav.pop() }, actions = {
        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete event") }
    }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = PagePadding, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ExpandCard("Overview", summary = Fmt.dateTime(e.startAt, zone), startExpanded = true) {
                AutoText("Event name", e.name, { v -> env.launch { env.repo.saveEvent(latestEvent(env, e).copy(name = v)) } }, singleLine = true)
                AutoText("Theme", e.theme, { v -> env.launch { env.repo.saveEvent(latestEvent(env, e).copy(theme = v)) } }, singleLine = true)
                AutoText("Description", e.description, { v -> env.launch { env.repo.saveEvent(latestEvent(env, e).copy(description = v)) } }, minLines = 3)
                AutoText("Objectives", e.objectives, { v -> env.launch { env.repo.saveEvent(latestEvent(env, e).copy(objectives = v)) } }, minLines = 2)
                DateTimeField("Date & time (${zone.id})", e.startAt, zone, { newStart ->
                    val p = if (newStart != null && e.startAt != null) proposals(newStart) else emptyList()
                    if (newStart != null && p.isNotEmpty()) shift = newStart to p
                    else env.launch { env.repo.saveEvent(latestEvent(env, e).copy(startAt = newStart)) }
                })
                AutoText("Location", e.location, { v -> env.launch { env.repo.saveEvent(latestEvent(env, e).copy(location = v)) } }, singleLine = true)
                Text("Status", style = MaterialTheme.typography.titleSmall)
                SingleChips(EVENT_STATUSES, e.status, { s -> if (s.isNotEmpty()) env.launch { env.repo.saveEvent(latestEvent(env, e).copy(status = s)) } }, allowClear = false)
            }
            ExpandCard("Preparation tasks", summary = d.tasksOf("event", id).let { "${it.count { t -> t.done }}/${it.size} done" }, startExpanded = true) {
                Text(if (eventDate == null) "Set the event date to use relative deadlines." else "Tasks can be set relative to the event date. If the date moves, you'll review the proposed changes first.", style = MaterialTheme.typography.bodySmall)
                TasksSection("event", id, showCategory = false, eventDate = eventDate)
            }
            ExpandCard("Linked content", summary = "${linked.size} posts", startExpanded = true) {
                EventPhase.entries.forEach { phase ->
                    val list = linked.filter { it.eventPhase == phase.name }
                    Text(phase.label, style = MaterialTheme.typography.titleSmall)
                    Text(phase.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (list.isEmpty()) Text("Nothing yet.", style = MaterialTheme.typography.bodySmall)
                    list.sortedBy { it.plannedAt ?: Long.MAX_VALUE }.forEach { c ->
                        SoftCard(Modifier.fillMaxWidth(), onClick = { env.nav.push("post/${c.id}") }) {
                            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                ContentThumb(c, Modifier.size(48.dp), RoundedCornerShape(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Text(Fmt.shortDateTime(c.plannedAt, zone) + (c.relPublishDays?.let { " · ${EventShift.describe(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall)
                                }
                                StatusChip(c.status, compact = true)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { newContent = phase }) { Text("New") }
                        OutlinedButton(onClick = { linkExisting = phase }) { Text("Link existing") }
                    }
                }
                val unphased = linked.filter { EventPhase.from(it.eventPhase) == null }
                if (unphased.isNotEmpty()) Text("${unphased.size} linked post(s) have no phase yet — open them to choose before/during/after.", style = MaterialTheme.typography.bodySmall)
            }
            ExpandCard("Links", summary = "${d.linksOf("event", id).size} saved") { LinksSection("event", id) }
            ExpandCard("Attachments", summary = "${d.assetsOf("event", id).size} files") { AssetSection("Attachments & references", "event", id, AssetRole.ATTACH) }
            ExpandCard("Notes", summary = e.notes) { AutoText("Notes", e.notes, { v -> env.launch { env.repo.saveEvent(latestEvent(env, e).copy(notes = v)) } }, minLines = 4) }
            ExpandCard("Post-event review", summary = if (listOf(e.reviewWorked, e.reviewNotWorked, e.reviewLessons).any { it.isNotBlank() }) "In progress" else "Not started") {
                AutoText("What worked", e.reviewWorked, { v -> env.launch { env.repo.saveEvent(latestEvent(env, e).copy(reviewWorked = v)) } }, minLines = 3)
                AutoText("What did not work", e.reviewNotWorked, { v -> env.launch { env.repo.saveEvent(latestEvent(env, e).copy(reviewNotWorked = v)) } }, minLines = 3)
                AutoText("Audience response", e.reviewAudience, { v -> env.launch { env.repo.saveEvent(latestEvent(env, e).copy(reviewAudience = v)) } }, minLines = 3)
                Text("Content performance (from metrics you entered)", style = MaterialTheme.typography.titleSmall)
                val published = linked.filter { it.status == Status.PUBLISHED }
                Text("${published.size} of ${linked.size} linked posts published.", style = MaterialTheme.typography.bodySmall)
                published.forEach { c ->
                    val vs = d.versionsOf(c.id).map { it.id }.ifEmpty { listOf(c.id) }
                    val snap = vs.flatMap { d.snapshotsByVersion[it].orEmpty() }.maxByOrNull { it.dayEpoch }
                    Text("• ${c.title}: " + (snap?.let { "reach ${Fmt.num(it.reach)}, likes ${Fmt.num(it.likes)}, saves ${Fmt.num(it.saves)} (${Fmt.day(it.dayEpoch)})" } ?: "no metrics entered yet"), style = MaterialTheme.typography.bodySmall)
                }
                AutoText("Performance notes", e.reviewPerformance, { v -> env.launch { env.repo.saveEvent(latestEvent(env, e).copy(reviewPerformance = v)) } }, minLines = 3)
                AutoText("Lessons", e.reviewLessons, { v -> env.launch { env.repo.saveEvent(latestEvent(env, e).copy(reviewLessons = v)) } }, minLines = 3)
                AutoText("Next actions", e.reviewNext, { v -> env.launch { env.repo.saveEvent(latestEvent(env, e).copy(reviewNext = v)) } }, minLines = 3)
            }
            Gap(24.dp)
        }
    }

    shift?.let { (newStart, list) ->
        AlertDialog(
            onDismissRequest = { shift = null },
            title = { Text("Event date is changing") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("New date: ${Fmt.dateTime(newStart, zone)}", style = MaterialTheme.typography.titleSmall)
                    Text("Items set relative to the event would move like this. Review, then choose.", style = MaterialTheme.typography.bodySmall)
                    list.forEach { p -> Text("• ${p.label}\n   ${p.from?.let { Fmt.shortDay(it) } ?: "no date"} → ${Fmt.shortDay(p.to)}", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { env.launch { env.repo.applyEventShift(latestEvent(env, e), newStart, list) }; env.toast("Event and ${list.size} dates updated"); shift = null }) { Text("Shift ${list.size} dates") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { env.launch { env.repo.saveEvent(latestEvent(env, e).copy(startAt = newStart)) }; shift = null }) { Text("Only the event") }
                    TextButton(onClick = { shift = null }) { Text("Cancel") }
                }
            },
        )
    }
    newContent?.let { phase ->
        PromptDialog("New ${phase.label.lowercase()} post", "Title", "Create", onDone = { t ->
            env.launch {
                val c = env.repo.createContent(t, Status.PLANNED, eventId = id)
                env.repo.linkToEvent(c.id, id, phase.name, null)
                env.nav.push("post/${c.id}")
            }
        }, onDismiss = { newContent = null })
    }
    linkExisting?.let { phase ->
        val candidates = d.contents.filter { it.eventId != id && !it.archived }.sortedByDescending { it.updatedAt }
        AlertDialog(
            onDismissRequest = { linkExisting = null }, title = { Text("Link to ${phase.label.lowercase()}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (candidates.isEmpty()) Text("No other content to link.")
                    candidates.take(60).forEach { c ->
                        TextButton(onClick = { env.launch { env.repo.linkToEvent(c.id, id, phase.name, null) }; linkExisting = null }, Modifier.fillMaxWidth()) {
                            Text(c.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { linkExisting = null }) { Text("Close") } },
        )
    }
    if (confirmDelete) ConfirmDialog("Delete this event?", "Linked posts stay, but lose their event link. You can undo right after.", "Delete", {
        env.launch { env.repo.deleteEvent(id) }
        env.nav.pop()
        env.undoable("Event deleted") { env.repo.restoreEvent(id) }
    }, { confirmDelete = false })
}

private fun latestEvent(env: AppEnv, e: EventEntity): EventEntity = env.repo.data.value.eventById[e.id] ?: e
