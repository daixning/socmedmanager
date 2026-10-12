package app.socmed.manager.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.listSaver
import app.socmed.core.Platform
import app.socmed.manager.SocMedApp
import app.socmed.manager.data.AppData
import app.socmed.manager.data.Backup
import app.socmed.manager.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Back stack of detail screens shown above the main tabs. Routes: post/<id>, event/<id>, metrics/<versionId>, csvimport. */
class Nav(initial: List<String> = emptyList()) {
    val stack = mutableStateListOf<String>().apply { addAll(initial) }
    fun push(route: String) { if (stack.lastOrNull() != route) stack.add(route) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    val top: String? get() = stack.lastOrNull()
    companion object { val Saver = listSaver<Nav, String>(save = { it.stack.toList() }, restore = { Nav(it) }) }
}

class AppEnv(val app: SocMedApp, val nav: Nav, val snackbar: SnackbarHostState) {
    val repo: Repository get() = app.repo
    val backup: Backup get() = app.backup
    /** Application-wide scope: work started here is not cancelled when a screen closes (e.g. a final autosave). */
    val scope: CoroutineScope get() = app.scope

    fun launch(block: suspend CoroutineScope.() -> Unit) = scope.launch(block = block)

    fun toast(message: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(message) } }

    /** Shows a message with Undo. Used for every delete so nothing is lost by a mis-tap. */
    fun undoable(message: String, undo: suspend () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) undo()
        }
    }
}

val LocalEnv = staticCompositionLocalOf<AppEnv> { error("AppEnv not provided") }
val LocalData = compositionLocalOf { AppData() }
val LocalDark = compositionLocalOf { false }

object Fmt {
    private val locale get() = Locale.getDefault()
    fun day(epochDay: Long?): String = epochDay?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) } ?: "Not set"
    fun shortDay(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
    fun dateTime(millis: Long?, zone: ZoneId): String = millis?.let {
        Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
    } ?: "Not set"
    fun shortDateTime(millis: Long?, zone: ZoneId): String = millis?.let {
        Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern("MMM d · h:mm a", locale))
    } ?: "No date"
    fun time(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a", locale))
    fun month(y: Int, m: Int): String = java.time.YearMonth.of(y, m).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    fun num(d: Double?): String = when {
        d == null -> "—"
        d == Math.rint(d) && kotlin.math.abs(d) < 1e12 -> String.format(locale, "%,d", d.toLong())
        else -> String.format(locale, "%,.1f", d)
    }
    fun pct(d: Double?): String = d?.let { String.format(locale, "%.1f%%", it) } ?: "—"
    fun bytes(b: Long): String = when {
        b < 1024 -> "$b B"; b < 1024 * 1024 -> "${b / 1024} KB"
        b < 1024L * 1024 * 1024 -> String.format(locale, "%.1f MB", b / 1048576.0)
        else -> String.format(locale, "%.2f GB", b / 1073741824.0)
    }
    fun platforms(list: List<Platform>) = list.joinToString(" · ") { it.label }
}

@Composable
fun rememberNav(initial: List<String> = emptyList()): Nav =
    androidx.compose.runtime.saveable.rememberSaveable(saver = Nav.Saver) { Nav(initial) }
