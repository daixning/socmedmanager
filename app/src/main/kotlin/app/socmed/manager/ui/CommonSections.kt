@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.socmed.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.socmed.core.EventShift
import app.socmed.manager.data.LinkEntity
import app.socmed.manager.data.TaskEntity
import java.time.LocalDate

val TASK_CATEGORIES = listOf("PHOTOGRAPH" to "Photograph", "DESIGN" to "Design", "FILM" to "Film", "EDIT" to "Edit", "OTHER" to "Other")
fun taskCategoryLabel(key: String) = TASK_CATEGORIES.firstOrNull { it.first == key }?.second.orEmpty()

/** Saved reference links. Saving and reading them is fully offline; only opening one needs internet. */
@Composable
fun LinksSection(ownerType: String, ownerId: String) {
    val env = LocalEnv.current
    val d = LocalData.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val links = d.linksOf(ownerType, ownerId)
    var editing by remember { mutableStateOf<LinkEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (links.isEmpty()) Text("No links yet. Links are stored on your phone and readable offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        links.forEach { l ->
            SoftCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.label.ifBlank { l.url }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (l.label.isNotBlank()) Text(l.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (l.notes.isNotBlank()) Text(l.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { if (!openUrl(ctx, l.url)) env.toast("Couldn't open that link. (Opening links needs a browser and internet.)") }) { Icon(Icons.Filled.OpenInNew, "Open link") }
                    IconButton(onClick = { editing = l }) { Icon(Icons.Filled.Edit, "Edit link") }
                    IconButton(onClick = {
                        env.launch { env.repo.deleteLink(l.id) }
                        env.undoable("Link removed") { env.repo.restoreLink(l.id) }
                    }) { Icon(Icons.Filled.Delete, "Remove link") }
                }
            }
        }
        AddButton("Add link", { adding = true })
    }
    if (adding) LinkDialog(null, { url, label, notes -> env.launch { env.repo.addLink(ownerType, ownerId, url, label, notes) } }) { adding = false }
    editing?.let { l -> LinkDialog(l, { url, label, notes -> env.launch { env.repo.saveLink(l.copy(url = url.trim(), label = label.trim(), notes = notes.trim())) } }) { editing = null } }
}

@Composable
private fun LinkDialog(initial: LinkEntity?, onSave: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if (initial == null) "Add link" else "Edit link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(label, { label = it }, label = { Text("Label (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = url.isNotBlank(), onClick = { onSave(url, label, notes); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Checklist. For content it carries a production category (photograph / design / film / edit).
 * For events ([eventDate] given) each task can be set relative to the event date, e.g. −7 = a week before.
 */
@Composable
fun TasksSection(ownerType: String, ownerId: String, showCategory: Boolean, eventDate: LocalDate? = null) {
    val env = LocalEnv.current
    val d = LocalData.current
    val tasks = d.tasksOf(ownerType, ownerId)
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var rel by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tasks.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(t.done, { env.launch { env.repo.saveTask(t.copy(done = it)) } })
                Column(Modifier.weight(1f)) {
                    Text(t.text, style = MaterialTheme.typography.bodyMedium, textDecoration = if (t.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                    val meta = listOfNotNull(
                        taskCategoryLabel(t.category).takeIf { it.isNotBlank() },
                        t.relativeDays?.let { EventShift.describe(it) + " the event" },
                        t.dueDay?.let { "due ${Fmt.day(it)}" },
                    ).joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = {
                    env.launch { env.repo.deleteTask(t.id) }
                    env.undoable("Task removed") { env.repo.restoreTask(t.id) }
                }) { Icon(Icons.Filled.Delete, "Remove task", Modifier.size(20.dp)) }
            }
        }
        OutlinedTextField(text, { text = it }, label = { Text("New task") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (showCategory) SingleChips(TASK_CATEGORIES, category, { category = it })
        if (eventDate != null) OutlinedTextField(
            rel, { rel = it.filter { ch -> ch.isDigit() || ch == '-' }.take(4) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text("Days from event (−7 = a week before, 2 = two days after)") },
        )
        AddButton("Add task", {
            if (text.isNotBlank()) {
                val r = rel.toIntOrNull()
                val due = if (eventDate != null && r != null) eventDate.plusDays(r.toLong()).toEpochDay() else null
                env.launch { env.repo.addTask(ownerType, ownerId, text, category, due, r) }
                text = ""; rel = ""; category = ""
            }
        })
    }
}

fun joinCaption(hook: String, caption: String, cta: String, hashtags: String): String =
    listOf(hook, caption, cta, hashtags).filter { it.isNotBlank() }.joinToString("\n\n")

@Composable
fun CopyShareRow(text: String) {
    val env = LocalEnv.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.OutlinedButton(onClick = { copyText(ctx, "Caption", text); env.toast("Caption copied") }, enabled = text.isNotBlank()) {
            Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)); Text("  Copy caption")
        }
        androidx.compose.material3.OutlinedButton(onClick = { shareText(ctx, text) }, enabled = text.isNotBlank()) { Text("Share…") }
    }
}
