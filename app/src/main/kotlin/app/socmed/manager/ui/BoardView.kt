@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.socmed.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.socmed.core.Platform
import app.socmed.manager.data.ContentEntity
import app.socmed.manager.data.VersionEntity

/** One card on the board: a platform version (platform board) or a whole post (status board / posts without platforms). */
private data class BoardCard(val content: ContentEntity, val version: VersionEntity?, val status: String)

@Composable
fun BoardView(filter: ContentFilter) {
    var byPlatform by rememberSaveable { mutableStateOf(true) }
    val d = LocalData.current
    val contents = d.contents.filter { filter.matches(d, it) }
    Column(Modifier.fillMaxSize()) {
        SingleChips(listOf("p" to "By platform", "s" to "By status only"), if (byPlatform) "p" else "s", { if (it.isNotEmpty()) byPlatform = it == "p" }, Modifier.padding(horizontal = PagePadding, vertical = 4.dp), allowClear = false)
        if (contents.isEmpty()) { EmptyState("No posts to show. Add one from the New button."); return@Column }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (byPlatform) {
                Platform.entries.forEach { p ->
                    val cards = contents.flatMap { c -> d.versionsOf(c.id).filter { it.platform == p.name && (filter.platforms.isEmpty() || it.platform in filter.platforms) }.map { BoardCard(c, it, it.status) } }
                    if (cards.isNotEmpty()) item(key = p.name) { Lane(p.label, p, cards) }
                }
                val none = contents.filter { d.versionsOf(it.id).isEmpty() }.map { BoardCard(it, null, it.status) }
                if (none.isNotEmpty() && filter.platforms.isEmpty()) item(key = "none") { Lane("No platform chosen yet", null, none) }
            } else {
                item(key = "all") { Lane("All content", null, contents.map { BoardCard(it, null, it.status) }, showPlatformsOnCard = true) }
            }
        }
    }
}

@Composable
private fun Lane(title: String, platform: Platform?, cards: List<BoardCard>, showPlatformsOnCard: Boolean = false) {
    val d = LocalData.current
    val known = d.visibleStatuses.map { it.key }.toSet()
    val columns = d.visibleStatuses.map { it.key } + if (cards.any { it.status !in known }) listOf("__other") else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (platform != null) PlatformBadge(platform)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("${cards.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            columns.forEach { key ->
                val list = cards.filter { if (key == "__other") it.status !in known else it.status == key }
                Column(
                    Modifier.width(236.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (key == "__other") Text("Other", style = MaterialTheme.typography.labelLarge) else StatusChip(key)
                        Text("${list.size}", style = MaterialTheme.typography.labelMedium)
                    }
                    if (list.isEmpty()) Text("Empty", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(8.dp))
                    list.forEach { card -> BoardCardView(card, showPlatformsOnCard) }
                }
            }
        }
    }
}

@Composable
private fun BoardCardView(card: BoardCard, showPlatforms: Boolean) {
    val env = LocalEnv.current
    val d = LocalData.current
    val c = card.content
    SoftCard(Modifier.fillMaxWidth(), onClick = { env.nav.push("post/${c.id}") }) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ContentThumb(c, Modifier.size(56.dp), RoundedCornerShape(12.dp), card.version?.id)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(c.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    LabelChip(c.pillar)
                }
                MoveToMenu(card.status, { s ->
                    env.launch { if (card.version != null) env.repo.setVersionStatus(card.version.id, s) else env.repo.setContentStatus(c.id, s) }
                    env.toast("Moved “${c.title.ifBlank { "Untitled" }}” to ${d.statusLabel(s)}")
                })
            }
            if (showPlatforms) PlatformLabels(d.platformsOf(c))
            if (c.deadlineDay != null) Text("Deadline ${Fmt.day(c.deadlineDay)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VisualsFlag(show = c.visualsNeeded)
        }
    }
}
