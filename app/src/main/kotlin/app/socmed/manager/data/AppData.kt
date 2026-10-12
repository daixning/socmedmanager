package app.socmed.manager.data

import app.socmed.core.Platform
import app.socmed.core.PostPerf
import app.socmed.core.Snap
import app.socmed.core.Metric
import app.socmed.core.Status
import java.time.LocalDate
import java.time.ZoneId

object Prefs {
    const val TZ = "tz"
    const val PALETTE = "palette"
    const val DARK = "dark"            // system | light | dark
    const val LAST_TAB = "last_content_tab"
    const val POSTS_LAYOUT = "posts_layout" // table | list
    const val CAL_MODE = "cal_mode"      // month | week | agenda
    const val NOTIFS = "notifs_enabled"
    const val LEAD_MIN = "remind_lead_min"
    const val PLAN_START = "plan_start"  // yyyy-MM
    const val IG_USER = "ig_username"
    const val IG_NAME = "ig_display_name"
    const val IG_BIO = "ig_bio"
    const val IG_POSTS = "ig_posts"
    const val IG_FOLLOWERS = "ig_followers"
    const val IG_FOLLOWING = "ig_following"
    const val IG_RATIO = "ig_ratio"
    const val IG_EXPERIMENT = "ig_experiment_order"
    const val BACKUP_LAST = "last_backup_at"
}

const val SAMPLE_PREFIX = "sample-"
fun isSampleId(id: String?) = id != null && id.startsWith(SAMPLE_PREFIX)

/** An immutable snapshot of every live record, with lookups. Every view reads from this, so edits show up everywhere. */
data class AppData(
    val contents: List<ContentEntity> = emptyList(),
    private val allVersions: List<VersionEntity> = emptyList(),
    val assets: List<AssetEntity> = emptyList(),
    val links: List<LinkEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val events: List<EventEntity> = emptyList(),
    val snapshots: List<SnapshotEntity> = emptyList(),
    val followerLogs: List<FollowerLogEntity> = emptyList(),
    val feedPosts: List<FeedPostEntity> = emptyList(),
    val highlights: List<HighlightEntity> = emptyList(),
    private val statusDefs: List<StatusDefEntity> = emptyList(),
    val prefs: Map<String, String> = emptyMap(),
    val loaded: Boolean = false,
) {
    val contentById: Map<String, ContentEntity> by lazy { contents.associateBy { it.id } }
    val eventById: Map<String, EventEntity> by lazy { events.associateBy { it.id } }

    val versions: List<VersionEntity> by lazy { allVersions.filter { it.contentId in contentById } }
    val versionById: Map<String, VersionEntity> by lazy { versions.associateBy { it.id } }
    val versionsByContent: Map<String, List<VersionEntity>> by lazy {
        versions.groupBy { it.contentId }.mapValues { (_, l) -> l.sortedBy { Platform.from(it.platform).ordinal } }
    }
    private val assetsByOwner: Map<Pair<String, String>, List<AssetEntity>> by lazy {
        assets.groupBy { it.ownerType to it.ownerId }.mapValues { (_, l) -> l.sortedBy { it.position } }
    }
    private val linksByOwner by lazy { links.groupBy { it.ownerType to it.ownerId }.mapValues { (_, l) -> l.sortedBy { it.position } } }
    private val tasksByOwner by lazy { tasks.groupBy { it.ownerType to it.ownerId }.mapValues { (_, l) -> l.sortedBy { it.position } } }
    val snapshotsByVersion: Map<String, List<SnapshotEntity>> by lazy { snapshots.groupBy { it.versionId } }
    val assetById: Map<String, AssetEntity> by lazy { assets.associateBy { it.id } }

    fun versionsOf(contentId: String) = versionsByContent[contentId].orEmpty()
    fun assetsOf(ownerType: String, ownerId: String, role: String? = null): List<AssetEntity> =
        assetsByOwner[ownerType to ownerId].orEmpty().let { l -> if (role == null) l else l.filter { it.role == role } }
    fun linksOf(ownerType: String, ownerId: String) = linksByOwner[ownerType to ownerId].orEmpty()
    fun tasksOf(ownerType: String, ownerId: String) = tasksByOwner[ownerType to ownerId].orEmpty()
    fun contentsOfEvent(eventId: String) = contents.filter { it.eventId == eventId && !it.archived }

    /** The cover shown everywhere for a content item (or one of its platform versions). */
    fun coverOf(c: ContentEntity, v: VersionEntity? = null): AssetEntity? {
        v?.coverAssetId?.let { assetById[it] }?.let { return it }
        c.coverAssetId?.let { assetById[it] }?.let { return it }
        val own = assetsOf("content", c.id)
        val pick = own.firstOrNull { it.role == AssetRole.FINAL && !it.isVideo }
            ?: own.firstOrNull { it.role == AssetRole.CAROUSEL }
            ?: own.firstOrNull { it.role == AssetRole.VIDEO }
            ?: own.firstOrNull { it.role == AssetRole.INSPIRATION }
            ?: own.firstOrNull()
        if (pick != null) return pick
        versionsOf(c.id).forEach { ver -> assetsOf("version", ver.id).firstOrNull()?.let { return it } }
        return null
    }

    // --- preferences ---
    fun pref(key: String, default: String = "") = prefs[key] ?: default
    fun prefBool(key: String, default: Boolean = false) = prefs[key]?.let { it == "1" } ?: default
    val zone: ZoneId get() = try { ZoneId.of(pref(Prefs.TZ, "Asia/Manila")) } catch (_: Exception) { ZoneId.of("Asia/Manila") }

    // --- statuses ---
    val statuses: List<StatusDefEntity> by lazy {
        (if (statusDefs.isEmpty()) Status.defaults.mapIndexed { i, (k, l) -> StatusDefEntity(k, l, i, false, 0) } else statusDefs)
            .sortedBy { it.position }
    }
    val visibleStatuses get() = statuses.filter { !it.hidden }
    fun statusLabel(key: String) = statuses.firstOrNull { it.key == key }?.label ?: key.replaceFirstChar { it.uppercase() }

    // --- sample data ---
    val hasSample: Boolean by lazy { contents.any { isSampleId(it.id) } || events.any { isSampleId(it.id) } }
    val hasReal: Boolean by lazy { contents.any { !isSampleId(it.id) } }

    fun pillars() = contents.map { it.pillar }.filter { it.isNotBlank() }.distinct().sorted()
    fun campaigns() = contents.map { it.campaign }.filter { it.isNotBlank() }.distinct().sorted()
    fun collections() = contents.map { it.collection }.filter { it.isNotBlank() }.distinct().sorted()
    fun allTags() = contents.flatMap { splitTags(it.tags) }.distinct().sorted()

    /** Flattens platform versions (or the content itself when it has none) for the analytics engine. */
    fun perf(sample: Boolean): List<PostPerf> = contents.filter { isSampleId(it.id) == sample }.flatMap { c ->
        val vs = versionsOf(c.id)
        if (vs.isEmpty()) listOf(
            PostPerf(c.id, c.id, c.title, "", c.format, c.pillar, c.campaign, c.eventId, c.plannedAt, c.publishedAt, c.status, snaps(c.id))
        ) else vs.map { v ->
            PostPerf(v.id, c.id, c.title, v.platform, v.format.ifBlank { c.format }, c.pillar, c.campaign, c.eventId,
                v.plannedAt ?: c.plannedAt, v.publishedAt ?: if (v.status == Status.PUBLISHED) c.publishedAt else null, v.status, snaps(v.id))
        }
    }

    private fun snaps(versionId: String): List<Snap> = snapshotsByVersion[versionId].orEmpty().map { s -> Snap(s.id, s.dayEpoch, s.toValues()) }

    /** One entry per content item per planned day (grouping the platform versions that share that day). */
    fun planEntries(includeArchived: Boolean = false): List<PlanEntry> = contents.filter { includeArchived || !it.archived }.flatMap { c ->
        val vs = versionsOf(c.id)
        if (vs.isEmpty()) {
            if (c.plannedAt == null) emptyList() else listOf(PlanEntry(c, emptyList(), c.plannedAt, c.status))
        } else {
            vs.filter { (it.plannedAt ?: c.plannedAt) != null }
                .groupBy { Days.dayOf(it.plannedAt ?: c.plannedAt!!, zone) }
                .map { (_, group) ->
                    val at = group.minOf { it.plannedAt ?: c.plannedAt!! }
                    val statuses = group.map { it.status }.distinct()
                    PlanEntry(c, group, at, if (statuses.size == 1) statuses[0] else c.status)
                }
        }
    }

    /** Active, unfinished content that has no planned date on any platform. */
    fun unscheduled(): List<ContentEntity> = contents.filter { c ->
        !c.archived && Status.isOpen(c.status) && c.status != Status.ON_HOLD &&
            (versionsOf(c.id).ifEmpty { null }?.all { (it.plannedAt ?: c.plannedAt) == null } ?: (c.plannedAt == null))
    }

    fun isOverdue(e: PlanEntry, now: Long): Boolean {
        val at = e.atMillis ?: return false
        if (at >= now) return false
        val open = if (e.versions.isEmpty()) listOf(e.content.status) else e.versions.map { it.status }
        return open.any { it != Status.PUBLISHED && it != Status.CANCELLED && it != Status.ON_HOLD }
    }
}

object Days {
    fun dayOf(millis: Long, zone: ZoneId): LocalDate = app.socmed.core.Cal.localDate(millis, zone)
}

data class PlanEntry(val content: ContentEntity, val versions: List<VersionEntity>, val atMillis: Long?, val status: String) {
    val platforms: List<Platform> get() = versions.map { Platform.from(it.platform) }
}

fun splitTags(s: String): List<String> = s.split(',', '\n').map { it.trim().trimStart('#') }.filter { it.isNotEmpty() }

fun SnapshotEntity.toValues(): Map<Metric, Double> = buildMap {
    views?.let { put(Metric.VIEWS, it) }; reach?.let { put(Metric.REACH, it) }; impressions?.let { put(Metric.IMPRESSIONS, it) }
    likes?.let { put(Metric.LIKES, it) }; comments?.let { put(Metric.COMMENTS, it) }; shares?.let { put(Metric.SHARES, it) }
    saves?.let { put(Metric.SAVES, it) }; linkClicks?.let { put(Metric.LINK_CLICKS, it) }; inquiries?.let { put(Metric.INQUIRIES, it) }
    followersGained?.let { put(Metric.FOLLOWERS_GAINED, it) }; watchTimeSec?.let { put(Metric.WATCH_TIME_SEC, it) }
    retentionPct?.let { put(Metric.RETENTION_PCT, it) }; spend?.let { put(Metric.SPEND, it) }; revenue?.let { put(Metric.REVENUE, it) }
}

fun SnapshotEntity.withValues(v: Map<Metric, Double?>): SnapshotEntity = copy(
    views = v[Metric.VIEWS], reach = v[Metric.REACH], impressions = v[Metric.IMPRESSIONS], likes = v[Metric.LIKES],
    comments = v[Metric.COMMENTS], shares = v[Metric.SHARES], saves = v[Metric.SAVES], linkClicks = v[Metric.LINK_CLICKS],
    inquiries = v[Metric.INQUIRIES], followersGained = v[Metric.FOLLOWERS_GAINED], watchTimeSec = v[Metric.WATCH_TIME_SEC],
    retentionPct = v[Metric.RETENTION_PCT], spend = v[Metric.SPEND], revenue = v[Metric.REVENUE],
)
