@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.socmed.manager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.socmed.manager.data.AssetEntity
import app.socmed.manager.data.AssetRole
import app.socmed.manager.data.MediaFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ---------------------------------------------------------------- sharing (never marks anything Published)

fun copyText(ctx: Context, label: String, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
}

fun shareText(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    ctx.startActivity(Intent.createChooser(i, "Share caption").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shareAssets(ctx: Context, media: MediaFiles, assets: List<AssetEntity>, caption: String = "") {
    if (assets.isEmpty()) return
    val uris = ArrayList<Uri>(assets.map { media.uriFor(it.path) })
    val anyVideo = assets.any { it.isVideo }; val anyImage = assets.any { !it.isVideo }
    val i = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = if (anyVideo && anyImage) "*/*" else if (anyVideo) "video/*" else "image/*"
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris[0]) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Share media").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun openUrl(ctx: Context, url: String): Boolean {
    val u = if (url.contains("://")) url else "https://$url"
    return try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
}

// ---------------------------------------------------------------- pickers

/** Photo-picker + camera buttons. No storage permission is needed; a denied/missing camera just shows a message. */
@Composable
fun MediaPickerBar(ownerType: String, ownerId: String, role: String, allowVideo: Boolean = false, modifier: Modifier = Modifier) {
    val env = LocalEnv.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun attach(uris: List<Uri>) {
        if (uris.isEmpty()) return
        env.launch {
            val n = env.repo.attachUris(uris, ownerType, ownerId, role)
            env.toast(if (n == uris.size) "Added $n file${if (n == 1) "" else "s"}" else "Added $n of ${uris.size} — some files could not be read")
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { attach(it) }
    var camTarget by remember { mutableStateOf<Pair<File, Uri>?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val t = camTarget; camTarget = null
        if (ok && t != null) attach(listOf(t.second))
    }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = {
            picker.launch(PickVisualMediaRequest(if (allowVideo) ActivityResultContracts.PickVisualMedia.ImageAndVideo else ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) { Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp)); Text("  ${if (allowVideo) "Photos / video" else "Add photos"}") }
        OutlinedButton(onClick = {
            val t = env.repo.media.newCameraTarget(); camTarget = t
            try { camera.launch(t.second) } catch (e: Exception) { camTarget = null; env.toast("No camera app is available on this device.") }
        }) { Icon(Icons.Filled.AddAPhoto, null, Modifier.size(18.dp)); Text("  Take photo") }
    }
}

// ---------------------------------------------------------------- asset grid

@Composable
fun AssetGrid(
    ownerType: String, ownerId: String, role: String, modifier: Modifier = Modifier,
    ordered: Boolean = false, coverId: String? = null, onSetCover: ((String?) -> Unit)? = null, tile: Int = 96,
    roles: Set<String> = setOf(role),
) {
    val env = LocalEnv.current
    val d = LocalData.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val items = d.assetsOf(ownerType, ownerId).filter { it.role in roles }
    var viewing by remember { mutableStateOf<AssetEntity?>(null) }
    if (items.isEmpty()) {
        Text("Nothing here yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
        return
    }
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { idx, a ->
            var menu by remember { mutableStateOf(false) }
            Box(Modifier.size(tile.dp)) {
                Thumb(a, Modifier.fillMaxSize().clickable { viewing = a }, description = "Attachment ${idx + 1}")
                if (ordered) Surface(Modifier.align(Alignment.TopStart).padding(4.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary) {
                    Text("${idx + 1}", Modifier.padding(horizontal = 6.dp), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                }
                if (a.id == coverId) Surface(Modifier.align(Alignment.BottomStart).padding(4.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiary) {
                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onTertiary)
                        Text("Cover", color = MaterialTheme.colorScheme.onTertiary, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Box(Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.MoreVert, "Options for attachment ${idx + 1}", tint = Color.White, modifier = Modifier.background(Color(0x66000000), MaterialTheme.shapes.small))
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("View") }, onClick = { viewing = a; menu = false })
                        if (onSetCover != null) DropdownMenuItem(text = { Text(if (a.id == coverId) "Remove as cover" else "Use as cover") }, onClick = { onSetCover(if (a.id == coverId) null else a.id); menu = false })
                        if (ordered && idx > 0) DropdownMenuItem(text = { Text("Move earlier") }, onClick = { env.launch { env.repo.moveAsset(a, -1) }; menu = false })
                        if (ordered && idx < items.lastIndex) DropdownMenuItem(text = { Text("Move later") }, onClick = { env.launch { env.repo.moveAsset(a, 1) }; menu = false })
                        DropdownMenuItem(text = { Text("Share…") }, onClick = { shareAssets(ctx, env.repo.media, listOf(a)); menu = false })
                        DropdownMenuItem(text = { Text("Remove") }, onClick = {
                            menu = false
                            env.launch { env.repo.deleteAsset(a.id) }
                            env.undoable("Attachment removed") { env.repo.restoreAsset(a.id) }
                        })
                    }
                }
            }
        }
    }
    viewing?.let { a -> MediaViewer(a) { viewing = null } }
}

@Composable
fun MediaViewer(a: AssetEntity, onClose: () -> Unit) {
    val env = LocalEnv.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (a.isVideo) {
        // Videos open in the phone's own player; nothing is loaded into memory by the app.
        androidx.compose.runtime.LaunchedEffect(a.id) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(env.repo.media.uriFor(a.path), a.mime.ifBlank { "video/*" })
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) { env.toast("No video player available.") }
            onClose()
        }
        return
    }
    val bmp by produceState<Bitmap?>(null, a.id) { value = withContext(Dispatchers.IO) { env.repo.media.decodeForDisplay(a.path, 2048) } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            bmp?.let { Image(it.asImageBitmap(), "Full size image", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            IconButton(onClick = onClose, Modifier.align(Alignment.TopEnd).padding(12.dp)) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
        }
    }
}

/** Label + picker bar + grid in one block. */
@Composable
fun AssetSection(label: String, ownerType: String, ownerId: String, role: String, allowVideo: Boolean = false, ordered: Boolean = false, coverId: String? = null, onSetCover: ((String?) -> Unit)? = null, hint: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MediaPickerBar(ownerType, ownerId, role, allowVideo)
        AssetGrid(ownerType, ownerId, role, ordered = ordered, coverId = coverId, onSetCover = onSetCover)
    }
}
