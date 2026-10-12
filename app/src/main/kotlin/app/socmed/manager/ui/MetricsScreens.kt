@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.socmed.manager.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.socmed.core.Csv
import app.socmed.core.CsvImport
import app.socmed.core.ImportField
import app.socmed.core.ImportRow
import app.socmed.core.Metric
import app.socmed.core.Platform
import app.socmed.core.Rates
import app.socmed.manager.data.AppData
import app.socmed.manager.data.SnapshotEntity
import app.socmed.manager.data.newId
import app.socmed.manager.data.toValues
import app.socmed.manager.data.withValues
import java.time.LocalDate

/** Manual metrics entry. Each save is one dated snapshot in a post's history. Blank = unknown (never zero). */
@Composable
fun MetricsScreen(versionId: String) {
    val env = LocalEnv.current
    val d = LocalData.current
    val version = d.versionById[versionId]
    val content = version?.let { d.contentById[it.contentId] } ?: d.contentById[versionId]
    if (content == null) { DetailScaffold("Metrics", { env.nav.pop() }) { pad -> EmptyState("This post no longer exists.", Modifier.padding(pad)) }; return }
    val label = version?.let { Platform.from(it.platform).label } ?: "Post"
    val snaps = d.snapshotsByVersion[versionId].orEmpty().sortedByDescending { it.dayEpoch }
    var editing by remember { mutableStateOf<SnapshotEntity?>(null) }
    var day by remember { mutableStateOf<Long?>(LocalDate.now(d.zone).toEpochDay()) }
    var note by remember { mutableStateOf("") }
    val fields = remember { mutableStateOf(Metric.entries.associateWith { "" }) }

    fun load(s: SnapshotEntity?) {
        editing = s
        day = s?.dayEpoch ?: LocalDate.now(d.zone).toEpochDay()
        note = s?.note.orEmpty()
        val v = s?.toValues().orEmpty()
        fields.value = Metric.entries.associateWith { m -> v[m]?.let { x -> if (x == Math.rint(x)) x.toLong().toString() else x.toString() } ?: "" }
    }
    val parsed: Map<Metric, Double?> = fields.value.mapValues { (_, t) -> CsvImport.parseNumber(t) }
    val invalid = fields.value.filter { (_, t) -> t.isNotBlank() && CsvImport.parseNumber(t) == null }.keys

    DetailScaffold("Metrics · ${content.title.ifBlank { "Untitled" }}", { env.nav.pop() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = PagePadding, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("$label · typed in by you. Leave a box empty if you don't know the number — empty is stored as unknown, not zero.", style = MaterialTheme.typography.bodyMedium)
            Text(if (editing == null) "New snapshot" else "Editing snapshot from ${Fmt.day(editing!!.dayEpoch)}", style = MaterialTheme.typography.titleMedium)
            DatePickerField("Snapshot date (when you read these numbers)", day, { day = it })
            Metric.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { m ->
                        OutlinedTextField(
                            fields.value.getValue(m), { t -> fields.value = fields.value + (m to t) }, label = { Text(m.label) }, singleLine = true,
                            isError = m in invalid, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Column(Modifier.weight(1f)) {}
                }
            }
            OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())

            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Rates for this snapshot", style = MaterialTheme.typography.titleSmall)
                    val vals = parsed.filterValues { it != null }.mapValues { it.value!! }
                    Rates.all.forEach { def ->
                        val r = Rates.compute(def, vals)
                        Text(def.name + ": " + (r.value?.let { Fmt.pct(it) } ?: "not calculated (${r.reason})"), style = MaterialTheme.typography.bodyMedium)
                        Text("= ${def.formula}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = day != null && invalid.isEmpty() && parsed.values.any { it != null }, onClick = {
                    val t = System.currentTimeMillis()
                    val base = editing ?: SnapshotEntity((if (content.id.startsWith("sample-")) "sample-" else "") + newId(), versionId, content.id, day!!, createdAt = t, updatedAt = t)
                    env.launch { env.repo.saveSnapshot(base.copy(dayEpoch = day!!, note = note).withValues(parsed)) }
                    env.toast("Snapshot saved"); load(null)
                }) { Text(if (editing == null) "Save snapshot" else "Update snapshot") }
                if (editing != null) TextButton(onClick = { load(null) }) { Text("Cancel edit") }
            }
            if (invalid.isNotEmpty()) Text("Some boxes are not numbers: ${invalid.joinToString { it.label }}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

            SectionTitle("History (${snaps.size})")
            Text("Every snapshot is kept. Analytics uses the latest one per post (or the latest on or before the date you choose) — they are never added together.", style = MaterialTheme.typography.bodySmall)
            snaps.forEach { s ->
                SoftCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Fmt.day(s.dayEpoch), style = MaterialTheme.typography.titleSmall)
                            Text(s.toValues().entries.joinToString(" · ") { "${it.key.label} ${Fmt.num(it.value)}" }.ifBlank { "No values" }, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { load(s) }) { Text("Edit") }
                        TextButton(onClick = { env.launch { env.repo.deleteSnapshot(s.id) }; env.undoable("Snapshot deleted") { env.repo.restoreSnapshot(s.id) } }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- CSV import

private sealed interface Match { data class Ok(val contentId: String, val versionId: String) : Match; data class Fail(val reason: String) : Match }

private fun platformMatches(v: app.socmed.manager.data.VersionEntity, name: String?) =
    name != null && (v.platform.equals(name.trim(), true) || Platform.from(v.platform).label.equals(name.trim(), true))

private fun resolve(row: ImportRow, d: AppData): Match {
    row.postId?.let { id ->
        d.versionById[id]?.let { return Match.Ok(it.contentId, it.id) }
        d.contentById[id]?.let { c ->
            val vs = d.versionsOf(c.id)
            return when {
                vs.isEmpty() -> Match.Ok(c.id, c.id)
                vs.size == 1 -> Match.Ok(c.id, vs[0].id)
                else -> vs.firstOrNull { platformMatches(it, row.platform) }?.let { Match.Ok(c.id, it.id) } ?: Match.Fail("Post has several platforms; add a platform column")
            }
        }
    }
    val title = row.title ?: return Match.Fail("Post ID not found")
    val cs = d.contents.filter { it.title.trim().equals(title.trim(), true) }
    if (cs.isEmpty()) return Match.Fail("No post titled “$title”")
    if (cs.size > 1) return Match.Fail("Several posts share the title “$title”")
    val c = cs[0]
    val vs = d.versionsOf(c.id)
    if (vs.isEmpty()) return Match.Ok(c.id, c.id)
    val wanted = row.platform?.trim()
    val v = if (wanted == null) vs.singleOrNull() else vs.firstOrNull { platformMatches(it, wanted) }
    return if (v != null) Match.Ok(c.id, v.id) else Match.Fail(if (wanted == null) "“$title” has several platforms; add a platform column" else "“$title” has no $wanted version")
}

@Composable
fun CsvImportScreen() {
    val env = LocalEnv.current
    val d = LocalData.current
    var table by remember { mutableStateOf<List<List<String>>?>(null) }
    var fileName by remember { mutableStateOf("") }
    var mapping by remember { mutableStateOf<Map<Int, ImportField>>(emptyMap()) }
    var dayFirst by remember { mutableStateOf(true) }
    var defaultDay by remember { mutableStateOf<Long?>(LocalDate.now(d.zone).toEpochDay()) }
    var problem by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) env.launch {
            try {
                val text = env.app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Could not read the file")
                if (text.length > 5_000_000) error("File is larger than 5 MB")
                val t = Csv.parse(text)
                if (t.size < 2) error("The file needs a header row and at least one data row")
                table = t; mapping = CsvImport.guessMapping(t[0]); fileName = uri.lastPathSegment.orEmpty(); problem = null
            } catch (e: Exception) { problem = e.message ?: "Could not import that file" }
        }
    }
    val rows = table?.let { CsvImport.rows(it, mapping, dayFirst, defaultDay ?: 0L) }.orEmpty()
    val matches = rows.map { it to if (it.valid) resolve(it, d) else Match.Fail(it.errors.first()) }
    val ready = matches.filter { it.second is Match.Ok }

    DetailScaffold("Import analytics CSV", { env.nav.pop() }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = PagePadding, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Import metrics you exported from Instagram, a spreadsheet, or this app. Each row becomes a dated snapshot on the matching post. Blank cells stay unknown.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*")) }) { Text(if (table == null) "Choose CSV file" else "Choose a different file") }
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val t = table
            if (t != null) {
                Text("$fileName · ${t.size - 1} data rows", style = MaterialTheme.typography.titleSmall)
                ExpandCard("1. Match columns", summary = "${mapping.size} of ${t[0].size} columns mapped", startExpanded = true) {
                    val options = listOf("" to "Ignore this column") + ImportField.entries.map { it.name to it.label }
                    t[0].forEachIndexed { i, h ->
                        DropdownField("Column “$h”  (e.g. ${t.getOrNull(1)?.getOrNull(i).orEmpty().take(24)})", options, mapping[i]?.name.orEmpty(), { sel ->
                            mapping = if (sel.isEmpty()) mapping - i else mapping.filterValues { it.name != sel } + (i to ImportField.valueOf(sel))
                        })
                    }
                }
                ExpandCard("2. Dates", summary = if (dayFirst) "day/month/year" else "month/day/year") {
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(dayFirst, { dayFirst = it }); Text("  Dates like 03/04/2026 are day first (3 April)") }
                    DatePickerField("Snapshot date for rows without a date", defaultDay, { defaultDay = it })
                }
                SectionTitle("3. Preview")
                val bad = matches.size - ready.size
                Text("${ready.size} rows ready · $bad will be skipped", style = MaterialTheme.typography.titleSmall)
                val existing = ready.count { (r, m) -> d.snapshotsByVersion[(m as Match.Ok).versionId].orEmpty().any { it.dayEpoch == r.dayEpoch } }
                if (existing > 0) Text("$existing of them match a snapshot already saved for the same post and date — those will be updated, not duplicated.", style = MaterialTheme.typography.bodySmall)
                matches.take(30).forEach { (r, m) ->
                    SoftCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Row ${r.line}: " + (r.title ?: r.postId ?: "?") + " · " + (r.dayEpoch?.let { Fmt.day(it) } ?: "no date"), style = MaterialTheme.typography.bodyMedium)
                            Text(r.values.entries.joinToString(" · ") { "${it.key.label} ${Fmt.num(it.value)}" }, style = MaterialTheme.typography.bodySmall)
                            when (m) {
                                is Match.Ok -> Text("✓ matches “${d.contentById[m.contentId]?.title}”${d.versionById[m.versionId]?.let { " (${Platform.from(it.platform).label})" }.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                is Match.Fail -> Text("Skipped: ${m.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                if (matches.size > 30) Text("…and ${matches.size - 30} more rows.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = ready.isNotEmpty(), onClick = {
                    env.launch {
                        val now = System.currentTimeMillis()
                        val list = ready.map { (r, m) ->
                            m as Match.Ok
                            val existingSnap = d.snapshotsByVersion[m.versionId].orEmpty().firstOrNull { it.dayEpoch == r.dayEpoch }
                            val base = existingSnap ?: SnapshotEntity((if (m.contentId.startsWith("sample-")) "sample-" else "") + newId(), m.versionId, m.contentId, r.dayEpoch!!, createdAt = now, updatedAt = now)
                            // Only overwrite metrics the file actually provides; keep the rest.
                            val merged = base.toValues().toMutableMap<Metric, Double?>().apply { putAll(r.values) }
                            base.withValues(merged).copy(note = r.note.ifBlank { base.note }, updatedAt = now)
                        }
                        env.repo.saveSnapshots(list)
                        env.toast("Imported ${list.size} snapshot${if (list.size == 1) "" else "s"}")
                        env.nav.pop()
                    }
                }) { Text("Import ${ready.size} snapshot${if (ready.size == 1) "" else "s"}") }
            }
        }
    }
}

@Composable
fun MetricsPickerDialog(sample: Boolean, onDismiss: () -> Unit) {
    val env = LocalEnv.current
    val d = LocalData.current
    val options = d.contents.filter { app.socmed.manager.data.isSampleId(it.id) == sample && !it.archived }.sortedByDescending { it.updatedAt }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Add metrics for…") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Metrics belong to a platform version of a post.", style = MaterialTheme.typography.bodySmall)
                options.take(80).forEach { c ->
                    val vs = d.versionsOf(c.id)
                    if (vs.isEmpty()) TextButton(onClick = { onDismiss(); env.nav.push("metrics/${c.id}") }, Modifier.fillMaxWidth()) { Text(c.title.ifBlank { "Untitled" }, Modifier.weight(1f), maxLines = 1) }
                    else vs.forEach { v -> TextButton(onClick = { onDismiss(); env.nav.push("metrics/${v.id}") }, Modifier.fillMaxWidth()) { Text("${c.title.ifBlank { "Untitled" }} · ${Platform.from(v.platform).label}", Modifier.weight(1f), maxLines = 1) } }
                }
                if (options.isEmpty()) Text("No posts yet. Create a post first.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
