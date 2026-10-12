package app.socmed.core

/** Production/publishing status keys. Labels are user-editable in the app; keys never change. */
object Status {
    const val IDEA = "idea"
    const val PLANNED = "planned"
    const val IN_PRODUCTION = "in_production"
    const val READY = "ready"
    const val PUBLISHED = "published"
    const val ON_HOLD = "on_hold"
    const val CANCELLED = "cancelled"

    val defaults: List<Pair<String, String>> = listOf(
        IDEA to "Idea",
        PLANNED to "Planned",
        IN_PRODUCTION to "In Production",
        READY to "Ready",
        PUBLISHED to "Published",
        ON_HOLD to "On Hold",
        CANCELLED to "Cancelled",
    )

    /** Statuses that mean "still needs work / not yet out in the world". */
    fun isOpen(key: String) = key != PUBLISHED && key != CANCELLED
}

enum class Platform(val label: String, val short: String) {
    INSTAGRAM("Instagram", "IG"),
    TIKTOK("TikTok", "TT"),
    FACEBOOK("Facebook", "FB"),
    YOUTUBE("YouTube", "YT"),
    X("X", "X"),
    LINKEDIN("LinkedIn", "LI"),
    THREADS("Threads", "TH"),
    PINTEREST("Pinterest", "PI"),
    OTHER("Other", "··");

    companion object {
        fun from(key: String?): Platform = entries.firstOrNull { it.name == key } ?: OTHER
    }
}

enum class Format(val label: String) {
    PHOTO("Photo"), CAROUSEL("Carousel"), REEL("Reel"), STORY("Story"),
    VIDEO("Video"), TEXT("Text"), CUSTOM("Custom");

    companion object {
        fun from(key: String?): Format = entries.firstOrNull { it.name == key } ?: CUSTOM
        fun labelOf(key: String?): String = if (key.isNullOrBlank()) "Unspecified" else from(key).label
    }
}

val GOALS = listOf("Awareness", "Engagement", "Education", "Community", "Inquiries", "Conversion")

enum class Priority(val label: String) { LOW("Low"), MEDIUM("Medium"), HIGH("High");
    companion object { fun from(key: String?) = entries.firstOrNull { it.name == key } ?: MEDIUM }
}

enum class EventPhase(val label: String, val hint: String) {
    BEFORE("Before the event", "Announcements, invitations, teasers, countdowns"),
    DURING("During the event", "Coverage, Stories, interviews, updates"),
    AFTER("After the event", "Recaps, appreciation posts, highlights, feedback");

    companion object { fun from(key: String?) = entries.firstOrNull { it.name == key } }
}

/** Metrics that can be recorded in an analytics snapshot. A missing key means "unknown", never zero. */
enum class Metric(val label: String, val isCount: Boolean = true) {
    VIEWS("Views"), REACH("Reach"), IMPRESSIONS("Impressions"),
    LIKES("Likes"), COMMENTS("Comments"), SHARES("Shares"), SAVES("Saves"),
    LINK_CLICKS("Link clicks"), INQUIRIES("Inquiries"), FOLLOWERS_GAINED("Followers gained"),
    WATCH_TIME_SEC("Watch time (sec)", false), RETENTION_PCT("Retention (%)", false),
    SPEND("Spend", false), REVENUE("Revenue", false);

    companion object { fun from(key: String?) = entries.firstOrNull { it.name == key } }
}

/** One dated reading of a post's metrics. Snapshots are history; they are never summed together. */
data class Snap(val id: String, val dayEpoch: Long, val values: Map<Metric, Double>)
