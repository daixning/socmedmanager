@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package app.socmed.manager.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.socmed.core.DateSwap
import app.socmed.core.FeedPlanner
import app.socmed.core.FeedTile
import app.socmed.core.Format
import app.socmed.core.Platform
import app.socmed.core.Status
import app.socmed.manager.data.AppData
import app.socmed.manager.data.AssetEntity
import app.socmed.manager.data.AssetRole
import app.socmed.manager.data.ContentEntity
import app.socmed.manager.data.FeedPostEntity
import app.socmed.manager.data.HighlightEntity
import app.socmed.manager.data.Prefs
import app.socmed.manager.data.newId

/** A tile in the mock feed. Planned/published Instagram versions come from content records; "ex:" tiles are manually added existing posts. */
private data class Tile(val key: String, val dateMillis: Long, val planned: Boolean, val pinned: Boolean, val content: ContentEntity?, val feed: FeedPostEntity?, val asset: AssetEntity?, val type: String)

private val RATIOS = listOf("1:1" to 1f, "4:5" to 0.8f, "3:4" to 0.75f, "9:16" to 0.5625f)

private fun buildTiles(d: AppData, pinned: Set<String>): List<Tile> {
    val out = ArrayList<Tile>()
    for (c in d.contents) {
        if (c.archived) continue
        val v = d.versionsOf(c.id).firstOrNull { it.platform == Platform.INSTAGRAM.name } ?: continue
        if (v.status == Status.CANCELLED || v.status == Status.ON_HOLD) continue
        val at = v.plannedAt ?: c.plannedAt ?: continue
        val fmt = v.format.ifBlank { c.format }
        out += Tile(c.id, v.publishedAt ?: at, v.status != Status.PUBLISHED, c.id in pinned, c, null, d.coverOf(c, v), fmt)
    }
    for (p in d.feedPosts) out += Tile("ex:${p.id}", p.postedAt, false, "ex:${p.id}" in pinned || p.pinned, null, p, d.assetsOf("feedpost", p.id, AssetRole.FEED).firstOrNull(), p.type)
    return out
}

@Composable
fun InstagramView() {
    val env = LocalEnv.current
    val d = LocalData.current
    val zone = d.zone
    var experiment by rememberSaveable { mutableStateOf(false) }
    var plannedOnly by rememberSaveable { mutableStateOf(false) }
    var cutoff by rememberSaveable { mutableStateOf<Long?>(null) }
    var options by remember { mutableStateOf<Tile?>(null) }
    var addPost by remember { mutableStateOf(false) }
    var editProfile by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf<List<DateSwap>?>(null) }
    val ratioKey = d.pref(Prefs.IG_RATIO, "4:5")
    val ratio = RATIOS.firstOrNull { it.first == ratioKey }?.second ?: 0.8f
    val pinnedSet = d.pref("ig_pinned").split(',').filter { it.isNotBlank() }.toSet()

    val tiles = buildTiles(d, pinnedSet)
    val cutoffMillis = cutoff?.let { app.socmed.core.Cal.atTime(java.time.LocalDate.ofEpochDay(it), java.time.LocalTime.MAX, zone) }
    val feedTiles = tiles.map { FeedTile(it.key, it.dateMillis, it.pinned, it.planned) }
    val scheduled = FeedPlanner.scheduleOrder(feedTiles, cutoffMillis, plannedOnly)
    val byKey = tiles.associateBy { it.key }

    // Layout experiment: planned (unpublished) tiles are re-ordered inside the slots they already occupy.
    val savedOrder = d.pref(Prefs.IG_EXPERIMENT).split(',').filter { it.isNotBlank() }
    val plannedKeysInSchedule = scheduled.filter { it.planned && !it.pinned }.map { it.key }
    val experimentOrder = FeedPlanner.reconcile(savedOrder, plannedKeysInSchedule)
    val displayKeys: List<String> = if (!experiment) scheduled.map { it.key } else {
        val q = experimentOrder.iterator()
        scheduled.map { t -> if (t.planned && !t.pinned && q.hasNext()) q.next() else t.key }
    }
    fun saveOrder(l: List<String>) = env.launch { env.repo.setPref(Prefs.IG_EXPERIMENT, l.joinToString(",")) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PagePadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Planning mockup — not Instagram and not connected to your account. It only shows how your saved plan could look.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProfileHeader(tiles.count { !it.planned } + 0, onEdit = { editProfile = true })
        HighlightsRow()

        ExpandCard("Preview settings", summary = (if (experiment) "Layout experiment" else "Schedule preview") + " · grid $ratioKey", startExpanded = false) {
            Text("Mode", style = MaterialTheme.typography.titleSmall)
            SingleChips(listOf("s" to "Schedule preview", "e" to "Layout experiment"), if (experiment) "e" else "s", { if (it.isNotEmpty()) experiment = it == "e" }, allowClear = false)
            Text(if (experiment) "Re-arrange planned tiles with the ◀ ▶ buttons. Publishing dates do NOT change unless you apply the order below." else "Feed is built from planned publishing dates, newest first.", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(plannedOnly, { plannedOnly = it }); Text("  Show planned posts only") }
            Text("Grid tile shape", style = MaterialTheme.typography.titleSmall)
            SingleChips(RATIOS.map { it.first to it.first }, ratioKey, { if (it.isNotEmpty()) env.launch { env.repo.setPref(Prefs.IG_RATIO, it) } }, allowClear = false)
            DatePickerField("Preview the profile after this date (hides later posts)", cutoff, { cutoff = it })
        }

        // ---------------------------------------------------------------- grid
        if (displayKeys.isEmpty()) EmptyState("The feed is empty. Add an Instagram platform to a post with a planned date, or add existing posts below.")
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            displayKeys.chunked(3).forEachIndexed { rowIdx, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEachIndexed { colIdx, key ->
                        val t = byKey[key] ?: return@forEachIndexed
                        val pos = rowIdx * 3 + colIdx
                        Box(Modifier.weight(1f)) {
                            TileView(t, ratio, zone, experiment && t.planned && !t.pinned,
                                onOpen = { t.content?.let { env.nav.push("post/${it.id}") } ?: run { options = t } },
                                onOptions = { options = t },
                                onEarlier = {
                                    val i = experimentOrder.indexOf(key); if (i > 0) saveOrder(experimentOrder.toMutableList().apply { add(i - 1, removeAt(i)) })
                                },
                                onLater = {
                                    val i = experimentOrder.indexOf(key); if (i in 0 until experimentOrder.lastIndex) saveOrder(experimentOrder.toMutableList().apply { add(i + 1, removeAt(i)) })
                                })
                        }
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
        Text("Tap a tile to open its post. Long-press to choose its cover, crop or pin. Icons: ▶ Reel, ▤ carousel, 📌 pinned.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (experiment) {
            val plannedTiles = scheduled.filter { it.planned && !it.pinned }
            val swaps = FeedPlanner.proposeSwaps(displayKeys.filter { k -> plannedTiles.any { it.key == k } }, plannedTiles)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { saveOrder(emptyList()) }, enabled = savedOrder.isNotEmpty()) { Text("Reset to schedule") }
                OutlinedButton(onClick = { review = swaps }, enabled = swaps.isNotEmpty()) { Text("Apply to plan… (${swaps.size})") }
            }
        }

        SectionTitle("Existing posts") { AddButton("Add existing post", { addPost = true }) }
        val ex = d.feedPosts.sortedByDescending { it.postedAt }
        if (ex.isEmpty()) Text("Upload posts you've already published so the mockup shows your real feed underneath the plan.", style = MaterialTheme.typography.bodySmall)
        ex.forEach { p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Thumb(d.assetsOf("feedpost", p.id, AssetRole.FEED).firstOrNull(), Modifier.size(44.dp), RoundedCornerShape(8.dp))
                Column(Modifier.weight(1f)) { Text(p.caption.ifBlank { "Existing post" }, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${Fmt.day(Days_epoch(p.postedAt, zone))} · ${Format.labelOf(p.type)}", style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { options = byKey["ex:${p.id}"] }) { Text("Edit") }
            }
        }
        Gap(80.dp)
    }

    if (addPost) AddExistingPostDialog { addPost = false }
    if (editProfile) EditProfileDialog { editProfile = false }
    options?.let { TileOptionsDialog(it, pinnedSet) { options = null } }
    review?.let { sw ->
        AlertDialog(
            onDismissRequest = { review = null }, title = { Text("Apply this order to your plan?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("These Instagram publishing dates would change. Captions, assets and other platforms are untouched.", style = MaterialTheme.typography.bodySmall)
                    sw.forEach { s ->
                        val c = d.contentById[s.key]
                        Text("• ${c?.title?.ifBlank { "Untitled" }}: ${Fmt.shortDateTime(s.oldMillis, zone)} → ${Fmt.shortDateTime(s.newMillis, zone)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { env.launch { env.repo.applyDateSwaps(sw, Platform.INSTAGRAM.name); env.repo.setPref(Prefs.IG_EXPERIMENT, "") }; env.toast("Publishing dates updated"); review = null }) { Text("Apply changes") } },
            dismissButton = { TextButton(onClick = { review = null }) { Text("Cancel") } },
        )
    }
}

private fun Days_epoch(millis: Long, zone: java.time.ZoneId): Long = app.socmed.core.Cal.localDate(millis, zone).toEpochDay()

@Composable
private fun TileView(t: Tile, ratio: Float, zone: java.time.ZoneId, arrows: Boolean, onOpen: () -> Unit, onOptions: () -> Unit, onEarlier: () -> Unit, onLater: () -> Unit) {
    val c = t.content
    val f = t.feed
    Box(
        Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(2.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onOptions, onClickLabel = if (c != null) "Open post" else "Edit existing post", onLongClickLabel = "Cover, crop and pin options"),
    ) {
        Thumb(t.asset, Modifier.fillMaxSize(), RoundedCornerShape(0.dp), c?.cropX ?: f?.cropX ?: 0.5f, c?.cropY ?: f?.cropY ?: 0.5f, c?.cropZoom ?: f?.cropZoom ?: 1f, showPlay = false)
        if (t.planned) Surface(Modifier.align(Alignment.BottomStart).padding(3.dp), color = Color(0xCC000000), shape = RoundedCornerShape(6.dp)) {
            Text("Planned", Modifier.padding(horizontal = 5.dp, vertical = 1.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.align(Alignment.TopEnd).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (t.pinned) Icon(Icons.Filled.PushPin, "Pinned", tint = Color.White, modifier = Modifier.size(16.dp).background(Color(0x88000000), CircleShape).padding(2.dp))
            when (Format.from(t.type)) {
                Format.REEL, Format.VIDEO -> Icon(Icons.Filled.PlayArrow, "Reel or video", tint = Color.White, modifier = Modifier.size(18.dp).background(Color(0x88000000), CircleShape).padding(1.dp))
                Format.CAROUSEL -> Icon(Icons.Filled.ViewCarousel, "Carousel", tint = Color.White, modifier = Modifier.size(18.dp).background(Color(0x88000000), CircleShape).padding(2.dp))
                else -> {}
            }
        }
        if (arrows) Row(Modifier.align(Alignment.BottomEnd)) {
            IconButton(onClick = onEarlier, Modifier.size(30.dp)) { Icon(Icons.Filled.ChevronLeft, "Move earlier in grid", tint = Color.White, modifier = Modifier.background(Color(0x99000000), CircleShape)) }
            IconButton(onClick = onLater, Modifier.size(30.dp)) { Icon(Icons.Filled.ChevronRight, "Move later in grid", tint = Color.White, modifier = Modifier.background(Color(0x99000000), CircleShape)) }
        }
    }
}

// ---------------------------------------------------------------- profile

@Composable
private fun ProfileHeader(existingCount: Int, onEdit: () -> Unit) {
    val env = LocalEnv.current
    val d = LocalData.current
    val avatar = d.assetsOf("profile", "ig", AssetRole.AVATAR).firstOrNull()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) env.launch {
            val m = env.repo.media.copyIn(uri) ?: return@launch
            d.assetsOf("profile", "ig", AssetRole.AVATAR).forEach { env.repo.deleteAsset(it.id) }
            env.repo.insertAsset(m, "profile", "ig", AssetRole.AVATAR, 0)
        }
    }
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(84.dp).clip(CircleShape).border(3.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(4.dp).clip(CircleShape).combinedClickable(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, onClickLabel = "Change profile photo")) {
                    Thumb(avatar, Modifier.fillMaxSize(), CircleShape)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Stat(d.pref(Prefs.IG_POSTS, existingCount.toString()), "posts")
                    Stat(d.pref(Prefs.IG_FOLLOWERS, "0"), "followers")
                    Stat(d.pref(Prefs.IG_FOLLOWING, "0"), "following")
                }
            }
            Text(d.pref(Prefs.IG_NAME, "Your Name"), fontWeight = FontWeight.Bold)
            Text("@" + d.pref(Prefs.IG_USER, "yourhandle"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(d.pref(Prefs.IG_BIO, "Tap Edit profile to write your bio."), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onEdit, Modifier.fillMaxWidth()) { Text("Edit profile & numbers") }
            Text("Numbers are typed by you; they are not fetched from Instagram.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Stat(value: String, label: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); Text(label, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun EditProfileDialog(onDismiss: () -> Unit) {
    val env = LocalEnv.current
    val d = LocalData.current
    var name by remember { mutableStateOf(d.pref(Prefs.IG_NAME)) }
    var user by remember { mutableStateOf(d.pref(Prefs.IG_USER)) }
    var bio by remember { mutableStateOf(d.pref(Prefs.IG_BIO)) }
    var posts by remember { mutableStateOf(d.pref(Prefs.IG_POSTS)) }
    var followers by remember { mutableStateOf(d.pref(Prefs.IG_FOLLOWERS)) }
    var following by remember { mutableStateOf(d.pref(Prefs.IG_FOLLOWING)) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Edit profile mockup") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Display name") }, singleLine = true)
                OutlinedTextField(user, { user = it.removePrefix("@") }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(bio, { bio = it }, label = { Text("Bio") }, minLines = 3)
                OutlinedTextField(posts, { posts = it.filter(Char::isDigit) }, label = { Text("Posts (blank = automatic)") }, singleLine = true)
                OutlinedTextField(followers, { followers = it.filter(Char::isDigit) }, label = { Text("Followers") }, singleLine = true)
                OutlinedTextField(following, { following = it.filter(Char::isDigit) }, label = { Text("Following") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                env.launch {
                    env.repo.setPref(Prefs.IG_NAME, name); env.repo.setPref(Prefs.IG_USER, user); env.repo.setPref(Prefs.IG_BIO, bio)
                    env.repo.setPref(Prefs.IG_POSTS, posts); env.repo.setPref(Prefs.IG_FOLLOWERS, followers); env.repo.setPref(Prefs.IG_FOLLOWING, following)
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HighlightsRow() {
    val env = LocalEnv.current
    val d = LocalData.current
    var edit by remember { mutableStateOf<HighlightEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().hScroll(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        d.highlights.sortedBy { it.position }.forEach { h ->
            Column(Modifier.combinedClickable(onClick = { edit = h }, onClickLabel = "Edit highlight ${h.title}"), horizontalAlignment = Alignment.CenterHorizontally) {
                Thumb(d.assetsOf("highlight", h.id).firstOrNull(), Modifier.size(62.dp).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(3.dp), CircleShape)
                Text(h.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.size(width = 62.dp, height = 18.dp), textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.combinedClickable(onClick = { creating = true }, onClickLabel = "Add highlight"), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(62.dp).border(2.dp, MaterialTheme.colorScheme.outline, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, null) }
            Text("New", style = MaterialTheme.typography.labelSmall)
        }
    }
    if (creating) PromptDialog("New highlight", "Title", "Create", onDone = { t -> env.launch { env.repo.saveHighlight(HighlightEntity(newId(), t, d.highlights.size, System.currentTimeMillis(), System.currentTimeMillis())) } }, onDismiss = { creating = false })
    edit?.let { h -> HighlightDialog(h) { edit = null } }
}

@Composable
private fun Modifier.hScroll(): Modifier = this.horizontalScroll(rememberScrollState())

@Composable
private fun HighlightDialog(h: HighlightEntity, onDismiss: () -> Unit) {
    val env = LocalEnv.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) env.launch {
            val m = env.repo.media.copyIn(uri) ?: return@launch
            env.repo.data.value.assetsOf("highlight", h.id).forEach { env.repo.deleteAsset(it.id) }
            env.repo.insertAsset(m, "highlight", h.id, AssetRole.HIGHLIGHT, 0)
        }
    }
    var title by remember { mutableStateOf(h.title) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Highlight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Choose cover photo") }
            }
        },
        confirmButton = { TextButton(onClick = { env.launch { env.repo.saveHighlight(h.copy(title = title.ifBlank { h.title })) }; onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { env.launch { env.repo.deleteHighlight(h.id) }; onDismiss() }) { Text("Delete") } },
    )
}

// ---------------------------------------------------------------- existing posts and tile options

@Composable
private fun AddExistingPostDialog(onDismiss: () -> Unit) {
    val env = LocalEnv.current
    val d = LocalData.current
    var uris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var type by remember { mutableStateOf("PHOTO") }
    var caption by remember { mutableStateOf("") }
    var day by remember { mutableStateOf<Long?>(java.time.LocalDate.now(d.zone).toEpochDay()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris = it }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Add an existing post") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) { Text(if (uris.isEmpty()) "Choose photos / video" else "${uris.size} selected — change") }
                SingleChips(listOf("PHOTO" to "Photo", "CAROUSEL" to "Carousel", "REEL" to "Reel"), type, { if (it.isNotEmpty()) type = it }, allowClear = false)
                OutlinedTextField(caption, { caption = it }, label = { Text("Caption (optional)") }, minLines = 2)
                DatePickerField("Posted on", day, { day = it })
                Text("The first image is the tile. Extra images are kept for carousels.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = uris.isNotEmpty(), onClick = {
                val postedAt = app.socmed.core.Cal.atTime(java.time.LocalDate.ofEpochDay(day ?: java.time.LocalDate.now(d.zone).toEpochDay()), java.time.LocalTime.NOON, d.zone)
                env.launch {
                    val t = System.currentTimeMillis()
                    val post = FeedPostEntity(newId(), caption, postedAt, type, false, createdAt = t, updatedAt = t)
                    env.repo.saveFeedPost(post)
                    env.repo.attachUris(uris, "feedpost", post.id, AssetRole.FEED)
                }
                onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TileOptionsDialog(t: Tile, pinned: Set<String>, onDismiss: () -> Unit) {
    val env = LocalEnv.current
    val d = LocalData.current
    val c = t.content
    val f = t.feed
    // Re-read so sliders reflect the stored values.
    val live = c?.let { d.contentById[it.id] }
    val liveFeed = f?.let { x -> d.feedPosts.firstOrNull { it.id == x.id } }
    var x by remember { mutableStateOf(live?.cropX ?: liveFeed?.cropX ?: 0.5f) }
    var y by remember { mutableStateOf(live?.cropY ?: liveFeed?.cropY ?: 0.5f) }
    var z by remember { mutableStateOf(live?.cropZoom ?: liveFeed?.cropZoom ?: 1f) }
    fun save() = env.launch {
        if (c != null) env.repo.update(c.id) { it.copy(cropX = x, cropY = y, cropZoom = z) }
        else if (f != null) env.repo.data.value.feedPosts.firstOrNull { it.id == f.id }?.let { env.repo.saveFeedPost(it.copy(cropX = x, cropY = y, cropZoom = z)) }
    }
    AlertDialog(
        onDismissRequest = { save(); onDismiss() },
        title = { Text(c?.title?.ifBlank { "Untitled" } ?: f?.caption?.take(30)?.ifBlank { "Existing post" } ?: "Tile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Thumb(t.asset, Modifier.fillMaxWidth(0.5f).aspectRatio(0.8f), RoundedCornerShape(8.dp), x, y, z, showPlay = false)
                Text("Crop: slide to move the visible area", style = MaterialTheme.typography.labelLarge)
                Text("Horizontal", style = MaterialTheme.typography.bodySmall); Slider(x, { x = it }, onValueChangeFinished = { save() })
                Text("Vertical", style = MaterialTheme.typography.bodySmall); Slider(y, { y = it }, onValueChangeFinished = { save() })
                Text("Zoom", style = MaterialTheme.typography.bodySmall); Slider(z, { z = it }, valueRange = 1f..2.5f, onValueChangeFinished = { save() })
                if (c != null) {
                    val imgs = d.assetsOf("content", c.id).filter { !it.isVideo } + d.versionsOf(c.id).flatMap { d.assetsOf("version", it.id) }.filter { !it.isVideo }
                    if (imgs.isNotEmpty()) {
                        Text("Cover (carousel slide / Reel cover)", style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.hScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            imgs.forEach { a -> Thumb(a, Modifier.size(64.dp).combinedClickable(onClick = { env.launch { env.repo.setCover(c.id, a.id) } }, onClickLabel = "Use as cover").let { m -> if (a.id == (live?.coverAssetId ?: t.asset?.id)) m.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else m }) }
                        }
                    }
                }
                val key = t.key
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(key in pinned, { on ->
                        val next = if (on) pinned + key else pinned - key
                        env.launch { env.repo.setPref("ig_pinned", next.joinToString(",")) }
                    }); Text("  Pin to top of profile")
                }
                if (c != null) OutlinedButton(onClick = { save(); onDismiss(); env.nav.push("post/${c.id}") }) { Text("Open post record") }
                if (f != null) TextButton(onClick = { env.launch { env.repo.deleteFeedPost(f.id) }; onDismiss() }) { Text("Delete this existing post") }
            }
        },
        confirmButton = { TextButton(onClick = { save(); onDismiss() }) { Text("Done") } },
    )
}
