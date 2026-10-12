package app.socmed.manager.data

import androidx.room.withTransaction
import app.socmed.core.Cal
import app.socmed.core.EventPhase
import app.socmed.core.Status
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/** Optional demo records. Every id starts with "sample-" so they can be removed in one tap and are kept out of real analytics. */
object SampleData {
    private data class S(val title: String, val pillar: String, val format: String, val platforms: List<String>, val dayOffset: Int, val status: String, val hue: Float, val campaign: String = "", val event: String? = null, val phase: String = "")

    suspend fun load(repo: Repository) {
        if (repo.data.value.hasSample) return
        val zone = repo.data.value.zone
        val today = LocalDate.now(zone)
        val t = System.currentTimeMillis()
        val dao = repo.db.dao()
        val rnd = Random(7)
        val eventDate = today.plusDays(12)
        val ev = EventEntity("sample-event-1", "Spring Market Pop-up", "Fresh & local", "A weekend pop-up market to meet customers in person.", "Grow local awareness; collect 50 email sign-ups.",
            Cal.atTime(eventDate, LocalTime.of(10, 0), zone), "Riverside Hall", "CONFIRMED", "Sample event — safe to delete.", createdAt = t, updatedAt = t)
        val ev2 = EventEntity("sample-event-2", "Anniversary Livestream", "Five years!", "", "Thank the community.", Cal.atTime(today.minusDays(20), LocalTime.of(19, 0), zone), "Online", "DONE",
            reviewWorked = "Q&A segment kept people watching.", reviewNotWorked = "Started 10 minutes late.", reviewLessons = "Test audio earlier.", createdAt = t, updatedAt = t)
        val items = listOf(
            S("5 tips for better product photos", "Education", "CAROUSEL", listOf("INSTAGRAM", "FACEBOOK"), -21, Status.PUBLISHED, 270f),
            S("Behind the scenes: packing orders", "Behind the scenes", "REEL", listOf("INSTAGRAM", "TIKTOK"), -18, Status.PUBLISHED, 330f),
            S("Customer love: Maya's review", "Community", "PHOTO", listOf("INSTAGRAM"), -15, Status.PUBLISHED, 20f),
            S("How we choose our materials", "Education", "CAROUSEL", listOf("INSTAGRAM", "LINKEDIN"), -12, Status.PUBLISHED, 200f),
            S("Quick desk tour", "Behind the scenes", "REEL", listOf("INSTAGRAM", "TIKTOK", "YOUTUBE"), -9, Status.PUBLISHED, 150f),
            S("Monday motivation quote", "Fun", "PHOTO", listOf("INSTAGRAM", "FACEBOOK"), -6, Status.PUBLISHED, 45f),
            S("Anniversary livestream recap", "Community", "VIDEO", listOf("YOUTUBE"), -19, Status.PUBLISHED, 300f, "Anniversary", "sample-event-2", "AFTER"),
            S("New spring collection teaser", "Product", "REEL", listOf("INSTAGRAM", "TIKTOK"), 2, Status.READY, 340f),
            S("Gift guide carousel", "Education", "CAROUSEL", listOf("INSTAGRAM"), 5, Status.IN_PRODUCTION, 180f),
            S("Pop-up market: save the date", "Promo", "PHOTO", listOf("INSTAGRAM", "FACEBOOK"), 3, Status.IN_PRODUCTION, 15f, "Spring Market", "sample-event-1", "BEFORE"),
            S("Pop-up countdown: 3 days", "Promo", "PHOTO", listOf("INSTAGRAM"), 9, Status.PLANNED, 60f, "Spring Market", "sample-event-1", "BEFORE"),
            S("Meet the maker Q&A", "Community", "STORY", listOf("INSTAGRAM"), 8, Status.PLANNED, 250f),
            S("Weekend reset routine", "Lifestyle", "REEL", listOf("INSTAGRAM", "TIKTOK"), 14, Status.PLANNED, 120f),
            S("Overdue example: shipping update", "Product", "PHOTO", listOf("FACEBOOK"), -2, Status.READY, 90f),
            S("Idea: customer spotlight series", "Community", "CAROUSEL", emptyList(), 0, Status.IDEA, 280f),
        )
        repo.db.withTransaction {
            dao.upsert(ev); dao.upsert(ev2)
            items.forEachIndexed { i, s ->
                val cid = "sample-c${i + 1}"
                val planned = if (s.status == Status.IDEA) null else Cal.atTime(today.plusDays(s.dayOffset.toLong()), LocalTime.of(listOf(9, 12, 18, 20)[i % 4], 0), zone)
                val published = if (s.status == Status.PUBLISHED) planned else null
                dao.upsert(ContentEntity(cid, s.title, "Sample content to explore the app.", s.pillar, s.campaign, s.event, s.phase, null, null, "sample,${s.pillar.lowercase()}", "Followers", "Engagement", s.format,
                    s.status, "MEDIUM", if (s.status == Status.IDEA) "Series ideas" else "", null, planned?.let { Cal.localDate(it, zone).minusDays(2).toEpochDay() }, planned, published,
                    if (published != null) "https://example.com/p/$i" else "", "Sample caption for “${s.title}”. ✨", "Stop scrolling!", "Save this for later", "#sample #demo",
                    s.status == Status.IN_PRODUCTION || s.status == Status.IDEA, "", null, 0.5f, 0.5f, 1f, false, t, t))
                val cover = repo.media.createPlaceholder(s.title, s.hue)
                dao.upsert(AssetEntity("sample-a$i", "content", cid, if (s.status == Status.IDEA) AssetRole.INSPIRATION else AssetRole.FINAL, 0, cover.path, cover.thumbPath, cover.mime, false, cover.sizeBytes, cover.width, cover.height, "sample", t, t))
                if (s.status == Status.IDEA) dao.upsert(LinkEntity("sample-l$i", "content", cid, "https://example.com/inspiration", "Inspiration board", "Sample link", 0, t, t))
                if (s.status == Status.IN_PRODUCTION) {
                    dao.upsert(TaskEntity("sample-t$i-1", "content", cid, "Shoot product photos", false, "PHOTOGRAPH", 0, null, null, t, t))
                    dao.upsert(TaskEntity("sample-t$i-2", "content", cid, "Design cover graphic", false, "DESIGN", 1, null, null, t, t))
                }
                s.platforms.forEachIndexed { j, p ->
                    val vid = "sample-v$i-$j"
                    dao.upsertVersion(VersionEntity(vid, cid, p, "", "", "", planned, s.status, published, if (published != null) "https://example.com/$p/$i" else "", null, t, t))
                    if (s.status == Status.PUBLISHED) {
                        val base = 300 + rnd.nextInt(1500)
                        val pubDay = Cal.localDate(planned!!, zone).toEpochDay()
                        for ((k, mult) in listOf(3L to 0.55, 10L to 1.0)) {
                            val reach = (base * mult).toInt().toDouble()
                            dao.upsert(SnapshotEntity("sample-s$i-$j-$k", vid, cid, pubDay + k, views = (reach * 1.4).toInt().toDouble(), reach = reach,
                                impressions = (reach * 1.6).toInt().toDouble(), likes = (reach * (0.04 + rnd.nextDouble() * 0.05)).toInt().toDouble(),
                                comments = (reach * 0.006).toInt().toDouble(), shares = (reach * 0.01).toInt().toDouble(),
                                saves = (reach * (if (s.format == "CAROUSEL") 0.06 else 0.015)).toInt().toDouble(), linkClicks = if (j == 0) (reach * 0.01).toInt().toDouble() else null,
                                createdAt = t, updatedAt = t))
                        }
                    }
                }
            }
            // event tasks relative to the event date
            listOf("Book the table" to -14, "Print flyers" to -7, "Pack stock" to -1, "Thank-you messages" to 2).forEachIndexed { i, (txt, rel) ->
                dao.upsert(TaskEntity("sample-et$i", "event", "sample-event-1", txt, false, "", i, eventDate.plusDays(rel.toLong()).toEpochDay(), rel, t, t))
            }
            dao.upsert(LinkEntity("sample-el1", "event", "sample-event-1", "https://example.com/market", "Organiser page", "", 0, t, t))
            // follower log
            listOf("INSTAGRAM" to 1200L, "TIKTOK" to 640L).forEach { (p, start) ->
                for (w in 0..8) dao.upsert(FollowerLogEntity("sample-f-$p-$w", p, today.minusDays((8 - w) * 7L).toEpochDay(), start + w * (if (p == "INSTAGRAM") 22 else 15) + rnd.nextInt(8), "", t, t))
            }
            val h = repo.media.createPlaceholder("About", 280f, 400, 400)
            dao.upsert(HighlightEntity("sample-h1", "About", 0, t, t))
            dao.upsert(AssetEntity("sample-ha1", "highlight", "sample-h1", AssetRole.HIGHLIGHT, 0, h.path, h.thumbPath, h.mime, false, h.sizeBytes, h.width, h.height, "", t, t))
        }
    }
}
