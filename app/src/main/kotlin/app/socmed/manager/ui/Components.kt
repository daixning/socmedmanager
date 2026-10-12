@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.socmed.manager.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.socmed.core.Platform
import app.socmed.manager.data.AssetEntity
import app.socmed.manager.data.ContentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

val PagePadding = 16.dp
private val inkOnPastel = Color(0xFF2B2236)

// ---------------------------------------------------------------- screen frame

@Composable
fun DetailScaffold(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    val env = LocalEnv.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(env.snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { actions() },
            )
        },
        content = content,
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Filled.Brush) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- chips and labels

@Composable
fun StatusChip(key: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val d = LocalData.current
    val s = statusStyle(key, LocalDark.current)
    Surface(shape = RoundedCornerShape(50), color = s.bg, contentColor = s.fg, modifier = modifier) {
        Row(Modifier.padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 3.dp else 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(s.icon, null, Modifier.size(if (compact) 12.dp else 14.dp))
            Text(d.statusLabel(key), style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
fun PlatformBadge(p: Platform, modifier: Modifier = Modifier) {
    Box(modifier.size(22.dp).clip(CircleShape).background(platformColor(p)).semantics { contentDescription = p.label }, contentAlignment = Alignment.Center) {
        Text(p.short, color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
fun PlatformLabels(list: List<Platform>, modifier: Modifier = Modifier, withNames: Boolean = false) {
    if (list.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        list.forEach { p ->
            if (withNames) Row(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PlatformBadge(p); Text(p.label, style = MaterialTheme.typography.labelSmall)
            } else PlatformBadge(p)
        }
    }
}

@Composable
fun LabelChip(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Surface(shape = RoundedCornerShape(50), color = labelColorFor(text), contentColor = inkOnPastel, modifier = modifier) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun VisualsFlag(modifier: Modifier = Modifier, show: Boolean = true) {
    if (!show) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(Icons.Filled.Brush, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
        Text("Visuals needed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
fun OverdueFlag(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(Icons.Filled.Warning, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
        Text("Overdue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------- cards

@Composable
fun SoftCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border) { content() }
    else Card(modifier = modifier, shape = shape, colors = colors, border = border) { content() }
}

/** Expandable section so long forms stay manageable. Open/closed state survives rotation. */
@Composable
fun ExpandCard(title: String, modifier: Modifier = Modifier, summary: String = "", startExpanded: Boolean = false, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(startExpanded) }
    SoftCard(modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (summary.isNotBlank() && !open) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (open) "Collapse $title" else "Expand $title")
            }
            AnimatedVisibility(open) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

// ---------------------------------------------------------------- inputs

/** Text field that saves itself shortly after typing stops and once more when you leave the screen. */
@Composable
fun AutoText(
    label: String, value: String, onSave: (String) -> Unit, modifier: Modifier = Modifier,
    singleLine: Boolean = false, minLines: Int = 1, placeholder: String = "",
) {
    var text by remember { mutableStateOf(value) }
    var saved by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    val latest by androidx.compose.runtime.rememberUpdatedState(onSave)
    // Pick up changes made elsewhere (e.g. a suggestion chip) while this field is not being edited.
    LaunchedEffect(value) { if (!focused && value != text) { text = value; saved = value } }
    LaunchedEffect(text) {
        if (text != saved) { delay(450); saved = text; latest(text) }
    }
    DisposableEffect(Unit) { onDispose { if (text != saved) latest(text) } }
    OutlinedTextField(
        value = text, onValueChange = { text = it }, label = { Text(label) },
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        singleLine = singleLine, minLines = minLines, placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder) }) else null,
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
fun DatePickerField(label: String, epochDay: Long?, onPick: (Long?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.day(epochDay))
            }
        }
        if (epochDay != null) IconButton(onClick = { onPick(null) }) { Icon(Icons.Filled.Close, "Clear $label") }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (epochDay ?: LocalDate.now().toEpochDay()) * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPick(Math.floorDiv(it, 86_400_000L)) }; open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
fun DateTimeField(label: String, millis: Long?, zone: ZoneId, onPick: (Long?) -> Unit, modifier: Modifier = Modifier) {
    var step by remember { mutableStateOf(0) } // 0 closed, 1 date, 2 time
    var pickedDay by remember { mutableStateOf<LocalDate?>(null) }
    val current = millis?.let { java.time.Instant.ofEpochMilli(it).atZone(zone) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { step = 1 }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.dateTime(millis, zone))
            }
        }
        if (millis != null) IconButton(onClick = { onPick(null) }) { Icon(Icons.Filled.Close, "Clear $label") }
    }
    if (step == 1) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (current?.toLocalDate() ?: LocalDate.now(zone)).toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { step = 0 },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { pickedDay = LocalDate.ofEpochDay(Math.floorDiv(it, 86_400_000L)); step = 2 } ?: run { step = 0 } }) { Text("Next: time") } },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
    if (step == 2) {
        val t = remember { rememberTimeInit(current?.toLocalTime()) }
        val state = rememberTimePickerState(t.hour, t.minute, false)
        AlertDialog(
            onDismissRequest = { step = 0 },
            title = { Text("Time (${zone.id})") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    pickedDay?.let { onPick(app.socmed.core.Cal.atTime(it, LocalTime.of(state.hour, state.minute), zone)) }
                    step = 0
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Cancel") } },
        )
    }
}

private fun rememberTimeInit(t: LocalTime?): LocalTime = t ?: LocalTime.of(9, 0)

@Composable
fun SingleChips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, allowClear: Boolean = true) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (key, label) ->
            FilterChip(selected = key == selected, onClick = { onSelect(if (allowClear && key == selected) "" else key) }, label = { Text(label) },
                leadingIcon = if (key == selected) ({ Icon(Icons.Filled.Done, null, Modifier.size(16.dp)) }) else null)
        }
    }
}

@Composable
fun MultiChips(options: List<Pair<String, String>>, selected: Set<String>, onChange: (Set<String>) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (key, label) ->
            val on = key in selected
            FilterChip(selected = on, onClick = { onChange(if (on) selected - key else selected + key) }, label = { Text(label) },
                leadingIcon = if (on) ({ Icon(Icons.Filled.Done, null, Modifier.size(16.dp)) }) else null)
        }
    }
}

@Composable
fun DropdownField(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(options.firstOrNull { it.first == selected }?.second ?: "—", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { (k, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(k); open = false }) }
        }
    }
}

/** Accessible alternative to drag-and-drop: a "Move to" menu listing every status. */
@Composable
fun MoveToMenu(current: String, onMove: (String) -> Unit, modifier: Modifier = Modifier) {
    val d = LocalData.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.SwapHoriz, "Move to another status") }
        DropdownMenu(open, { open = false }) {
            Text("Move to", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
            d.visibleStatuses.forEach { s ->
                DropdownMenuItem(text = { StatusChip(s.key, compact = true) }, enabled = s.key != current, onClick = { onMove(s.key); open = false })
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PromptDialog(title: String, label: String, confirmLabel: String, initial: String = "", onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onDone(text.trim()); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------- images

private object BitmapCache {
    val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) { override fun sizeOf(key: String, value: Bitmap) = value.byteCount }
}

/** Loads a small thumbnail off the main thread and caches it; large originals are never decoded for list views. */
@Composable
fun Thumb(
    asset: AssetEntity?, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    cropX: Float = 0.5f, cropY: Float = 0.5f, zoom: Float = 1f, description: String? = null, showPlay: Boolean = true,
) {
    val env = LocalEnv.current
    val key = asset?.let { it.thumbPath.ifEmpty { it.path } }
    val bmp by produceState<Bitmap?>(initialValue = key?.let { BitmapCache.cache.get(it) }, key) {
        if (key == null) { value = null; return@produceState }
        val a = asset ?: return@produceState
        if (value == null) value = withContext(Dispatchers.IO) {
            val b = if (a.thumbPath.isNotEmpty()) android.graphics.BitmapFactory.decodeFile(env.repo.media.file(a.thumbPath).path)
                else if (!a.isVideo) env.repo.media.decodeForDisplay(a.path, 480) else null
            b?.also { BitmapCache.cache.put(key, it) }
        }
    }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(
                b.asImageBitmap(), description, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom },
                contentScale = ContentScale.Crop, alignment = BiasAlignment(cropX * 2 - 1, cropY * 2 - 1),
            )
        } else {
            Icon(if (asset?.isVideo == true) Icons.Filled.Movie else Icons.Filled.Image, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(28.dp))
        }
        if (showPlay && asset?.isVideo == true) Icon(Icons.Filled.PlayCircle, "Video", tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun ContentThumb(c: ContentEntity, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp), versionId: String? = null) {
    val d = LocalData.current
    val v = versionId?.let { d.versionById[it] }
    Thumb(d.coverOf(c, v), modifier, shape, c.cropX, c.cropY, c.cropZoom, description = "Cover for ${c.title}")
}

@Composable
fun Gap(h: Dp = 8.dp) = Spacer(Modifier.height(h))
@Composable
fun GapW(w: Dp = 8.dp) = Spacer(Modifier.width(w))

@Composable
fun AddButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(50)) {
        Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(text)
    }
}
