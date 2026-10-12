package app.socmed.manager.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Every syncable record has: a stable String UUID id, createdAt/updatedAt (epoch millis, UTC) and a nullable
 * deletedAt tombstone. Deletes are soft so a future sync layer can propagate them and so Undo works.
 * Ids that start with "sample-" belong to the optional demo data and can be removed in one action.
 */

@Entity(tableName = "content", indices = [Index("eventId"), Index("status")])
data class ContentEntity(
    @PrimaryKey val id: String,
    val title: String = "",
    val description: String = "",
    val pillar: String = "",
    val campaign: String = "",
    val eventId: String? = null,
    val eventPhase: String = "",
    val relPublishDays: Int? = null,
    val relDeadlineDays: Int? = null,
    val tags: String = "",
    val audience: String = "",
    val goal: String = "",
    val format: String = "",
    val status: String = "idea",
    val priority: String = "MEDIUM",
    val collection: String = "",
    val targetDay: Long? = null,
    val deadlineDay: Long? = null,
    val plannedAt: Long? = null,
    val publishedAt: Long? = null,
    val publishedUrl: String = "",
    val caption: String = "",
    val hook: String = "",
    val cta: String = "",
    val hashtags: String = "",
    val visualsNeeded: Boolean = false,
    val notes: String = "",
    val coverAssetId: String? = null,
    val cropX: Float = 0.5f,
    val cropY: Float = 0.5f,
    val cropZoom: Float = 1f,
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** A platform-specific version of a content item (own caption, format, date, status, URL, assets, analytics). */
@Entity(tableName = "version", indices = [Index("contentId")])
data class VersionEntity(
    @PrimaryKey val id: String,
    val contentId: String,
    val platform: String,
    val format: String = "",
    val caption: String = "",
    val hashtags: String = "",
    val plannedAt: Long? = null,
    val status: String = "planned",
    val publishedAt: Long? = null,
    val url: String = "",
    val coverAssetId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** ownerType: content | version | event | profile | feedpost | highlight. role: see [AssetRole]. */
@Entity(tableName = "asset", indices = [Index(value = ["ownerType", "ownerId"])])
data class AssetEntity(
    @PrimaryKey val id: String,
    val ownerType: String,
    val ownerId: String,
    val role: String,
    val position: Int = 0,
    val path: String,
    val thumbPath: String = "",
    val mime: String = "",
    val isVideo: Boolean = false,
    val sizeBytes: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val name: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

object AssetRole {
    const val INSPIRATION = "INSPIRATION"
    const val FINAL = "FINAL"
    const val CAROUSEL = "CAROUSEL"
    const val VIDEO = "VIDEO"
    const val ATTACH = "ATTACH"
    const val AVATAR = "AVATAR"
    const val HIGHLIGHT = "HIGHLIGHT"
    const val FEED = "FEED"
}

@Entity(tableName = "link", indices = [Index(value = ["ownerType", "ownerId"])])
data class LinkEntity(
    @PrimaryKey val id: String,
    val ownerType: String,
    val ownerId: String,
    val url: String,
    val label: String = "",
    val notes: String = "",
    val position: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Checklist item for a content item (ownerType=content) or an event task (ownerType=event). */
@Entity(tableName = "task", indices = [Index(value = ["ownerType", "ownerId"])])
data class TaskEntity(
    @PrimaryKey val id: String,
    val ownerType: String,
    val ownerId: String,
    val text: String,
    val done: Boolean = false,
    /** For production checklists: PHOTOGRAPH, DESIGN, FILM, EDIT, OTHER or blank. */
    val category: String = "",
    val position: Int = 0,
    val dueDay: Long? = null,
    /** Event tasks only: days relative to the event date (−7 = a week before). */
    val relativeDays: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "event")
data class EventEntity(
    @PrimaryKey val id: String,
    val name: String,
    val theme: String = "",
    val description: String = "",
    val objectives: String = "",
    val startAt: Long? = null,
    val location: String = "",
    val status: String = "PLANNING",
    val notes: String = "",
    val reviewWorked: String = "",
    val reviewNotWorked: String = "",
    val reviewAudience: String = "",
    val reviewPerformance: String = "",
    val reviewLessons: String = "",
    val reviewNext: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** One dated reading of a platform version's metrics. Null = unknown (never stored as zero). */
@Entity(tableName = "snapshot", indices = [Index("versionId")])
data class SnapshotEntity(
    @PrimaryKey val id: String,
    val versionId: String,
    val contentId: String,
    val dayEpoch: Long,
    val views: Double? = null,
    val reach: Double? = null,
    val impressions: Double? = null,
    val likes: Double? = null,
    val comments: Double? = null,
    val shares: Double? = null,
    val saves: Double? = null,
    val linkClicks: Double? = null,
    val inquiries: Double? = null,
    val followersGained: Double? = null,
    val watchTimeSec: Double? = null,
    val retentionPct: Double? = null,
    val spend: Double? = null,
    val revenue: Double? = null,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "follower_log")
data class FollowerLogEntity(
    @PrimaryKey val id: String,
    val platform: String,
    val dayEpoch: Long,
    val followers: Long,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** An already-published post the user added manually to establish the current Instagram feed. */
@Entity(tableName = "feed_post")
data class FeedPostEntity(
    @PrimaryKey val id: String,
    val caption: String = "",
    val postedAt: Long,
    val type: String = "PHOTO",
    val pinned: Boolean = false,
    val cropX: Float = 0.5f,
    val cropY: Float = 0.5f,
    val cropZoom: Float = 1f,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "highlight")
data class HighlightEntity(
    @PrimaryKey val id: String,
    val title: String,
    val position: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Editable production statuses. [key] is stable; [label] is what the user sees. */
@Entity(tableName = "status_def")
data class StatusDefEntity(
    @PrimaryKey val key: String,
    val label: String,
    val position: Int,
    val hidden: Boolean = false,
    val updatedAt: Long,
)

@Entity(tableName = "pref")
data class PrefEntity(@PrimaryKey val key: String, val value: String, val updatedAt: Long)
