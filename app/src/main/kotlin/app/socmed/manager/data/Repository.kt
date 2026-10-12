package app.socmed.manager.data

import android.content.Context
import androidx.room.withTransaction
import app.socmed.core.ShiftProposal
import app.socmed.core.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
private fun now() = System.currentTimeMillis()

/**
 * The single place that writes records. All screens read [data] and call these functions, so a change made in any
 * view is the same change everywhere. Identity is never duplicated: views are just different projections.
 */
class Repository(val context: Context, val db: AppDatabase, val media: MediaFiles, private val scope: CoroutineScope) {
    private val dao = db.dao()

    val dbFile: File get() = context.getDatabasePath(AppDatabase.FILE_NAME)

    @Suppress("UNCHECKED_CAST")
    val data: StateFlow<AppData> = combine(
        listOf(
            dao.contents(), dao.versions(), dao.assets(), dao.links(), dao.tasks(), dao.events(), dao.snapshots(),
            dao.followerLogs(), dao.feedPosts(), dao.highlights(), dao.statusDefs(), dao.prefs(),
        )
    ) { a ->
        AppData(
            contents = a[0] as List<ContentEntity>, allVersions = a[1] as List<VersionEntity>, assets = a[2] as List<AssetEntity>,
            links = a[3] as List<LinkEntity>, tasks = a[4] as List<TaskEntity>, events = a[5] as List<EventEntity>,
            snapshots = a[6] as List<SnapshotEntity>, followerLogs = a[7] as List<FollowerLogEntity>,
            feedPosts = a[8] as List<FeedPostEntity>, highlights = a[9] as List<HighlightEntity>,
            statusDefs = a[10] as List<StatusDefEntity>, prefs = (a[11] as List<PrefEntity>).associate { it.key to it.value },
            loaded = true,
        )
    }.stateIn(scope, SharingStarted.Eagerly, AppData())

    suspend fun ensureDefaults() {
        if (dao.statusCount() == 0) dao.upsertStatuses(Status.defaults.mapIndexed { i, (k, l) -> StatusDefEntity(k, l, i, false, now()) })
    }

    // ---------- preferences ----------
    suspend fun setPref(key: String, value: String) = dao.upsert(PrefEntity(key, value, now()))
    suspend fun setPrefBool(key: String, value: Boolean) = setPref(key, if (value) "1" else "0")

    // ---------- content ----------
    suspend fun createContent(title: String, status: String = Status.IDEA, collection: String = "", eventId: String? = null, plannedAt: Long? = null): ContentEntity {
        val t = now()
        val c = ContentEntity(newId(), title = title.trim(), status = status, collection = collection, eventId = eventId, plannedAt = plannedAt, createdAt = t, updatedAt = t)
        dao.upsert(c)
        return c
    }

    suspend fun save(c: ContentEntity) = dao.upsert(c.copy(updatedAt = now()))

    /** Latest stored copy, so field-by-field edits never overwrite each other with stale state. */
    suspend fun update(id: String, f: (ContentEntity) -> ContentEntity) {
        val c = dao.contentById(id) ?: return
        dao.upsert(f(c).copy(updatedAt = now()))
    }

    suspend fun setContentStatus(id: String, status: String) = db.withTransaction {
        val c = dao.contentById(id) ?: return@withTransaction
        val t = now()
        val versions = dao.versionsOf(id).filter { it.deletedAt == null }
        val publishedAt = if (status == Status.PUBLISHED) c.publishedAt ?: t else c.publishedAt
        dao.upsert(c.copy(status = status, publishedAt = publishedAt, updatedAt = t))
        if (versions.size == 1) {
            val v = versions[0]
            dao.upsertVersion(v.copy(status = status, publishedAt = if (status == Status.PUBLISHED) v.publishedAt ?: t else v.publishedAt, updatedAt = t))
        }
    }

    suspend fun setVersionStatus(versionId: String, status: String) = db.withTransaction {
        val v = data.value.versionById[versionId] ?: return@withTransaction
        val t = now()
        dao.upsertVersion(v.copy(status = status, publishedAt = if (status == Status.PUBLISHED) v.publishedAt ?: t else v.publishedAt, updatedAt = t))
        syncParent(v.contentId)
    }

    suspend fun saveVersion(v: VersionEntity) = db.withTransaction {
        dao.upsertVersion(v.copy(updatedAt = now()))
        syncParent(v.contentId)
    }

    /** One version: parent mirrors it. Several: parent becomes Published once every non-cancelled version is. */
    private suspend fun syncParent(contentId: String) {
        val c = dao.contentById(contentId) ?: return
        val vs = dao.versionsOf(contentId).filter { it.deletedAt == null }
        val t = now()
        if (vs.size == 1) {
            val v = vs[0]
            if (c.status != v.status || c.publishedAt != v.publishedAt)
                dao.upsert(c.copy(status = v.status, publishedAt = v.publishedAt ?: c.publishedAt, updatedAt = t))
        } else if (vs.isNotEmpty()) {
            val live = vs.filter { it.status != Status.CANCELLED }
            if (live.isNotEmpty() && live.all { it.status == Status.PUBLISHED } && c.status != Status.PUBLISHED)
                dao.upsert(c.copy(status = Status.PUBLISHED, publishedAt = live.mapNotNull { it.publishedAt }.maxOrNull() ?: t, updatedAt = t))
        }
    }

    suspend fun addPlatform(contentId: String, platform: String): VersionEntity? {
        val c = dao.contentById(contentId) ?: return null
        dao.versionsOf(contentId).firstOrNull { it.platform == platform }?.let {
            if (it.deletedAt != null) { val r = it.copy(deletedAt = null, updatedAt = now()); dao.upsertVersion(r); return r }
            return it
        }
        val t = now()
        val v = VersionEntity(newId(), contentId, platform, status = c.status, plannedAt = null, createdAt = t, updatedAt = t)
        dao.upsertVersion(v)
        return v
    }

    suspend fun removePlatform(versionId: String) {
        softDelete("version", listOf(versionId))
    }

    suspend fun archive(id: String, archived: Boolean) = update(id) { it.copy(archived = archived) }

    suspend fun softDeleteContent(id: String) = softDelete("content", listOf(id))
    suspend fun restoreContent(id: String) = restore("content", listOf(id))

    suspend fun duplicateContent(id: String): ContentEntity? = db.withTransaction {
        val c = dao.contentById(id) ?: return@withTransaction null
        val d = data.value
        val t = now()
        val copy = c.copy(id = newId(), title = c.title.ifBlank { "Untitled" } + " (copy)", status = if (c.status == Status.PUBLISHED) Status.PLANNED else c.status,
            publishedAt = null, publishedUrl = "", plannedAt = null, coverAssetId = null, archived = false, createdAt = t, updatedAt = t)
        dao.upsert(copy)
        val versionMap = HashMap<String, String>()
        d.versionsOf(id).forEach { v ->
            val nv = v.copy(id = newId(), contentId = copy.id, plannedAt = null, publishedAt = null, url = "", coverAssetId = null,
                status = if (v.status == Status.PUBLISHED) Status.PLANNED else v.status, createdAt = t, updatedAt = t)
            versionMap[v.id] = nv.id; dao.upsertVersion(nv)
        }
        val assetMap = HashMap<String, String>()
        fun copyAssets(ownerType: String, oldOwner: String, newOwner: String) {
            d.assetsOf(ownerType, oldOwner).forEach { a ->
                val na = a.copy(id = newId(), ownerId = newOwner, createdAt = t, updatedAt = t)
                assetMap[a.id] = na.id
                val ext = a.path.substringAfterLast('.', "bin")
                val rel = "media/${na.id}.$ext"
                runCatching { media.file(a.path).copyTo(media.file(rel), overwrite = true) }
                val thumbRel = if (a.thumbPath.isNotEmpty()) "thumbs/${na.id}.jpg" else ""
                if (thumbRel.isNotEmpty()) runCatching { media.file(a.thumbPath).copyTo(media.file(thumbRel), overwrite = true) }
                pendingAssets += na.copy(path = rel, thumbPath = thumbRel)
            }
        }
        pendingAssets.clear()
        copyAssets("content", id, copy.id)
        versionMap.forEach { (o, n) -> copyAssets("version", o, n) }
        if (pendingAssets.isNotEmpty()) dao.upsertAssets(pendingAssets.toList())
        c.coverAssetId?.let { assetMap[it] }?.let { dao.upsert(copy.copy(coverAssetId = it)) }
        dao.upsertLinks(d.linksOf("content", id).map { it.copy(id = newId(), ownerId = copy.id, createdAt = t, updatedAt = t) })
        dao.upsertTasks(d.tasksOf("content", id).map { it.copy(id = newId(), ownerId = copy.id, done = false, createdAt = t, updatedAt = t) })
        copy
    }
    private val pendingAssets = ArrayList<AssetEntity>()

    // ---------- tombstones ----------
    private suspend fun softDelete(table: String, ids: List<String>) = setDeleted(table, ids, now())
    private suspend fun restore(table: String, ids: List<String>) = setDeleted(table, ids, null)
    private suspend fun setDeleted(table: String, ids: List<String>, at: Long?) = db.withTransaction {
        val w = db.openHelper.writableDatabase
        for (id in ids) w.execSQL("UPDATE $table SET deletedAt = ?, updatedAt = ? WHERE id = ?", arrayOf<Any?>(at, now(), id))
    }

    suspend fun deleteLink(id: String) = softDelete("link", listOf(id))
    suspend fun deleteTask(id: String) = softDelete("task", listOf(id))
    suspend fun deleteAsset(id: String) = softDelete("asset", listOf(id))
    suspend fun deleteSnapshot(id: String) = softDelete("snapshot", listOf(id))
    suspend fun deleteFollowerLog(id: String) = softDelete("follower_log", listOf(id))
    suspend fun deleteEvent(id: String) = softDelete("event", listOf(id))
    suspend fun restoreEvent(id: String) = restore("event", listOf(id))
    suspend fun deleteFeedPost(id: String) = softDelete("feed_post", listOf(id))
    suspend fun deleteHighlight(id: String) = softDelete("highlight", listOf(id))
    suspend fun restoreLink(id: String) = restore("link", listOf(id))
    suspend fun restoreTask(id: String) = restore("task", listOf(id))
    suspend fun restoreAsset(id: String) = restore("asset", listOf(id))
    suspend fun restoreSnapshot(id: String) = restore("snapshot", listOf(id))

    // ---------- links / tasks ----------
    suspend fun addLink(ownerType: String, ownerId: String, url: String, label: String = "", notes: String = "") {
        val t = now()
        val pos = data.value.linksOf(ownerType, ownerId).size
        dao.upsert(LinkEntity(newId(), ownerType, ownerId, url.trim(), label.trim(), notes.trim(), pos, t, t))
    }
    suspend fun saveLink(l: LinkEntity) = dao.upsert(l.copy(updatedAt = now()))

    suspend fun addTask(ownerType: String, ownerId: String, text: String, category: String = "", dueDay: Long? = null, relativeDays: Int? = null) {
        val t = now()
        val pos = data.value.tasksOf(ownerType, ownerId).size
        dao.upsert(TaskEntity(newId(), ownerType, ownerId, text.trim(), false, category, pos, dueDay, relativeDays, t, t))
    }
    suspend fun saveTask(task: TaskEntity) = dao.upsert(task.copy(updatedAt = now()))

    // ---------- media ----------
    /** Copies each picked Uri into app storage and attaches it. Returns how many succeeded. */
    suspend fun attachUris(uris: List<android.net.Uri>, ownerType: String, ownerId: String, role: String): Int {
        var ok = 0
        var pos = data.value.assetsOf(ownerType, ownerId).size
        for (u in uris) {
            val m = media.copyIn(u) ?: continue
            insertAsset(m, ownerType, ownerId, role, pos++); ok++
        }
        return ok
    }

    suspend fun insertAsset(m: ImportedMedia, ownerType: String, ownerId: String, role: String, position: Int): AssetEntity {
        val t = now()
        val a = AssetEntity(newId(), ownerType, ownerId, role, position, m.path, m.thumbPath, m.mime, m.isVideo, m.sizeBytes, m.width, m.height, m.name, t, t)
        dao.upsert(a)
        return a
    }

    suspend fun saveAsset(a: AssetEntity) = dao.upsert(a.copy(updatedAt = now()))

    suspend fun moveAsset(a: AssetEntity, delta: Int) = db.withTransaction {
        val list = data.value.assetsOf(a.ownerType, a.ownerId, a.role).toMutableList()
        val i = list.indexOfFirst { it.id == a.id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return@withTransaction
        list.add(j, list.removeAt(i))
        dao.upsertAssets(list.mapIndexed { idx, x -> x.copy(position = idx, updatedAt = now()) })
    }

    suspend fun setCover(contentId: String, assetId: String?, versionId: String? = null) {
        if (versionId != null) data.value.versionById[versionId]?.let { dao.upsertVersion(it.copy(coverAssetId = assetId, updatedAt = now())) }
        else update(contentId) { it.copy(coverAssetId = assetId) }
    }

    // ---------- events ----------
    suspend fun createEvent(name: String, startAt: Long? = null): EventEntity {
        val t = now()
        val e = EventEntity(newId(), name.trim(), startAt = startAt, createdAt = t, updatedAt = t)
        dao.upsert(e); return e
    }
    suspend fun saveEvent(e: EventEntity) = dao.upsert(e.copy(updatedAt = now()))

    /** Links content to an event in a phase. A day offset (vs the event date) makes it follow future date changes. */
    suspend fun linkToEvent(contentId: String, eventId: String?, phase: String, publishOffset: Int?, deadlineOffset: Int? = null) = update(contentId) { c ->
        val ev = eventId?.let { data.value.eventById[it] }
        val zone = data.value.zone
        var planned = c.plannedAt
        var deadline = c.deadlineDay
        if (ev?.startAt != null) {
            val eventDate = app.socmed.core.Cal.localDate(ev.startAt, zone)
            if (publishOffset != null) planned = app.socmed.core.Cal.moveToDate(c.plannedAt ?: ev.startAt, eventDate.plusDays(publishOffset.toLong()), zone)
            if (deadlineOffset != null) deadline = eventDate.plusDays(deadlineOffset.toLong()).toEpochDay()
        }
        c.copy(eventId = eventId, eventPhase = if (eventId == null) "" else phase,
            relPublishDays = if (eventId == null) null else publishOffset, relDeadlineDays = if (eventId == null) null else deadlineOffset,
            plannedAt = planned, deadlineDay = deadline)
    }

    /** Applies a reviewed list of date shifts (tasks, content deadlines and publishing dates) together with the new event time. */
    suspend fun applyEventShift(event: EventEntity, newStartAt: Long, proposals: List<ShiftProposal>) = db.withTransaction {
        val d = data.value; val zone = d.zone
        dao.upsert(event.copy(startAt = newStartAt, updatedAt = now()))
        for (p in proposals) when (p.kind) {
            "task" -> d.tasks.firstOrNull { it.id == p.id }?.let { dao.upsert(it.copy(dueDay = p.to.toEpochDay(), updatedAt = now())) }
            "deadline" -> d.contentById[p.id]?.let { dao.upsert(it.copy(deadlineDay = p.to.toEpochDay(), updatedAt = now())) }
            "publish" -> d.contentById[p.id]?.let { c ->
                val newAt = c.plannedAt?.let { app.socmed.core.Cal.moveToDate(it, p.to, zone) } ?: app.socmed.core.Cal.atTime(p.to, java.time.LocalTime.of(9, 0), zone)
                dao.upsert(c.copy(plannedAt = newAt, updatedAt = now()))
                // Platform versions that followed the parent's date keep following it; versions with their own date shift by the same days.
                val dayDiff = c.plannedAt?.let { java.time.temporal.ChronoUnit.DAYS.between(app.socmed.core.Cal.localDate(it, zone), p.to) } ?: 0L
                d.versionsOf(c.id).forEach { v -> v.plannedAt?.let { vp ->
                    dao.upsertVersion(v.copy(plannedAt = app.socmed.core.Cal.moveToDate(vp, app.socmed.core.Cal.localDate(vp, zone).plusDays(dayDiff), zone), updatedAt = now()))
                } }
            }
        }
    }

    // ---------- analytics ----------
    suspend fun saveSnapshot(s: SnapshotEntity) = dao.upsert(s.copy(updatedAt = now()))
    suspend fun saveSnapshots(list: List<SnapshotEntity>) = dao.upsertSnapshots(list)
    suspend fun saveFollowerLog(l: FollowerLogEntity) = dao.upsert(l.copy(updatedAt = now()))

    // ---------- instagram mockup ----------
    suspend fun saveFeedPost(p: FeedPostEntity) = dao.upsert(p.copy(updatedAt = now()))
    suspend fun saveHighlight(h: HighlightEntity) = dao.upsert(h.copy(updatedAt = now()))

    /**
     * Applies reviewed publishing-date swaps from the Instagram layout experiment. Keys are content ids. Only the
     * Instagram version's date changes (or the post's default date when that version inherits it).
     */
    suspend fun applyDateSwaps(swaps: List<app.socmed.core.DateSwap>, platform: String) = db.withTransaction {
        val d = data.value
        for (s in swaps) {
            val c = d.contentById[s.key] ?: continue
            val v = d.versionsOf(c.id).firstOrNull { it.platform == platform }
            if (v?.plannedAt != null) dao.upsertVersion(v.copy(plannedAt = s.newMillis, updatedAt = now()))
            else dao.upsert(c.copy(plannedAt = s.newMillis, updatedAt = now()))
        }
    }

    // ---------- statuses ----------
    suspend fun saveStatus(s: StatusDefEntity) = dao.upsert(s.copy(updatedAt = now()))
    suspend fun saveStatuses(list: List<StatusDefEntity>) = dao.upsertStatuses(list.map { it.copy(updatedAt = now()) })

    // ---------- housekeeping ----------
    /** Permanently removes tombstoned rows and media no record references. Returns bytes freed. */
    suspend fun purgeDeleted(): Long = withContext(Dispatchers.IO) {
        val w = db.openHelper.writableDatabase
        db.withTransaction {
            for (t in listOf("content", "version", "asset", "link", "task", "event", "snapshot", "follower_log", "feed_post", "highlight"))
                w.execSQL("DELETE FROM $t WHERE deletedAt IS NOT NULL")
            // children whose owner is gone
            w.execSQL("DELETE FROM version WHERE contentId NOT IN (SELECT id FROM content)")
            w.execSQL("DELETE FROM snapshot WHERE versionId NOT IN (SELECT id FROM version) AND versionId NOT IN (SELECT id FROM content)")
            w.execSQL("DELETE FROM asset WHERE (ownerType='content' AND ownerId NOT IN (SELECT id FROM content)) OR (ownerType='version' AND ownerId NOT IN (SELECT id FROM version)) OR (ownerType='event' AND ownerId NOT IN (SELECT id FROM event)) OR (ownerType='feedpost' AND ownerId NOT IN (SELECT id FROM feed_post)) OR (ownerType='highlight' AND ownerId NOT IN (SELECT id FROM highlight))")
            w.execSQL("DELETE FROM link WHERE (ownerType='content' AND ownerId NOT IN (SELECT id FROM content)) OR (ownerType='event' AND ownerId NOT IN (SELECT id FROM event))")
            w.execSQL("DELETE FROM task WHERE (ownerType='content' AND ownerId NOT IN (SELECT id FROM content)) OR (ownerType='event' AND ownerId NOT IN (SELECT id FROM event))")
        }
        val refs = HashSet<String>()
        dao.allAssetsIncludingDeleted().forEach { refs += it.path; if (it.thumbPath.isNotEmpty()) refs += it.thumbPath }
        media.deleteOrphans(refs)
    }

    suspend fun clearAllDataForRestore() {
        val w = db.openHelper.writableDatabase
        for (t in TABLES) w.execSQL("DELETE FROM $t")
    }

    companion object {
        val TABLES = listOf("content", "version", "asset", "link", "task", "event", "snapshot", "follower_log", "feed_post", "highlight", "status_def", "pref")
    }

    // ---------- sample data ----------
    suspend fun loadSample() = SampleData.load(this)
    suspend fun removeSample() = db.withTransaction {
        val w = db.openHelper.writableDatabase
        val p = "$SAMPLE_PREFIX%"
        // Children first (their owner ids are sample ids too).
        w.execSQL("DELETE FROM snapshot WHERE contentId LIKE ?", arrayOf(p))
        w.execSQL("DELETE FROM asset WHERE ownerId LIKE ?", arrayOf(p))
        w.execSQL("DELETE FROM link WHERE ownerId LIKE ?", arrayOf(p))
        w.execSQL("DELETE FROM task WHERE ownerId LIKE ?", arrayOf(p))
        w.execSQL("DELETE FROM version WHERE contentId LIKE ?", arrayOf(p))
        w.execSQL("DELETE FROM content WHERE id LIKE ?", arrayOf(p))
        w.execSQL("DELETE FROM event WHERE id LIKE ?", arrayOf(p))
        w.execSQL("DELETE FROM follower_log WHERE id LIKE ?", arrayOf(p))
        w.execSQL("DELETE FROM feed_post WHERE id LIKE ?", arrayOf(p))
        w.execSQL("DELETE FROM highlight WHERE id LIKE ?", arrayOf(p))
    }.also { purgeDeleted() }

    // Convenience for the UI: today in the user's zone.
    fun today(): LocalDate = LocalDate.now(data.value.zone)
}
