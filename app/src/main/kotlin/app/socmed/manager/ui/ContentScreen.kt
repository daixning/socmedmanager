@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.socmed.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.socmed.core.Format
import app.socmed.core.Platform
import app.socmed.core.Priority
import app.socmed.core.Status
import app.socmed.manager.data.AppData
import app.socmed.manager.data.ContentEntity
import app.socmed.manager.data.Prefs
import app.socmed.manager.data.splitTags
import java.time.LocalDate

// ---------------------------------------------------------------- shared helpers

fun AppData.plannedOf(c: ContentEntity): Long? = versionsOf(c.id).mapNotNull { it.plannedAt }.minOrNull() ?: c.plannedAt
fun AppData.platformsOf(c: ContentEntity): List<Platform> = versionsOf(c.id).map { Platform.from(it.platform) }

/** One filter shared by the Content tabs (All Posts, Calendar, Visual Plan, Board…). */
data class ContentFilter(
    val query: String = "",
    val statuses: Set<String> = emptySet(),
    val platforms: Set<String> = emptySet(),
    val pillar: String = "",
    val campaign: String = "",
    val showArchived: Boolean = false,
) {
    val activeCount get() = listOf(statuses.isNotEmpty(), platforms.isNotEmpty(), pillar.isNotEmpty(), campaign.isNotEmpty(), showArchived).count { it }
    fun matches(d: AppData, c: ContentEntity): Boolean {
        if (c.archived != showArchived) return false
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            val hay = (c.title + " " + c.caption + " " + c.tags + " " + c.description + " " + c.pillar + " " + c.campaign).lowercase()
            if (q !in hay) return false
        }
        if (statuses.isNotEmpty() && c.status !in statuses && d.versionsOf(c.id).none { it.status in statuses }) return false
        if (platforms.isNotEmpty() && d.versionsOf(c.id).none { it.platform in platforms }) return false
        if (pillar.isNotEmpty() && c.pillar != pillar) return false
        if (campaign.isNotEmpty() && c.campaign != campaign) return false
        return true
    }
}

@Composable
fun FilterBar(filter: ContentFilter, onChange: (ContentFilter) -> Unit, modifier: Modifier = Modifier, showStatus: Boolean = true) {
    val d = LocalData.current
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier.padding(horizontal = PagePadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                filter.query, { onChange(filter.copy(query = it)) }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Search posts") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = MaterialTheme.shapes.extraLarge,
            )
            OutlinedButton(onClick = { open = !open }) {
                Icon(Icons.Filled.FilterList, null, Modifier.size(18.dp)); Text(if (filter.activeCount > 0) "  Filters (${filter.activeCount})" else "  Filters")
            }
        }
        if (open) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showStatus) { Text("Status", style = MaterialTheme.typography.labelLarge); MultiChips(d.visibleStatuses.map { it.key to it.label }, filter.statuses, { onChange(filter.copy(statuses = it)) }) }
            Text("Platform", style = MaterialTheme.typography.labelLarge)
            MultiChips(Platform.entries.map { it.name to it.label }, filter.platforms, { onChange(filter.copy(platforms = it)) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField("Category", listOf("" to "All") + d.pillars().map { it to it }, filter.pillar, { onChange(filter.copy(pillar = it)) }, Modifier.weight(1f))
                DropdownField("Campaign", listOf("" to "All") + d.campaigns().map { it to it }, filter.campaign, { onChange(filter.copy(campaign = it)) }, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(filter.showArchived, { onChange(filter.copy(showArchived = it)) }); Text("  Show archived instead")
                Box(Modifier.weight(1f))
                TextButton(onClick = { onChange(ContentFilter()) }) { Text("Clear all") }
            }
        }
    }
}

// ---------------------------------------------------------------- quick add

@Composable
fun QuickAddDialog(onDismiss: () -> Unit, plannedDate: LocalDate? = null, eventId: String? = null, collection: String = "", defaultIdea: Boolean = true, onCreated: ((ContentEntity) -> Unit)? = null) {
    val env = LocalEnv.current
    val d = LocalData.current
    var title by remember { mutableStateOf("") }
    fun create(status: String, open: Boolean) {
        val planned = plannedDate?.let { app.socmed.core.Cal.atTime(it, java.time.LocalTime.of(9, 0), d.zone) }
        env.launch {
            val c = env.repo.createContent(title, status, collection, eventId, planned)
            if (onCreated != null) onCreated(c) else if (open) env.nav.push("post/${c.id}")
        }
        onDismiss()
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (plannedDate != null) "Add content on ${Fmt.shortDay(plannedDate)}" else "Quick add") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Only a title is needed. Add everything else later.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Row {
                TextButton(enabled = title.isNotBlank(), onClick = { create(Status.IDEA, false) }) { Text("Save idea") }
                TextButton(enabled = title.isNotBlank(), onClick = { create(Status.PLANNED, true) }) { Text("Plan & edit") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- tab host

private val TABS = listOf("posts" to "All Posts", "calendar" to "Calendar", "plan" to "Visual Plan", "board" to "Platform & Status", "visuals" to "Visuals Needed", "ideas" to "Ideas & References", "instagram" to "Instagram Preview")

@Composable
fun ContentScreen() {
    val env = LocalEnv.current
    val d = LocalData.current
    var filter by remember { mutableStateOf(ContentFilter()) }
    var quickAdd by remember { mutableStateOf(false) }
    val saved = d.pref(Prefs.LAST_TAB, "posts")
    var tab by rememberSaveable { mutableStateOf<String?>(null) }
    val current = tab ?: saved
    val idx = TABS.indexOfFirst { it.first == current }.coerceAtLeast(0)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = idx, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.background) {
                TABS.forEachIndexed { i, (key, label) ->
                    Tab(selected = i == idx, onClick = { tab = key; env.launch { env.repo.setPref(Prefs.LAST_TAB, key) } }, text = { Text(label, maxLines = 1) })
                }
            }
            when (TABS[idx].first) {
                "posts" -> { FilterBar(filter, { filter = it }); AllPostsView(filter) }
                "calendar" -> CalendarView(filter) { filter = it }
                "plan" -> VisualPlanView(filter) { filter = it }
                "board" -> { FilterBar(filter, { filter = it }, showStatus = false); BoardView(filter) }
                "visuals" -> VisualsNeededView()
                "ideas" -> IdeasView()
                "instagram" -> InstagramView()
            }
        }
        if (TABS[idx].first in setOf("posts", "board", "plan", "ideas")) {
            ExtendedFloatingActionButton(
                onClick = { quickAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text(if (TABS[idx].first == "ideas") "New idea" else "New") },
            )
        }
    }
    if (quickAdd) QuickAddDialog({ quickAdd = false }, collection = "")
}

// ---------------------------------------------------------------- All Posts

private enum class SortBy(val label: String) { PLANNED("Planned date"), TITLE("Title"), STATUS("Status"), CATEGORY("Category"), PRIORITY("Priority"), CREATED("Created") }

@Composable
fun AllPostsView(filter: ContentFilter) {
    val env = LocalEnv.current
    val d = LocalData.current
    val zone = d.zone
    val table = d.pref(Prefs.POSTS_LAYOUT, "table") == "table"
    var sort by rememberSaveable { mutableStateOf(SortBy.PLANNED) }
    var desc by rememberSaveable { mutableStateOf(false) }
    val statusOrder = d.statuses.associate { it.key to it.position }
    val rows = d.contents.filter { filter.matches(d, it) }.let { l ->
        val cmp: Comparator<ContentEntity> = when (sort) {
            SortBy.PLANNED -> compareBy { d.plannedOf(it) ?: Long.MAX_VALUE }
            SortBy.TITLE -> compareBy { it.title.lowercase() }
            SortBy.STATUS -> compareBy { statusOrder[it.status] ?: 99 }
            SortBy.CATEGORY -> compareBy { it.pillar.lowercase() }
            SortBy.PRIORITY -> compareByDescending { Priority.from(it.priority).ordinal }
            SortBy.CREATED -> compareBy { it.createdAt }
        }
        l.sortedWith(if (desc) cmp.reversed() else cmp)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = PagePadding, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField("Sort by", SortBy.entries.map { it.name to it.label }, sort.name, { sort = SortBy.valueOf(it) }, Modifier.weight(1f))
            IconButton(onClick = { desc = !desc }) { Icon(if (desc) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, if (desc) "Descending" else "Ascending") }
            IconButton(onClick = { env.launch { env.repo.setPref(Prefs.POSTS_LAYOUT, if (table) "list" else "table") } }) {
                Icon(if (table) Icons.Filled.ViewAgenda else Icons.Filled.TableChart, if (table) "Switch to list" else "Switch to table")
            }
        }
        Text("${rows.size} post${if (rows.size == 1) "" else "s"}", Modifier.padding(horizontal = PagePadding), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (rows.isEmpty()) { EmptyState(if (d.contents.isEmpty()) "No content yet. Tap New to save your first idea — a title is enough." else "Nothing matches these filters."); return@Column }
        if (table) PostsTable(rows, sort, desc, { s -> if (sort == s) desc = !desc else { sort = s; desc = false } })
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(rows, key = { it.id }) { c -> PostListCard(c, zone) }
        }
    }
}

@Composable
fun PostListCard(c: ContentEntity, zone: java.time.ZoneId) {
    val env = LocalEnv.current
    val d = LocalData.current
    val planned = d.plannedOf(c)
    val overdue = planned != null && planned < System.currentTimeMillis() && Status.isOpen(c.status) && c.status != Status.ON_HOLD
    SoftCard(Modifier.fillMaxWidth(), onClick = { env.nav.push("post/${c.id}") }) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ContentThumb(c, Modifier.size(76.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(c.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { StatusChip(c.status, compact = true); LabelChip(c.pillar) }
                PlatformLabels(d.platformsOf(c))
                Text(Fmt.shortDateTime(planned, zone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { VisualsFlag(show = c.visualsNeeded); if (overdue) OverdueFlag() }
            }
        }
    }
}

private val COLS: List<Triple<String, Int, SortBy?>> = listOf(
    Triple("Post", 220, SortBy.TITLE), Triple("Platform", 110, null), Triple("Status", 140, SortBy.STATUS),
    Triple("Category", 120, SortBy.CATEGORY), Triple("Planned", 150, SortBy.PLANNED), Triple("Visuals", 100, null),
)

@Composable
private fun PostsTable(rows: List<ContentEntity>, sort: SortBy, desc: Boolean, onSort: (SortBy) -> Unit) {
    val env = LocalEnv.current
    val d = LocalData.current
    val zone = d.zone
    val hs = rememberScrollState()
    val total = COLS.sumOf { it.second }
    Box(Modifier.fillMaxSize().horizontalScroll(hs)) {
        Column(Modifier.width(total.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)) {
                COLS.forEach { (label, w, s) ->
                    Row(Modifier.width(w.dp).then(if (s != null) Modifier.clickable { onSort(s) } else Modifier).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        if (s == sort) Icon(if (desc) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, null, Modifier.size(14.dp))
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 8.dp, end = 8.dp, bottom = 96.dp)) {
                items(rows, key = { it.id }) { c ->
                    val planned = d.plannedOf(c)
                    val overdue = planned != null && planned < System.currentTimeMillis() && Status.isOpen(c.status) && c.status != Status.ON_HOLD
                    Column {
                        Row(Modifier.fillMaxWidth().clickable { env.nav.push("post/${c.id}") }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.width(COLS[0].second.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ContentThumb(c, Modifier.size(44.dp), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                Text(c.title.ifBlank { "Untitled" }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Box(Modifier.width(COLS[1].second.dp).padding(horizontal = 10.dp)) { PlatformLabels(d.platformsOf(c)) }
                            Box(Modifier.width(COLS[2].second.dp).padding(horizontal = 6.dp)) { StatusChip(c.status, compact = true) }
                            Box(Modifier.width(COLS[3].second.dp).padding(horizontal = 6.dp)) { LabelChip(c.pillar) }
                            Column(Modifier.width(COLS[4].second.dp).padding(horizontal = 10.dp)) {
                                Text(Fmt.shortDateTime(planned, zone), style = MaterialTheme.typography.bodySmall)
                                if (overdue) OverdueFlag()
                            }
                            Box(Modifier.width(COLS[5].second.dp).padding(horizontal = 10.dp)) { if (c.visualsNeeded) VisualsFlag() else Text("—", style = MaterialTheme.typography.bodySmall) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Visuals Needed

@Composable
fun VisualsNeededView() {
    val env = LocalEnv.current
    val d = LocalData.current
    var cat by rememberSaveable { mutableStateOf("") }
    var sortByDeadline by rememberSaveable { mutableStateOf(true) }
    val items = d.contents.filter { it.visualsNeeded && !it.archived && Status.isOpen(it.status) }
        .filter { c -> cat.isEmpty() || d.tasksOf("content", c.id).any { it.category == cat && !it.done } }
        .sortedBy { c -> if (sortByDeadline) (c.deadlineDay ?: Long.MAX_VALUE) else (d.plannedOf(c) ?: Long.MAX_VALUE) }
    val today = LocalDate.now(d.zone).toEpochDay()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("What still needs to be photographed, designed, filmed or edited.", style = MaterialTheme.typography.bodyMedium)
            Gap()
            SingleChips(TASK_CATEGORIES, cat, { cat = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(sortByDeadline, { sortByDeadline = it }); Text(if (sortByDeadline) "  Sorted by production deadline" else "  Sorted by publishing date")
            }
        }
        if (items.isEmpty()) item { EmptyState("Nothing needs visuals. Switch “Visuals needed” on inside a post to see it here.") }
        items(items, key = { it.id }) { c ->
            val tasks = d.tasksOf("content", c.id)
            val late = c.deadlineDay != null && c.deadlineDay < today
            SoftCard(Modifier.fillMaxWidth(), onClick = { env.nav.push("post/${c.id}") }) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ContentThumb(c, Modifier.size(64.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(c.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { StatusChip(c.status, compact = true); LabelChip(c.pillar) }
                            Text("Deadline: ${Fmt.day(c.deadlineDay)}", style = MaterialTheme.typography.bodySmall, color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (late) OverdueFlag()
                        }
                    }
                    if (tasks.isNotEmpty()) {
                        Text("Checklist ${tasks.count { it.done }}/${tasks.size}", style = MaterialTheme.typography.labelLarge)
                        tasks.forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(t.done, { env.launch { env.repo.saveTask(t.copy(done = it)) } })
                                Text(t.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                LabelChip(taskCategoryLabel(t.category))
                            }
                        }
                    } else Text("No checklist yet — open the post to add tasks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { env.launch { env.repo.update(c.id) { it.copy(visualsNeeded = false) } }; env.toast("Marked visuals as done") }) { Text("Visuals are done") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Ideas & References

@Composable
fun IdeasView() {
    val env = LocalEnv.current
    val d = LocalData.current
    var collection by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var add by remember { mutableStateOf(false) }
    val ideas = d.contents.filter { !it.archived && (it.status == Status.IDEA || it.collection.isNotBlank()) }
        .filter { collection.isEmpty() || it.collection == collection }
        .filter { tag.isEmpty() || tag in splitTags(it.tags) }
        .filter { query.isBlank() || (it.title + it.description + it.tags).contains(query, true) }
        .sortedByDescending { it.createdAt }
    val grouped = ideas.groupBy { it.collection.ifBlank { "Unsorted" } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Save inspiration with notes, links and photos, then turn it into a planned post.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search ideas") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge)
            val cols = d.collections()
            if (cols.isNotEmpty()) { Text("Collections", style = MaterialTheme.typography.labelLarge); SingleChips(cols.map { it to it }, collection, { collection = it }) }
            val tags = d.allTags()
            if (tags.isNotEmpty()) { Text("Tags", style = MaterialTheme.typography.labelLarge); SingleChips(tags.take(30).map { it to "#$it" }, tag, { tag = it }) }
        }
        if (ideas.isEmpty()) item { EmptyState("No ideas yet. Tap New idea and type a title. You can attach links and photos afterwards.") }
        grouped.forEach { (name, list) ->
            item(key = "h-$name") { SectionTitle("$name · ${list.size}") }
            items(list, key = { it.id }) { c ->
                SoftCard(Modifier.fillMaxWidth(), onClick = { env.nav.push("post/${c.id}") }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ContentThumb(c, Modifier.size(72.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(c.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                if (c.description.isNotBlank()) Text(c.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val links = d.linksOf("content", c.id).size; val photos = d.assetsOf("content", c.id, "INSPIRATION").size
                                Text("$links link${if (links == 1) "" else "s"} · $photos photo${if (photos == 1) "" else "s"}${c.targetDay?.let { " · target ${Fmt.day(it)}" }.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        splitTags(c.tags).takeIf { it.isNotEmpty() }?.let { t -> Text(t.joinToString("  ") { "#$it" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusChip(c.status, compact = true)
                            if (c.status == Status.IDEA) OutlinedButton(onClick = {
                                env.launch { env.repo.update(c.id) { it.copy(status = Status.PLANNED) } }
                                env.toast("Now a planned post — links and photos came with it")
                                env.nav.push("post/${c.id}")
                            }) { Text("Turn into planned post") }
                        }
                    }
                }
            }
        }
    }
    if (add) QuickAddDialog({ add = false }, collection = collection)
}
