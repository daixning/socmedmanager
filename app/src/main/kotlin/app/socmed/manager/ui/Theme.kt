package app.socmed.manager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import app.socmed.core.Platform
import app.socmed.core.Status

class Palette(val id: String, val name: String, val light: ColorScheme, val dark: ColorScheme, val swatch: List<Color>)

private val base = listOf(
    Triple("lavender", "Lavender", 0xFF6C4FB5 to 0xFFE7DFFB),
    Triple("mauve", "Mauve", 0xFF8E4A7D to 0xFFF2DCEB),
    Triple("pink", "Pink", 0xFFBE3A75 to 0xFFFBDDEB),
    Triple("peach", "Peach", 0xFFB05424 to 0xFFFFE0CC),
    Triple("mint", "Mint", 0xFF1D7557 to 0xFFD3F3E4),
    Triple("blue", "Soft blue", 0xFF2D63B0 to 0xFFD8E7FB),
)

private fun c(v: Long) = Color(v)

private fun build(i: Int): Palette {
    val (id, name, main) = base[i]
    val sec = base[(i + 2) % base.size].third
    val ter = base[(i + 4) % base.size].third
    val p = c(main.first); val pc = c(main.second)
    val s = c(sec.first); val sc = c(sec.second)
    val t = c(ter.first); val tc = c(ter.second)
    val ink = c(0xFF221B2E)
    val bgL = lerp(Color.White, pc, 0.22f)
    val light = lightColorScheme(
        primary = p, onPrimary = Color.White, primaryContainer = pc, onPrimaryContainer = lerp(p, Color.Black, 0.6f),
        secondary = s, onSecondary = Color.White, secondaryContainer = sc, onSecondaryContainer = lerp(s, Color.Black, 0.6f),
        tertiary = t, onTertiary = Color.White, tertiaryContainer = tc, onTertiaryContainer = lerp(t, Color.Black, 0.6f),
        background = bgL, onBackground = ink, surface = bgL, onSurface = ink,
        surfaceVariant = lerp(Color.White, pc, 0.55f), onSurfaceVariant = c(0xFF4A4256),
        outline = c(0xFF7B7189), outlineVariant = lerp(Color.White, pc, 0.9f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = lerp(Color.White, pc, 0.12f),
        surfaceContainer = lerp(Color.White, pc, 0.28f), surfaceContainerHigh = lerp(Color.White, pc, 0.42f), surfaceContainerHighest = lerp(Color.White, pc, 0.55f),
    )
    val bgD = lerp(c(0xFF14101B), p, 0.08f)
    val onD = c(0xFFEEE8F6)
    val dark = darkColorScheme(
        primary = lerp(pc, p, 0.15f), onPrimary = lerp(p, Color.Black, 0.7f), primaryContainer = lerp(p, Color.Black, 0.45f), onPrimaryContainer = pc,
        secondary = lerp(sc, s, 0.15f), onSecondary = lerp(s, Color.Black, 0.7f), secondaryContainer = lerp(s, Color.Black, 0.5f), onSecondaryContainer = sc,
        tertiary = lerp(tc, t, 0.15f), onTertiary = lerp(t, Color.Black, 0.7f), tertiaryContainer = lerp(t, Color.Black, 0.5f), onTertiaryContainer = tc,
        background = bgD, onBackground = onD, surface = bgD, onSurface = onD,
        surfaceVariant = lerp(bgD, p, 0.18f), onSurfaceVariant = c(0xFFCFC6DB),
        outline = c(0xFF988EA6), outlineVariant = lerp(bgD, p, 0.3f),
        surfaceContainerLowest = lerp(bgD, Color.Black, 0.3f), surfaceContainerLow = lerp(bgD, p, 0.08f),
        surfaceContainer = lerp(bgD, p, 0.14f), surfaceContainerHigh = lerp(bgD, p, 0.2f), surfaceContainerHighest = lerp(bgD, p, 0.26f),
    )
    return Palette(id, name, light, dark, listOf(p, s, t, pc))
}

val palettes: List<Palette> by lazy { base.indices.map { build(it) } }
fun paletteById(id: String) = palettes.firstOrNull { it.id == id } ?: palettes[0]

private val typography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SocMedTheme(paletteId: String, mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val p = paletteById(paletteId)
    MaterialTheme(colorScheme = if (dark) p.dark else p.light, typography = typography, shapes = shapes, content = content)
}

/** Status look: colour AND an icon AND the text label, so meaning never depends on colour alone. */
class StatusStyle(val icon: ImageVector, val bg: Color, val fg: Color)

private fun st(icon: ImageVector, bg: Long, fg: Long) = StatusStyle(icon, c(bg), c(fg))
private val lightStatus = mapOf(
    Status.IDEA to st(Icons.Filled.Lightbulb, 0xFFEADCFB, 0xFF3F2A66),
    Status.PLANNED to st(Icons.Filled.CalendarToday, 0xFFD9E8FB, 0xFF1E3F70),
    Status.IN_PRODUCTION to st(Icons.Filled.Build, 0xFFFFE2CC, 0xFF6B3410),
    Status.READY to st(Icons.Filled.Done, 0xFFD2F3E3, 0xFF0F5039),
    Status.PUBLISHED to st(Icons.Filled.CheckCircle, 0xFFB9EBD0, 0xFF0A3D2A),
    Status.ON_HOLD to st(Icons.Filled.Pause, 0xFFECE5EE, 0xFF4A4256),
    Status.CANCELLED to st(Icons.Filled.Cancel, 0xFFF8D9DF, 0xFF7A1F38),
)
private val customStatus = st(Icons.Filled.Label, 0xFFF3E1F3, 0xFF5A2F5A)

fun statusStyle(key: String, dark: Boolean): StatusStyle {
    val s = lightStatus[key] ?: customStatus
    return if (dark) StatusStyle(s.icon, s.fg, s.bg) else s
}

fun platformColor(p: Platform): Color = when (p) {
    Platform.INSTAGRAM -> c(0xFFC13584); Platform.TIKTOK -> c(0xFF1F1F2E); Platform.FACEBOOK -> c(0xFF1877F2)
    Platform.YOUTUBE -> c(0xFFD62828); Platform.X -> c(0xFF3A3A4A); Platform.LINKEDIN -> c(0xFF0A66C2)
    Platform.THREADS -> c(0xFF5B5B6B); Platform.PINTEREST -> c(0xFFC8232C); Platform.OTHER -> c(0xFF7B6A8F)
}

/** Soft accents used to colour pillars/categories consistently. */
val labelColors = listOf(0xFFE7DFFB, 0xFFFBDDEB, 0xFFFFE0CC, 0xFFD3F3E4, 0xFFD8E7FB, 0xFFF2DCEB, 0xFFFFF1C9).map { Color(it) }
fun labelColorFor(text: String): Color = labelColors[(text.lowercase().hashCode().and(0x7fffffff)) % labelColors.size]
