@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.socmed.manager.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.socmed.manager.data.Backup
import app.socmed.manager.data.CsvExport
import app.socmed.manager.data.Prefs
import app.socmed.manager.data.RestoreMode
import app.socmed.manager.data.RestorePreview
import app.socmed.manager.data.StatusDefEntity
import app.socmed.manager.data.StorageUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

private val COMMON_ZONES = listOf(
    "Asia/Manila", "UTC", "Asia/Singapore", "Asia/Hong_Kong", "Asia/Tokyo", "Asia/Seoul", "Asia/Dubai", "Asia/Kolkata", "Australia/Sydney", "Pacific/Auckland",
    "Europe/London", "Europe/Paris", "Europe/Berlin", "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles", "America/Toronto", "America/Sao_Paulo",
)

@Composable
fun SettingsScreen() {
    val env = LocalEnv.current
    val d = LocalData.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var includeSampleInExports by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf<StorageUsage?>(null) }
    var preview by remember { mutableStateOf<RestorePreview?>(null) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var confirmRemoveSample by remember { mutableStateOf(false) }
    var customZone by remember { mutableStateOf("") }
    var newStatus by remember { mutableStateOf("") }
    LaunchedEffect(d.contents.size, d.assets.size) { usage = withContext(Dispatchers.IO) { env.repo.media.usage(env.repo.dbFile) } }

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) env.launch {
            busy = "Writing backup…"
            try { val m = env.backup.export(uri); env.toast("Backup saved: ${m.counts.values.sum()} records, ${m.mediaFiles} media files") }
            catch (e: Exception) { env.toast("Backup failed: ${e.message}") }
            busy = null
        }
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) env.launch {
            busy = "Checking backup…"; restoreError = null
            try { preview = env.backup.inspect(uri) } catch (e: Exception) { restoreError = e.message ?: "Could not read the backup." }
            busy = null
        }
    }
    val csvContent = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) env.launch { writeText(env, uri, CsvExport.content(env.repo.data.value, includeSampleInExports)) }
    }
    val csvAnalytics = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) env.launch { writeText(env, uri, CsvExport.analytics(env.repo.data.value, includeSampleInExports)) }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        env.launch { env.repo.setPrefBool(Prefs.NOTIFS, true) }
        if (!granted) env.toast("Notifications are blocked in system settings. Reminders are saved, but Android won't show them until you allow notifications for this app.")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        busy?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        ExpandCard("Appearance", summary = paletteById(d.pref(Prefs.PALETTE, "lavender")).name + " · " + d.pref(Prefs.DARK, "system"), startExpanded = true) {
            Text("Colour palette", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                palettes.forEach { p ->
                    val sel = d.pref(Prefs.PALETTE, "lavender") == p.id
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .clickable { env.launch { env.repo.setPref(Prefs.PALETTE, p.id) } }.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { p.swatch.forEach { Row(Modifier.size(18.dp).clip(CircleShape).background(it)) {} } }
                        Text(p.name + if (sel) " ✓" else "", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Text("Mode", style = MaterialTheme.typography.titleSmall)
            SingleChips(listOf("system" to "Follow system", "light" to "Light", "dark" to "Dark"), d.pref(Prefs.DARK, "system"), { if (it.isNotEmpty()) env.launch { env.repo.setPref(Prefs.DARK, it) } }, allowClear = false)
        }

        ExpandCard("Time zone", summary = d.zone.id) {
            Text("Dates and times are shown in this zone. Stored times are exact moments, so changing the zone never corrupts anything.", style = MaterialTheme.typography.bodySmall)
            DropdownField("Time zone", (COMMON_ZONES + d.zone.id).distinct().map { it to it }, d.zone.id, { env.launch { env.repo.setPref(Prefs.TZ, it) } })
            OutlinedTextField(customZone, { customZone = it }, label = { Text("Or type any zone ID, e.g. Europe/Madrid") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(enabled = customZone.isNotBlank(), onClick = {
                try { ZoneId.of(customZone.trim()); env.launch { env.repo.setPref(Prefs.TZ, customZone.trim()) }; customZone = "" } catch (e: Exception) { env.toast("Unknown time zone") }
            }) { Text("Use this zone") }
        }

        ExpandCard("Production statuses", summary = d.visibleStatuses.joinToString(" → ") { it.label }) {
            Text("Rename the statuses to suit your workflow. Hidden statuses disappear from pickers and boards but existing posts keep them.", style = MaterialTheme.typography.bodySmall)
            d.statuses.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { AutoText("Status ${i + 1}", s.label, { v -> if (v.isNotBlank()) env.launch { env.repo.saveStatus(s.copy(label = v.trim())) } }, singleLine = true) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Show", style = MaterialTheme.typography.labelSmall); Switch(!s.hidden, { on -> env.launch { env.repo.saveStatus(s.copy(hidden = !on)) } }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newStatus, { newStatus = it }, label = { Text("Add custom status") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedButton(enabled = newStatus.isNotBlank(), onClick = {
                    env.launch { env.repo.saveStatus(StatusDefEntity("custom_" + System.currentTimeMillis(), newStatus.trim(), d.statuses.size, false, 0)) }; newStatus = ""
                }) { Text("Add") }
            }
        }

        ExpandCard("Reminders", summary = if (d.prefBool(Prefs.NOTIFS)) "On" else "Off") {
            Text("Optional local notifications for publishing times and preparation deadlines. A reminder never marks anything as published.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(d.prefBool(Prefs.NOTIFS), { on ->
                    if (!on) env.launch { env.repo.setPrefBool(Prefs.NOTIFS, false) }
                    else if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else env.launch { env.repo.setPrefBool(Prefs.NOTIFS, true) }
                }); Text("  Enable reminders")
            }
            Text("Remind me before a planned publishing time", style = MaterialTheme.typography.titleSmall)
            SingleChips(listOf("0" to "At the time", "15" to "15 min", "60" to "1 hour", "180" to "3 hours", "1440" to "1 day"), d.pref(Prefs.LEAD_MIN, "60"), { if (it.isNotEmpty()) env.launch { env.repo.setPref(Prefs.LEAD_MIN, it) } }, allowClear = false)
            Text("Android may delay reminders slightly to save battery. If you deny the notification permission the app keeps working normally.", style = MaterialTheme.typography.bodySmall)
        }

        ExpandCard("Backup & restore", summary = d.pref(Prefs.BACKUP_LAST).toLongOrNull()?.let { "Last backup ${Fmt.dateTime(it, d.zone)}" } ?: "No backup yet", startExpanded = true) {
            Text("A full backup is one .zip file with all records, statuses, settings and every photo/video. CSV files are NOT a backup. Keep backups somewhere off the phone (Drive, a computer, a USB drive).", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { exportBackup.launch("socmed-backup-${LocalDate.now(d.zone)}.zip") }) { Text("Export full backup") }
            OutlinedButton(onClick = { restorePicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) }) { Text("Restore from backup…") }
            restoreError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Restore checks the file first and shows what would change. “Merge” keeps everything you have and adds/updates by record ID so nothing is duplicated.", style = MaterialTheme.typography.bodySmall)
        }

        ExpandCard("CSV export", summary = "Spreadsheets only") {
            Text("For use in a spreadsheet. The analytics CSV can be re-imported on the Analytics tab.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(includeSampleInExports, { includeSampleInExports = it }); Text("  Include sample data") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { csvContent.launch("socmed-content-${LocalDate.now(d.zone)}.csv") }) { Text("Content CSV") }
                OutlinedButton(onClick = { csvAnalytics.launch("socmed-analytics-${LocalDate.now(d.zone)}.csv") }) { Text("Analytics CSV") }
            }
        }

        ExpandCard("Storage", summary = usage?.let { Fmt.bytes(it.mediaBytes + it.thumbBytes + it.dbBytes) + " used" } ?: "Calculating…") {
            usage?.let { u ->
                Text("Media originals: ${Fmt.bytes(u.mediaBytes)} (${u.mediaFiles} files)")
                Text("Thumbnails: ${Fmt.bytes(u.thumbBytes)}")
                Text("Database: ${Fmt.bytes(u.dbBytes)}")
            }
            Text("Everything lives in the app's private storage on this phone. Imported media is copied, so deleting the original from your gallery is safe. Uninstalling the app deletes this data — export a backup first.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { env.launch { val freed = env.repo.purgeDeleted(); env.toast("Removed deleted items permanently (freed ${Fmt.bytes(freed)})") } }) { Text("Permanently remove deleted items") }
        }

        ExpandCard("Sample data", summary = if (d.hasSample) "Loaded" else "Not loaded") {
            Text("Optional demo posts, events and metrics so you can explore. They are marked SAMPLE, kept out of your real analytics, and removed with one tap.", style = MaterialTheme.typography.bodySmall)
            if (!d.hasSample) OutlinedButton(onClick = { env.launch { env.repo.loadSample(); env.toast("Sample data loaded") } }) { Text("Load sample data") }
            else OutlinedButton(onClick = { confirmRemoveSample = true }) { Text("Remove all sample data") }
        }

        ExpandCard("What works now · what is planned", startExpanded = false) { FeatureStatus() }
        ExpandCard("About & updates") { About() }
        Gap(24.dp)
    }

    preview?.let { p -> RestoreDialog(p, onDismiss = { preview = null; java.io.File(ctx.cacheDir, "restore").deleteRecursively() }) { mode ->
        env.launch {
            busy = "Restoring…"
            try { val r = env.backup.apply(p, mode); env.toast("Restored: ${r.added} added, ${r.updated} updated, ${r.skipped} unchanged") }
            catch (e: Exception) { env.toast("Restore failed: ${e.message}") }
            busy = null
        }
        preview = null
    } }
    if (confirmRemoveSample) ConfirmDialog("Remove sample data?", "Only items marked sample are deleted. Your own posts, events and metrics stay.", "Remove", { env.launch { env.repo.removeSample(); env.toast("Sample data removed") } }, { confirmRemoveSample = false })
}

private suspend fun writeText(env: AppEnv, uri: android.net.Uri, text: String) = withContext(Dispatchers.IO) {
    try {
        env.app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(("﻿" + text).toByteArray(Charsets.UTF_8)) }
        env.toast("CSV saved")
    } catch (e: Exception) { env.toast("Could not save: ${e.message}") }
}

@Composable
private fun RestoreDialog(p: RestorePreview, onDismiss: () -> Unit, onApply: (RestoreMode) -> Unit) {
    val d = LocalData.current
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Restore this backup?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Made ${Fmt.dateTime(p.manifest.createdAt, d.zone)} · app ${p.manifest.appVersion} · ${p.manifest.mediaFiles} media files", style = MaterialTheme.typography.bodySmall)
                Text("✓ File structure and database verified", style = MaterialTheme.typography.bodyMedium)
                if (p.missingMedia.isNotEmpty()) Text("⚠ ${p.missingMedia.size} media file(s) referenced by records are missing from the backup. Those records will restore without their picture.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text("Compared with what is on this phone:", style = MaterialTheme.typography.titleSmall)
                Text("• ${p.newRecords} records would be added\n• ${p.updatedRecords} records are newer in the backup and would be updated\n• ${p.unchangedRecords} are already up to date", style = MaterialTheme.typography.bodyMedium)
                SingleChips(listOf("MERGE" to "Merge (keep my current data)", "REPLACE" to "Replace everything"), mode.name, { if (it.isNotEmpty()) mode = RestoreMode.valueOf(it) }, allowClear = false)
                Text(if (mode == RestoreMode.MERGE) "Nothing is deleted. Records with the same ID are never duplicated; the most recently edited copy wins." else "Everything currently in the app is replaced by the backup. Export a backup first if you might want the current data.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onApply(mode) }) { Text(if (mode == RestoreMode.MERGE) "Merge" else "Replace") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FeatureStatus() {
    Text("Working in this version (all offline)", style = MaterialTheme.typography.titleSmall)
    Text(
        "• Posts with every field, platform versions, checklists, reminders\n• All Posts table/list, Calendar (month/week/agenda), Visual Plan, Platform & Status board, Visuals Needed, Ideas & References, Instagram mockup — all views of the same records\n• Events with relative deadlines, date-shift review and post-event review\n• Manual metrics, CSV import, rule-based insights, formulas shown\n• Photo picker, camera, local media copies and thumbnails\n• Full backup/restore (zip), CSV export, sample data",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text("Future integrations (not built, not simulated)", style = MaterialTheme.typography.titleSmall)
    Text(
        "• Google sign-in (identity only) — needs a Google Cloud OAuth client\n• Account-linked backup and cross-device sync with conflict handling — needs a backend or Drive/Firebase storage and a privacy review; the data model already has stable IDs, modified times and deletion markers for it\n• Instagram/Facebook/TikTok account connection, automatic analytics, automatic publishing — each needs the platform's official API, app review and permissions\n• Drag-and-drop reordering (use Move to / arrows / date picker today)\n• Link previews",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun About() {
    Text("SocMed Manager 1.0.0 · application ID app.socmed.manager", style = MaterialTheme.typography.bodyMedium)
    Text(
        "Updating: install a newer APK over this one. Your data stays because the application ID and signing key are the same, and database changes are done with migrations. An APK signed with a different key cannot be installed over this one (Android refuses) — keep your signing key safe. Always export a backup before updating.",
        style = MaterialTheme.typography.bodySmall,
    )
}
