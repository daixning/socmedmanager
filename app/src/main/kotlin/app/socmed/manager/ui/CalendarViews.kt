@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.socmed.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.socmed.core.Platform
import app.socmed.core.Status
import app.socmed.manager.data.AppData
import app.socmed.manager.data.ContentEntity
import app.socmed.manager.data.EventEntity
import app.socmed.manager.data.PlanEntry
import app.socmed.manager.data.Prefs
import app.socmed.manager.data.Days
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class CalKind(val label: String, val icon: ImageVector) {
    POST("Post", Icons.Filled.Send), DEADLINE("Production deadline", Icons.Filled.Flag), EVENT("Event", Icons.Filled.Event)
}

data class CalItem(
    val kind: CalKind, val date: LocalDate, val title: String, val atMillis: Long?, val content: ContentEntity? = null,
    val entry: PlanEntry? = null, val event: EventEntity? = null, val overdue: Boolean = false,
)

fun calendarItems(d: AppData, filter: ContentFilter, now: Long): List<CalItem> {
    val zone = d.zone
    val out = ArrayList<CalItem>()
    for (e in d.planEntries(includeArchived = filter.showArchived)) {
        if (!filter.matches(d, e.content)) continue
        if (filter.platforms.isNotEmpty() && e.versions.isNotEmpty() && e.versions.none { it.platform in filter.platforms }) continue
        val at = e.atMillis ?: continue
        out += CalItem(CalKind.POST, Days.dayOf(at, zone), e.content.title.ifBlank { "Untitled" }, at, e.content, e, null, d.isOverdue(e, now))
    }
    for (c in d.contents) {
        val dl = c.deadlineDay ?: continue
        if (!filter.matches(d, c) || !Status.isOpen(c.status)) continue
        out += CalItem(CalKind.DEADLINE, LocalDate.ofEpochDay(dl), c.title.ifBlank { "Untitled" }, null, c, null, null, dl < LocalDate.now(zone).toEpochDay() && c.status != Status.ON_HOLD)
    }
    for (ev in d.events) {
        val st = ev.startAt ?: continue
        out += CalItem(CalKind.EVENT, Days.dayOf(st, zone), ev.name, st, null, null, ev)
    }
    return out.sortedWith(compareBy({ it.date }, { it.atMillis ?: 0L }))
}

private fun reschedule(env: AppEnv, d: AppData, e: PlanEntry, newDate: LocalDate) {
    val zone = d.zone
    env.launch {
        if (e.versions.isEmpty()) env.repo.update(e.content.id) { it.copy(plannedAt = app.socmed.core.Cal.moveToDate(it.plannedAt ?: e.atMillis!!, newDate, zone)) }
        else e.versions.forEach { v -> env.repo.saveVersion(v.copy(plannedAt = app.socmed.core.Cal.moveToDate(v.plannedAt ?: e.content.plannedAt ?: e.atMillis!!, newDate, zone))) }
    }
}

// ---------------------------------------------------------------- Calendar tab

@Composable
fun CalendarView(filter: ContentFilter, onFilter: (ContentFilter) -> Unit) {
    val env = LocalEnv.current
    val d = LocalData.current
    val zone = d.zone
    val now = System.currentTimeMillis()
    val mode = d.pref(Prefs.CAL_MODE, "month")
    var anchor by rememberSaveable { mutableStateOf(LocalDate.now(zone).toEpochDay()) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now(zone).toEpochDay()) }
    var addPostOn by remember { mutableStateOf<LocalDate?>(null) }
    var addEventOn by remember { mutableStateOf<LocalDate?>(null) }
    var moving by remember { mutableStateOf<PlanEntry?>(null) }
    var confirmMove by remember { mutableStateOf<Pair<PlanEntry, LocalDate>?>(null) }
    val items = calendarItems(d, filter, now)
    val byDate = items.groupBy { it.date }
    val a = LocalDate.ofEpochDay(anchor)

    Column(Modifier.fillMaxSize()) {
        FilterBar(filter, onFilter)
        Row(Modifier.padding(horizontal = PagePadding, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SingleChips(listOf("month" to "Month", "week" to "Week", "agenda" to "Agenda"), mode, { m -> if (m.isNotEmpty()) env.launch { env.repo.setPref(Prefs.CAL_MODE, m) } }, Modifier.weight(1f), allowClear = false)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = PagePadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (mode != "agenda") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { anchor = if (mode == "month") a.minusMonths(1).toEpochDay() else a.minusWeeks(1).toEpochDay() }) { Icon(Icons.Filled.ChevronLeft, "Previous") }
                    Text(if (mode == "month") Fmt.month(a.year, a.monthValue) else "Week of ${Fmt.shortDay(app.socmed.core.Cal.weekOf(a)[0])}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { anchor = LocalDate.now(zone).toEpochDay(); selected = anchor }) { Text("Today") }
                    IconButton(onClick = { anchor = if (mode == "month") a.plusMonths(1).toEpochDay() else a.plusWeeks(1).toEpochDay() }) { Icon(Icons.Filled.ChevronRight, "Next") }
                }
                Text("Time zone: ${zone.id} · Post = send icon, Deadline = flag icon, Event = calendar icon", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (mode) {
                "month" -> {
                    MonthGrid(a.year, a.monthValue, byDate, LocalDate.ofEpochDay(selected), zone) { selected = it.toEpochDay() }
                    DayPanel(LocalDate.ofEpochDay(selected), byDate[LocalDate.ofEpochDay(selected)].orEmpty(), zone, { addPostOn = it }, { addEventOn = it }, { moving = it })
                }
                "week" -> app.socmed.core.Cal.weekOf(a).forEach { day ->
                    DayPanel(day, byDate[day].orEmpty(), zone, { addPostOn = it }, { addEventOn = it }, { moving = it })
                }
                else -> AgendaList(items, zone, now, { moving = it })
            }
            UnscheduledArea(d, filter, zone) { moving = it }
            Gap(80.dp)
        }
    }
    addPostOn?.let { QuickAddDialog({ addPostOn = null }, plannedDate = it) }
    addEventOn?.let { day -> EventNameDialog(day) { addEventOn = null } }
    moving?.let { e ->
        val zoneNow = LocalDate.now(zone)
        val initial = e.atMillis?.let { Days.dayOf(it, zone) } ?: zoneNow
        DatePickerField2(initial, { picked -> moving = null; confirmMove = e to picked }, { moving = null })
    }
    confirmMove?.let { (e, to) ->
        ConfirmDialog("Reschedule post?", "Move “${e.content.title.ifBlank { "Untitled" }}” to ${Fmt.shortDay(to)}? Its time of day stays the same.", "Move", { reschedule(env, d, e, to) }, { confirmMove = null })
    }
}

/** Date picker as a dialog only (no button), used by reschedule and "schedule" actions. */
@Composable
fun DatePickerField2(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDay() * 86_400_000L)
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L))) } ?: onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { androidx.compose.material3.DatePicker(state) }
}

@Composable
fun EventNameDialog(day: LocalDate?, onDismiss: () -> Unit) {
    val env = LocalEnv.current
    val d = LocalData.current
    PromptDialog("New event", "Event name", "Create", onDone = { name ->
        env.launch {
            val e = env.repo.createEvent(name, day?.let { app.socmed.core.Cal.atTime(it, java.time.LocalTime.of(10, 0), d.zone) })
            env.nav.push("event/${e.id}")
        }
    }, onDismiss = onDismiss)
}

@Composable
private fun MonthGrid(year: Int, month: Int, byDate: Map<LocalDate, List<CalItem>>, selected: LocalDate, zone: ZoneId, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now(zone)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        app.socmed.core.Cal.monthGrid(year, month).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    val list = byDate[day].orEmpty()
                    val inMonth = day.monthValue == month
                    val isSel = day == selected
                    Column(
                        Modifier.weight(1f).height(62.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable { onSelect(day) }.padding(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            day.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (day == today) FontWeight.ExtraBold else FontWeight.Normal,
                            color = if (day == today) MaterialTheme.colorScheme.primary else if (inMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            CalKind.entries.forEach { k ->
                                val n = list.count { it.kind == k }
                                if (n > 0) Icon(k.icon, "$n ${k.label}", Modifier.size(11.dp), tint = if (list.any { it.kind == k && it.overdue }) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (list.isNotEmpty()) Text("${list.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPanel(day: LocalDate, items: List<CalItem>, zone: ZoneId, onAddPost: (LocalDate) -> Unit, onAddEvent: (LocalDate) -> Unit, onMove: (PlanEntry) -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.shortDay(day), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { onAddPost(day) }) { Text("+ Content") }
                TextButton(onClick = { onAddEvent(day) }) { Text("+ Event") }
            }
            if (items.isEmpty()) Text("Nothing on this day. Tap + Content or + Event to add.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            items.forEach { CalItemRow(it, zone, onMove) }
        }
    }
}

@Composable
fun CalItemRow(item: CalItem, zone: ZoneId, onMove: (PlanEntry) -> Unit) {
    val env = LocalEnv.current
    val d = LocalData.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { item.content?.let { env.nav.push("post/${it.id}") } ?: item.event?.let { env.nav.push("event/${it.id}") } }.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.content != null) ContentThumb(item.content, Modifier.size(46.dp), RoundedCornerShape(10.dp))
        else Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) { Icon(item.kind.icon, null, tint = MaterialTheme.colorScheme.onTertiaryContainer) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(item.kind.icon, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                Text(item.kind.label + (item.atMillis?.let { " · " + Fmt.time(it, zone) } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                item.entry?.let { e -> StatusChip(e.status, compact = true); PlatformLabels(e.platforms) }
                if (item.overdue) OverdueFlag()
            }
        }
        item.entry?.let { e -> TextButton(onClick = { onMove(e) }) { Text("Move") } }
    }
}

@Composable
private fun AgendaList(items: List<CalItem>, zone: ZoneId, now: Long, onMove: (PlanEntry) -> Unit) {
    val today = LocalDate.now(zone)
    val overdue = items.filter { it.overdue && it.date < today }
    if (overdue.isNotEmpty()) {
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error); Text("Overdue — still not published", style = MaterialTheme.typography.titleSmall)
                }
                Text("These stay on their original dates. Move or publish them yourself.", style = MaterialTheme.typography.bodySmall)
                overdue.forEach { CalItemRow(it, zone, onMove) }
            }
        }
    }
    val upcoming = items.filter { it.date >= today }.groupBy { it.date }
    if (upcoming.isEmpty()) EmptyState("Nothing scheduled from today onwards.")
    upcoming.forEach { (day, list) ->
        Text(if (day == today) "Today · ${Fmt.shortDay(day)}" else Fmt.shortDay(day), style = MaterialTheme.typography.titleSmall)
        list.forEach { CalItemRow(it, zone, onMove) }
    }
}

@Composable
private fun UnscheduledArea(d: AppData, filter: ContentFilter, zone: ZoneId, onNoop: (PlanEntry) -> Unit) {
    val env = LocalEnv.current
    val list = d.unscheduled().filter { filter.matches(d, it) }
    var schedule by remember { mutableStateOf<ContentEntity?>(null) }
    ExpandCard("Unscheduled content", summary = "${list.size} without a date", startExpanded = list.isNotEmpty() && list.size <= 5) {
        if (list.isEmpty()) Text("Everything active has a planned date.", style = MaterialTheme.typography.bodySmall)
        list.forEach { c ->
            Row(Modifier.fillMaxWidth().clickable { env.nav.push("post/${c.id}") }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ContentThumb(c, Modifier.size(44.dp), RoundedCornerShape(10.dp))
                Column(Modifier.weight(1f)) { Text(c.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis); StatusChip(c.status, compact = true) }
                OutlinedButton(onClick = { schedule = c }) { Text("Schedule") }
            }
        }
    }
    schedule?.let { c ->
        DatePickerField2(LocalDate.now(zone), { day ->
            env.launch {
                val at = app.socmed.core.Cal.atTime(day, java.time.LocalTime.of(9, 0), zone)
                env.repo.update(c.id) { it.copy(plannedAt = at) }
            }
            schedule = null
        }, { schedule = null })
    }
}

// ---------------------------------------------------------------- Visual Plan

@Composable
fun VisualPlanView(filter: ContentFilter, onFilter: (ContentFilter) -> Unit) {
    val env = LocalEnv.current
    val d = LocalData.current
    val zone = d.zone
    val now = System.currentTimeMillis()
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val startPref = d.pref(Prefs.PLAN_START, "")
    val start = runCatching { YearMonth.parse(startPref) }.getOrElse { YearMonth.now(zone) }
    var month by rememberSaveable { mutableStateOf(start.toString()) }
    val shown = runCatching { YearMonth.parse(month) }.getOrDefault(start)
    var pickStart by remember { mutableStateOf(false) }
    var expandedDay by remember { mutableStateOf<LocalDate?>(null) }

    val entries = d.planEntries(filter.showArchived).filter { filter.matches(d, it.content) }.filter { e -> filter.platforms.isEmpty() || e.versions.isEmpty() || e.versions.any { it.platform in filter.platforms } }
        .sortedBy { it.atMillis }
    val byMonth = entries.groupBy { YearMonth.from(Days.dayOf(it.atMillis!!, zone)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilterBar(filter, onFilter, Modifier.padding(horizontal = 0.dp), showStatus = true)
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("12-month overview", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { pickStart = true }) { Text("Start: ${Fmt.month(start.year, start.monthValue)}") }
                }
                (0 until 12).map { start.plusMonths(it.toLong()) }.chunked(if (wide) 4 else 3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { ym ->
                            val list = byMonth[ym].orEmpty()
                            val pub = list.count { it.status == Status.PUBLISHED }
                            val sel = ym == shown
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                    .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable { month = ym.toString() }.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(ym.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() } + " " + ym.year.toString().takeLast(2), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text("${list.size} planned", style = MaterialTheme.typography.labelSmall)
                                Text("$pub published", style = MaterialTheme.typography.labelSmall)
                                LinearProgressIndicator(progress = { if (list.isEmpty()) 0f else pub.toFloat() / list.size }, Modifier.fillMaxWidth())
                            }
                        }
                        repeat((if (wide) 4 else 3) - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }

        val monthEntries = byMonth[shown].orEmpty()
        val published = monthEntries.count { it.status == Status.PUBLISHED }
        val overdueN = monthEntries.count { d.isOverdue(it, now) }
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = shown.minusMonths(1).toString() }) { Icon(Icons.Filled.ChevronLeft, "Previous month") }
                    Text(Fmt.month(shown.year, shown.monthValue), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { month = shown.plusMonths(1).toString() }) { Icon(Icons.Filled.ChevronRight, "Next month") }
                }
                Text("Publishing progress: $published of ${monthEntries.size} published" + if (overdueN > 0) " · $overdueN overdue" else "", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { if (monthEntries.isEmpty()) 0f else published.toFloat() / monthEntries.size }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
                val dist = monthEntries.flatMap { e -> if (e.platforms.isEmpty()) listOf(null) else e.platforms }.groupBy { it }.mapValues { it.value.size }.toList().sortedByDescending { it.second }
                if (dist.isNotEmpty()) {
                    Text("Platform distribution", style = MaterialTheme.typography.labelLarge)
                    val max = dist.maxOf { it.second }
                    dist.forEach { (p, n) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (p != null) PlatformBadge(p) else Box(Modifier.size(22.dp))
                            Text(p?.label ?: "No platform", Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(progress = { n.toFloat() / max }, Modifier.weight(1f).height(8.dp).clip(CircleShape))
                            Text("$n", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Text("This plan is built from your saved posts. It never reads from social media.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val byDay = monthEntries.groupBy { Days.dayOf(it.atMillis!!, zone) }
        if (monthEntries.isEmpty()) EmptyState("No posts planned this month.")
        else if (wide) {
            app.socmed.core.Cal.monthGrid(shown.year, shown.monthValue).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    week.forEach { day ->
                        val list = byDay[day].orEmpty()
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${day.dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = if (day.monthValue == shown.monthValue) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                            list.take(2).forEach { PlanCard(it, zone, now, compact = true) }
                            if (list.size > 2) TextButton(onClick = { expandedDay = day }, contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp)) { Text("View all (${list.size})", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        } else {
            byDay.toSortedMap().forEach { (day, list) ->
                Text(Fmt.shortDay(day), style = MaterialTheme.typography.titleSmall)
                list.take(3).forEach { PlanCard(it, zone, now, compact = false) }
                if (list.size > 3) OutlinedButton(onClick = { expandedDay = day }) { Text("View all ${list.size} posts on this day") }
            }
        }
        Gap(80.dp)
    }
    expandedDay?.let { day ->
        AlertDialog(
            onDismissRequest = { expandedDay = null }, title = { Text(Fmt.shortDay(day)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { byMonth[YearMonth.from(day)].orEmpty().filter { Days.dayOf(it.atMillis!!, zone) == day }.forEach { PlanCard(it, zone, now, false) } } },
            confirmButton = { TextButton(onClick = { expandedDay = null }) { Text("Close") } },
        )
    }
    if (pickStart) StartMonthDialog(start, { env.launch { env.repo.setPref(Prefs.PLAN_START, it.toString()) }; month = it.toString() }) { pickStart = false }
}

@Composable
private fun StartMonthDialog(current: YearMonth, onPick: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    var year by remember { mutableStateOf(current.year) }
    var m by remember { mutableStateOf(current.monthValue) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Start the overview at…") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField("Year", (current.year - 3..current.year + 5).map { it.toString() to it.toString() }, year.toString(), { year = it.toInt() })
                DropdownField("Month", (1..12).map { it.toString() to java.time.Month.of(it).name.lowercase().replaceFirstChar { c -> c.uppercase() } }, m.toString(), { m = it.toInt() })
            }
        },
        confirmButton = { TextButton(onClick = { onPick(YearMonth.of(year, m)); onDismiss() }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PlanCard(e: PlanEntry, zone: ZoneId, now: Long, compact: Boolean) {
    val env = LocalEnv.current
    val d = LocalData.current
    val c = e.content
    val overdue = d.isOverdue(e, now)
    val excerpt = c.title.ifBlank { c.caption.take(60) }.ifBlank { "Untitled" }
    if (compact) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable { env.nav.push("post/${c.id}") }.padding(3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ContentThumb(c, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(8.dp), e.versions.firstOrNull()?.id)
            Text(excerpt, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(e.atMillis?.let { Fmt.time(it, zone) }.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusChip(e.status, compact = true)
        }
        return
    }
    SoftCard(Modifier.fillMaxWidth(), onClick = { env.nav.push("post/${c.id}") }) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ContentThumb(c, Modifier.size(92.dp), RoundedCornerShape(14.dp), e.versions.firstOrNull()?.id)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(excerpt, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (c.caption.isNotBlank() && c.title.isNotBlank()) Text(c.caption, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.shortDateTime(e.atMillis, zone), style = MaterialTheme.typography.labelMedium)
                PlatformLabels(e.platforms, withNames = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(e.status, compact = true); LabelChip(c.pillar)
                }
                if (overdue) OverdueFlag()
            }
        }
    }
}
