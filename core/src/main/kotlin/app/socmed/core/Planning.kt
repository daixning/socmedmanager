package app.socmed.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

object Cal {
    /** Weeks (Monday-first) covering the month, each a list of 7 dates. */
    fun monthGrid(year: Int, month: Int): List<List<LocalDate>> {
        val first = LocalDate.of(year, month, 1)
        var d = first.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val last = first.with(TemporalAdjusters.lastDayOfMonth())
        val weeks = ArrayList<List<LocalDate>>()
        while (!d.isAfter(last)) { weeks += (0..6).map { d.plusDays(it.toLong()) }; d = d.plusDays(7) }
        return weeks
    }

    fun weekOf(date: LocalDate): List<LocalDate> {
        val start = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        return (0..6).map { start.plusDays(it.toLong()) }
    }

    fun localDate(millis: Long, zone: ZoneId): LocalDate = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** Moves an instant to another calendar day, keeping the wall-clock time in [zone]. */
    fun moveToDate(millis: Long, newDate: LocalDate, zone: ZoneId, defaultTime: LocalTime = LocalTime.of(9, 0)): Long {
        val time = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return ZonedDateTime.of(newDate, time ?: defaultTime, zone).toInstant().toEpochMilli()
    }

    fun atTime(date: LocalDate, time: LocalTime, zone: ZoneId): Long = ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()
}

/** Something anchored to an event date by a signed day offset (−7 = a week before, +2 = two days after). */
data class Anchored(val id: String, val kind: String, val label: String, val relativeDays: Int, val current: LocalDate?)
data class ShiftProposal(val id: String, val kind: String, val label: String, val from: LocalDate?, val to: LocalDate) {
    val changed get() = from != to
}

object EventShift {
    fun propose(newEventDate: LocalDate, items: List<Anchored>): List<ShiftProposal> =
        items.map { ShiftProposal(it.id, it.kind, it.label, it.current, newEventDate.plusDays(it.relativeDays.toLong())) }
            .filter { it.changed }

    fun offsetFor(eventDate: LocalDate, target: LocalDate): Int = java.time.temporal.ChronoUnit.DAYS.between(eventDate, target).toInt()

    fun describe(offset: Int): String = when {
        offset == 0 -> "on the event day"
        offset < 0 -> "${-offset} day${if (offset == -1) "" else "s"} before"
        else -> "$offset day${if (offset == 1) "" else "s"} after"
    }
}

/** One tile in the Instagram profile mockup. [key] is the content id for planned tiles, or "ex:<id>" for existing posts. */
data class FeedTile(val key: String, val dateMillis: Long, val pinned: Boolean, val planned: Boolean)
data class DateSwap(val key: String, val oldMillis: Long, val newMillis: Long)

object FeedPlanner {
    /** Newest first. Pinned tiles come first (newest pinned first). Tiles after [cutoffMillis] are hidden. */
    fun scheduleOrder(tiles: List<FeedTile>, cutoffMillis: Long?, plannedOnly: Boolean): List<FeedTile> {
        val visible = tiles.filter { (cutoffMillis == null || it.dateMillis <= cutoffMillis) && (!plannedOnly || it.planned) }
        val (pinned, rest) = visible.partition { it.pinned }
        return pinned.sortedByDescending { it.dateMillis } + rest.sortedByDescending { it.dateMillis }
    }

    /**
     * Applies an experimental order to publishing dates: the planned tiles keep the same *set* of dates, reassigned so the
     * tile at the top of the experiment gets the newest date. Returns only the tiles whose date actually changes.
     */
    fun proposeSwaps(experimentOrderPlannedKeys: List<String>, plannedTiles: List<FeedTile>): List<DateSwap> {
        val byKey = plannedTiles.associateBy { it.key }
        val keys = experimentOrderPlannedKeys.filter { it in byKey }
        val dates = keys.map { byKey.getValue(it).dateMillis }.sortedDescending()
        return keys.mapIndexedNotNull { i, k ->
            val old = byKey.getValue(k).dateMillis
            if (old != dates[i]) DateSwap(k, old, dates[i]) else null
        }
    }

    /** Merge a saved experiment order with the current planned tiles: unknown keys dropped, new ones appended in schedule order. */
    fun reconcile(saved: List<String>, scheduleKeys: List<String>): List<String> {
        val valid = saved.filter { it in scheduleKeys }.distinct()
        return valid + scheduleKeys.filter { it !in valid }
    }
}
