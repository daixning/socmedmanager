@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.socmed.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.socmed.core.EventPhase
import app.socmed.core.EventShift
import app.socmed.core.Format
import app.socmed.core.GOALS
import app.socmed.core.Platform
import app.socmed.core.Priority
import app.socmed.core.Status
import app.socmed.manager.data.AssetRole
import app.socmed.manager.data.ContentEntity
import app.socmed.manager.data.VersionEntity
import app.socmed.manager.data.isSampleId

private val FORMAT_OPTIONS = Format.entries.map { it.name to it.label }

/** Full detail + edit screen for one post. Every field autosaves into the same record every other view reads. */
@Composable
fun PostScreen(id: String) {
    val env = LocalEnv.current
    val d = LocalData.current
    val zone = d.zone
    val c = d.contentById[id]
    if (c == null) {
        DetailScaffold("Post", { env.nav.pop() }) { pad -> EmptyState("This post was deleted.", Modifier.padding(pad)) }
        return
    }
    val versions = d.versionsOf(id)
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val fullText = joinCaption(c.hook, c.caption, c.cta, c.hashtags)
    val ctx = androidx.compose.ui.platform.LocalContext.current

    DetailScaffold(c.title.ifBlank { "New post" }, { env.nav.pop() }, actions = {
        IconButton(onClick = { shareText(ctx, fullText) }, enabled = fullText.isNotBlank()) { Icon(Icons.Filled.Share, "Share caption") }
        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More actions") }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                menu = false
                env.launch { env.repo.duplicateContent(id)?.let { env.toast("Duplicated as “${it.title}”") } }
            })
            DropdownMenuItem(text = { Text(if (c.archived) "Unarchive" else "Archive") }, onClick = {
                menu = false
                env.launch { env.repo.archive(id, !c.archived) }
                env.toast(if (c.archived) "Restored from archive" else "Archived — find it with the Archived filter")
            })
            DropdownMenuItem(text = { Text("Delete…") }, onClick = { menu = false; confirmDelete = true })
        }
    }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = PagePadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isSampleId(id)) Text("Sample data — remove it any time in Settings.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            // ---- essentials
            AutoText("Title", c.title, { v -> env.launch { env.repo.update(id) { it.copy(title = v) } } }, singleLine = true)
            Text("Status", style = MaterialTheme.typography.titleSmall)
            SingleChips(d.visibleStatuses.map { it.key to it.label }, c.status, { s -> if (s.isNotEmpty()) env.launch { env.repo.setContentStatus(id, s) } }, allowClear = false)
            if (c.status != Status.PUBLISHED && (c.plannedAt ?: Long.MAX_VALUE) < System.currentTimeMillis() && Status.isOpen(c.status) && c.status != Status.ON_HOLD)
                Row { OverdueFlag(); Text("  Planned time has passed. Nothing is changed automatically — set the status yourself.", style = MaterialTheme.typography.bodySmall) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(c.visualsNeeded, { v -> env.launch { env.repo.update(id) { it.copy(visualsNeeded = v) } } })
                Text("  Visuals needed (photo / design / video still to produce)", Modifier.weight(1f))
            }
            Text("Priority", style = MaterialTheme.typography.titleSmall)
            SingleChips(Priority.entries.map { it.name to it.label }, c.priority, { p -> if (p.isNotEmpty()) env.launch { env.repo.update(id) { it.copy(priority = p) } } }, allowClear = false)

            // ---- planning
            ExpandCard("Planning", summary = listOf(c.pillar, c.campaign, Format.labelOf(c.format).takeIf { c.format.isNotBlank() }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "), startExpanded = true) {
                AutoText("Content pillar / category", c.pillar, { v -> env.launch { env.repo.update(id) { it.copy(pillar = v.trim()) } } }, singleLine = true)
                val pillars = d.pillars()
                if (pillars.isNotEmpty()) SingleChips(pillars.map { it to it }, c.pillar, { v -> env.launch { env.repo.update(id) { it.copy(pillar = v) } } })
                AutoText("Campaign", c.campaign, { v -> env.launch { env.repo.update(id) { it.copy(campaign = v.trim()) } } }, singleLine = true)
                AutoText("Tags (comma separated)", c.tags, { v -> env.launch { env.repo.update(id) { it.copy(tags = v) } } }, singleLine = true)
                AutoText("Intended audience", c.audience, { v -> env.launch { env.repo.update(id) { it.copy(audience = v) } } }, singleLine = true)
                Text("Goal", style = MaterialTheme.typography.titleSmall)
                SingleChips(GOALS.map { it to it }, c.goal, { v -> env.launch { env.repo.update(id) { it.copy(goal = v) } } })
                if (c.goal !in GOALS) AutoText("Custom goal (or pick one above)", c.goal, { v -> env.launch { env.repo.update(id) { it.copy(goal = v.trim()) } } }, singleLine = true)
                Text("Format", style = MaterialTheme.typography.titleSmall)
                SingleChips(FORMAT_OPTIONS, c.format, { v -> env.launch { env.repo.update(id) { it.copy(format = v) } } })
                DatePickerField("Target date", c.targetDay, { v -> env.launch { env.repo.update(id) { it.copy(targetDay = v) } } })
                DatePickerField("Production deadline", c.deadlineDay, { v -> env.launch { env.repo.update(id) { it.copy(deadlineDay = v) } } })
                if (versions.isEmpty()) {
                    DateTimeField("Planned publishing date & time", c.plannedAt, zone, { v -> env.launch { env.repo.update(id) { it.copy(plannedAt = v) } } })
                    DateTimeField("Actual publishing date & time", c.publishedAt, zone, { v -> env.launch { env.repo.update(id) { it.copy(publishedAt = v) } } })
                    AutoText("Published post URL", c.publishedUrl, { v -> env.launch { env.repo.update(id) { it.copy(publishedUrl = v.trim()) } } }, singleLine = true)
                    Text("Tip: add a platform below to track each platform's date, status and URL separately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    DateTimeField("Default planned date & time (platforms without their own)", c.plannedAt, zone, { v -> env.launch { env.repo.update(id) { it.copy(plannedAt = v) } } })
                }
                Text("A planned time is only a reminder. Nothing becomes Published unless you mark it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            ExpandCard("Description", summary = c.description) {
                AutoText("Detailed description", c.description, { v -> env.launch { env.repo.update(id) { it.copy(description = v) } } }, minLines = 4)
            }

            ExpandCard("Caption, hook & hashtags", summary = c.caption.ifBlank { c.hook }) {
                AutoText("Hook", c.hook, { v -> env.launch { env.repo.update(id) { it.copy(hook = v) } } }, minLines = 1)
                AutoText("Caption", c.caption, { v -> env.launch { env.repo.update(id) { it.copy(caption = v) } } }, minLines = 4)
                AutoText("Call to action", c.cta, { v -> env.launch { env.repo.update(id) { it.copy(cta = v) } } })
                AutoText("Hashtags", c.hashtags, { v -> env.launch { env.repo.update(id) { it.copy(hashtags = v) } } })
                CopyShareRow(fullText)
                Text("Sharing opens another app. It never marks the post as published.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ---- platforms
            ExpandCard("Platforms & versions", summary = if (versions.isEmpty()) "None selected" else Fmt.platforms(versions.map { Platform.from(it.platform) }), startExpanded = versions.isNotEmpty()) {
                Text("Choose where this will be posted. Each platform gets its own version.", style = MaterialTheme.typography.bodySmall)
                val active = versions.map { it.platform }.toSet()
                MultiChips(Platform.entries.map { it.name to it.label }, active, { sel ->
                    val added = sel - active; val removed = active - sel
                    added.forEach { p -> env.launch { env.repo.addPlatform(id, p) } }
                    removed.forEach { p ->
                        versions.firstOrNull { it.platform == p }?.let { v ->
                            env.launch { env.repo.removePlatform(v.id) }
                            env.undoable("${Platform.from(p).label} version removed") { env.repo.addPlatform(id, p) }
                        }
                    }
                })
                versions.forEach { v -> key(v.id) { VersionCard(c, v) } }
            }

            // ---- references & visuals
            ExpandCard("Reference links", summary = "${d.linksOf("content", id).size} saved") { LinksSection("content", id) }
            ExpandCard("Inspiration photos", summary = "${d.assetsOf("content", id, AssetRole.INSPIRATION).size} photos") {
                AssetSection("Inspiration", "content", id, AssetRole.INSPIRATION, hint = "Mood boards and examples. Copied into the app so they never disappear.")
            }
            ExpandCard("Final publishing assets", summary = "${d.assetsOf("content", id).count { it.role == AssetRole.FINAL || it.role == AssetRole.CAROUSEL || it.role == AssetRole.VIDEO }} files") {
                AssetSection("Final images", "content", id, AssetRole.FINAL, coverId = c.coverAssetId, onSetCover = { a -> env.launch { env.repo.setCover(id, a) } })
                AssetSection("Carousel images (in order)", "content", id, AssetRole.CAROUSEL, ordered = true, coverId = c.coverAssetId, onSetCover = { a -> env.launch { env.repo.setCover(id, a) } },
                    hint = "Use “Move earlier / later” to order slides, and “Use as cover” to pick the cover.")
                AssetSection("Video", "content", id, AssetRole.VIDEO, allowVideo = true, coverId = c.coverAssetId, onSetCover = { a -> env.launch { env.repo.setCover(id, a) } },
                    hint = "Choose any image above (or a video frame) as the cover with “Use as cover”.")
            }
            ExpandCard("Production checklist", summary = d.tasksOf("content", id).let { t -> if (t.isEmpty()) "No tasks" else "${t.count { it.done }}/${t.size} done" }) {
                TasksSection("content", id, showCategory = true)
            }
            ExpandCard("Event / campaign link", summary = c.eventId?.let { d.eventById[it]?.name }.orEmpty()) { EventLinkSection(c) }
            ExpandCard("Performance", summary = "Manual metrics") { PerformanceSection(c, versions) }
            ExpandCard("Private notes", summary = c.notes) {
                AutoText("Notes (only on your phone)", c.notes, { v -> env.launch { env.repo.update(id) { it.copy(notes = v) } } }, minLines = 4)
            }
            OutlinedButton(onClick = { confirmDelete = true }, Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Delete, null, Modifier.size(18.dp)); Text("  Delete post")
            }
            Gap(24.dp)
        }
    }
    if (confirmDelete) ConfirmDialog("Delete this post?", "“${c.title.ifBlank { "Untitled" }}” will be removed from every view. You can undo right after.", "Delete", {
        env.launch { env.repo.softDeleteContent(id) }
        env.nav.pop()
        env.undoable("Post deleted") { env.repo.restoreContent(id) }
    }, { confirmDelete = false })
}

@Composable
private fun VersionCard(c: ContentEntity, v: VersionEntity) {
    val env = LocalEnv.current
    val d = LocalData.current
    val zone = d.zone
    val p = Platform.from(v.platform)
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlatformBadge(p); Text(p.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(v.status, compact = true)
                MoveToMenu(v.status, { s -> env.launch { env.repo.setVersionStatus(v.id, s) } })
            }
            DropdownField("Format for ${p.label} (blank = same as main)", listOf("" to "Same as main") + FORMAT_OPTIONS, v.format, { f -> env.launch { env.repo.saveVersion(v.copy(format = f)) } })
            AutoText("${p.label} caption (blank = use main caption)", v.caption, { t -> env.launch { env.repo.saveVersion(latest(env, v).copy(caption = t)) } }, minLines = 3)
            AutoText("${p.label} hashtags", v.hashtags, { t -> env.launch { env.repo.saveVersion(latest(env, v).copy(hashtags = t)) } })
            DateTimeField("Planned publishing (${zone.id})", v.plannedAt ?: c.plannedAt, zone, { t -> env.launch { env.repo.saveVersion(latest(env, v).copy(plannedAt = t)) } })
            if (v.plannedAt == null && c.plannedAt != null) Text("Using the post's default date. Pick a date to give ${p.label} its own.", style = MaterialTheme.typography.bodySmall)
            DateTimeField("Actual publishing time", v.publishedAt, zone, { t -> env.launch { env.repo.saveVersion(latest(env, v).copy(publishedAt = t)) } })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (v.status != Status.PUBLISHED) OutlinedButton(onClick = { env.launch { env.repo.setVersionStatus(v.id, Status.PUBLISHED) } }) { Text("Mark as published now") }
                else OutlinedButton(onClick = { env.launch { env.repo.setVersionStatus(v.id, Status.READY) } }) { Text("Not published after all") }
            }
            AutoText("Published post URL", v.url, { t -> env.launch { env.repo.saveVersion(latest(env, v).copy(url = t.trim())) } }, singleLine = true)
            AssetSection("${p.label} assets", "version", v.id, AssetRole.FINAL, allowVideo = true, coverId = v.coverAssetId, onSetCover = { a -> env.launch { env.repo.setCover(c.id, a, v.id) } })
            CopyShareRow(joinCaption(c.hook, v.caption.ifBlank { c.caption }, c.cta, v.hashtags.ifBlank { c.hashtags }))
        }
    }
}

/** The freshest stored copy, so slow autosaves never overwrite a status or date that changed in the meantime. */
private fun latest(env: AppEnv, v: VersionEntity): VersionEntity = env.repo.data.value.versionById[v.id] ?: v

@Composable
private fun EventLinkSection(c: ContentEntity) {
    val env = LocalEnv.current
    val d = LocalData.current
    val ev = c.eventId?.let { d.eventById[it] }
    var offsetText by remember(c.relPublishDays, c.eventId) { mutableStateOf(c.relPublishDays?.toString().orEmpty()) }
    var deadlineText by remember(c.relDeadlineDays, c.eventId) { mutableStateOf(c.relDeadlineDays?.toString().orEmpty()) }
    val events = d.events.sortedByDescending { it.startAt ?: 0L }
    DropdownField("Linked event", listOf("" to "None") + events.map { it.id to it.name }, c.eventId.orEmpty(), { e ->
        env.launch { env.repo.linkToEvent(c.id, e.ifBlank { null }, c.eventPhase.ifBlank { EventPhase.BEFORE.name }, c.relPublishDays, c.relDeadlineDays) }
    })
    if (ev != null) {
        Text("When in the event?", style = MaterialTheme.typography.titleSmall)
        SingleChips(EventPhase.entries.map { it.name to it.label }, c.eventPhase, { ph -> if (ph.isNotEmpty()) env.launch { env.repo.linkToEvent(c.id, ev.id, ph, c.relPublishDays, c.relDeadlineDays) } }, allowClear = false)
        Text(EventPhase.from(c.eventPhase)?.hint.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ev.startAt != null) {
            OutlinedTextField(offsetText, { offsetText = it.filter { ch -> ch.isDigit() || ch == '-' }.take(4) }, label = { Text("Publish this many days from the event (−7 = a week before)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(deadlineText, { deadlineText = it.filter { ch -> ch.isDigit() || ch == '-' }.take(4) }, label = { Text("Production deadline, days from the event (−10 = ten days before)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { env.launch { env.repo.linkToEvent(c.id, ev.id, c.eventPhase.ifBlank { EventPhase.BEFORE.name }, offsetText.toIntOrNull(), deadlineText.toIntOrNull()) } }) {
                Text(if (offsetText.toIntOrNull() != null || deadlineText.toIntOrNull() != null) "Apply the day offsets" else "Clear the date links")
            }
            offsetText.toIntOrNull()?.let { Text("Publishes ${EventShift.describe(it)} the event.", style = MaterialTheme.typography.bodySmall) }
            deadlineText.toIntOrNull()?.let { Text("Production due ${EventShift.describe(it)} the event.", style = MaterialTheme.typography.bodySmall) }
            Text("With a day offset, this post follows the event if you ever move the event date (you review the changes first).", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = { env.nav.push("event/${ev.id}") }) { Text("Open event") }
    }
}

@Composable
private fun PerformanceSection(c: ContentEntity, versions: List<VersionEntity>) {
    val env = LocalEnv.current
    val d = LocalData.current
    val targets = if (versions.isEmpty()) listOf(c.id to "Post") else versions.map { it.id to Platform.from(it.platform).label }
    Text("Metrics are typed in by you — nothing is fetched automatically. Each entry is a dated snapshot; they are never added together.", style = MaterialTheme.typography.bodySmall)
    targets.forEach { (vid, label) ->
        val snaps = d.snapshotsByVersion[vid].orEmpty().sortedByDescending { it.dayEpoch }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$label · ${snaps.size} snapshot${if (snaps.size == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall)
            snaps.take(3).forEach { s ->
                Text("${Fmt.day(s.dayEpoch)} — reach ${Fmt.num(s.reach)}, likes ${Fmt.num(s.likes)}, saves ${Fmt.num(s.saves)}", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = { env.nav.push("metrics/$vid") }) { Text(if (snaps.isEmpty()) "Add metrics" else "Add / review metrics") }
        }
    }
}
