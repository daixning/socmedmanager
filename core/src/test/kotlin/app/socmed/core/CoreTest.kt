package app.socmed.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.*

class CoreTest {
    private val zone = ZoneId.of("Asia/Manila")
    private fun ms(date: String, hour: Int = 10) = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun day(date: String) = LocalDate.parse(date).toEpochDay()

    private fun post(id: String, format: String, pillar: String, pub: String, vararg v: Pair<Metric, Double>, snaps: List<Snap>? = null) =
        PostPerf(id, "c$id", "Post $id", "INSTAGRAM", format, pillar, "", null, ms(pub), ms(pub), Status.PUBLISHED,
            snaps ?: listOf(Snap("s$id", day(pub) + 3, v.toMap())))

    @Test fun csvRoundTrip() {
        val rows = listOf(listOf("a", "b,c", "say \"hi\""), listOf("line\nbreak", "", "x"))
        assertEquals(rows, Csv.parse(Csv.write(rows)))
    }

    @Test fun csvDelimiterAndBom() {
        val t = Csv.parse("﻿date;views\r\n2026-01-02;1,234\r\n")
        assertEquals(listOf(listOf("date", "views"), listOf("2026-01-02", "1,234")), t)
    }

    @Test fun parseNumbers() {
        assertEquals(1234.0, CsvImport.parseNumber("1,234"))
        assertEquals(12.5, CsvImport.parseNumber("12.5%"))
        assertEquals(1500.0, CsvImport.parseNumber("1.5k"))
        assertNull(CsvImport.parseNumber(""))
        assertNull(CsvImport.parseNumber("abc"))
        assertEquals(0.0, CsvImport.parseNumber("0"))
    }

    @Test fun engagementRateRules() {
        val full = mapOf(Metric.REACH to 200.0, Metric.LIKES to 10.0, Metric.COMMENTS to 2.0, Metric.SHARES to 3.0, Metric.SAVES to 5.0)
        val r = Rates.compute(Rates.all.first { it.id == "eng_reach" }, full)
        assertEquals(10.0, r.value)
        // Missing saves => no rate (blank is not zero).
        val missing = Rates.compute(Rates.all.first(), full - Metric.SAVES)
        assertNull(missing.value); assertTrue(missing.reason!!.contains("saves"))
        // Zero denominator => no rate.
        assertNull(Rates.compute(Rates.all.first(), full + (Metric.REACH to 0.0)).value)
        // Explicit zero numerators are valid values.
        assertEquals(0.0, Rates.compute(Rates.all.first(), full.mapValues { if (it.key == Metric.REACH) 100.0 else 0.0 }).value)
    }

    @Test fun snapshotsAreNotSummedAndLatestIsUsed() {
        val snaps = listOf(Snap("a", day("2026-03-02"), mapOf(Metric.VIEWS to 100.0)), Snap("b", day("2026-03-09"), mapOf(Metric.VIEWS to 250.0)))
        val p = post("1", "REEL", "Edu", "2026-03-01", snaps = snaps)
        assertEquals(250.0, Analytics.metricValue(p, Metric.VIEWS, null))
        assertEquals(100.0, Analytics.metricValue(p, Metric.VIEWS, day("2026-03-05")))
        assertNull(Analytics.metricValue(p, Metric.VIEWS, day("2026-03-01")))
        val t = Analytics.trend(listOf(p), Metric.VIEWS, null, zone, true)
        assertEquals(250.0, t.single().value)
    }

    @Test fun groupMedianAndMean() {
        val ps = listOf(
            post("1", "CAROUSEL", "Edu", "2026-03-01", Metric.SAVES to 10.0),
            post("2", "CAROUSEL", "Edu", "2026-03-02", Metric.SAVES to 30.0),
            post("3", "CAROUSEL", "Edu", "2026-03-03", Metric.SAVES to 20.0),
            post("4", "PHOTO", "Fun", "2026-03-04", Metric.SAVES to 2.0),
            post("5", "PHOTO", "Fun", "2026-03-05"),
        )
        val g = Analytics.byGroup(ps, Metric.SAVES, null) { it.format }
        assertEquals("CAROUSEL", g[0].name); assertEquals(20.0, g[0].median); assertEquals(20.0, g[0].mean); assertEquals(3, g[0].n)
        assertEquals(1, g[1].n) // post without saves excluded
    }

    @Test fun insightsRuleBasedAndLimitedFlag() {
        val ps = (1..3).map { post("c$it", "CAROUSEL", "Edu", "2026-03-0$it", Metric.SAVES to 20.0 + it) } +
            (1..4).map { post("p$it", "PHOTO", "Fun", "2026-03-1$it", Metric.SAVES to 3.0) }
        val f = AnalyticsFilter(day("2026-03-01"), day("2026-03-31"))
        val ins = Analytics.insights(ps, f, zone)
        assertTrue(ins.isNotEmpty())
        val first = ins.first()
        assertTrue(first.text.contains("Carousel"), first.text)
        assertEquals(7, first.sampleSize)
        assertFalse(first.limited)
        val small = Analytics.insights(ps.take(2) + ps.takeLast(1), f, zone)
        assertTrue(small.all { it.limited })
    }

    @Test fun equivalentPeriodComparison() {
        val ps = listOf(
            post("1", "PHOTO", "A", "2026-02-10", Metric.LIKES to 10.0),
            post("2", "PHOTO", "A", "2026-03-10", Metric.LIKES to 30.0),
            post("3", "PHOTO", "A", "2026-03-11", Metric.LIKES to 50.0),
        )
        val f = AnalyticsFilter(day("2026-03-01"), day("2026-03-31"))
        val c = Analytics.compareEquivalent(ps, f, Metric.LIKES, zone, useMedian = false)!!
        assertEquals(40.0, c.current); assertEquals(10.0, c.previous); assertEquals(300.0, c.pct)
    }

    @Test fun progressDoesNotTreatPlannedAsPublished() {
        val planned = PostPerf("v", "c", "t", "INSTAGRAM", "PHOTO", "", "", null, ms("2026-03-05"), null, Status.READY, emptyList())
        val pr = Analytics.progress(listOf(planned), AnalyticsFilter(day("2026-03-01"), day("2026-03-31")), zone, ms("2026-03-20"))
        assertEquals(1, pr.planned); assertEquals(0, pr.published); assertEquals(1, pr.overdue)
    }

    @Test fun eventShiftProposal() {
        val items = listOf(Anchored("t1", "task", "Book venue", -7, LocalDate.parse("2026-05-03")), Anchored("c1", "post", "Recap", 2, LocalDate.parse("2026-05-12")), Anchored("x", "task", "ok", 0, LocalDate.parse("2026-05-20")))
        val p = EventShift.propose(LocalDate.parse("2026-05-20"), items)
        assertEquals(listOf(LocalDate.parse("2026-05-13"), LocalDate.parse("2026-05-22")), p.map { it.to })
        assertEquals(-7, EventShift.offsetFor(LocalDate.parse("2026-05-10"), LocalDate.parse("2026-05-03")))
    }

    @Test fun feedScheduleAndSwaps() {
        val tiles = listOf(FeedTile("a", ms("2026-03-01"), false, true), FeedTile("b", ms("2026-03-05"), false, true),
            FeedTile("ex:1", ms("2026-02-01"), false, false), FeedTile("p", ms("2026-01-01"), true, false))
        assertEquals(listOf("p", "b", "a", "ex:1"), FeedPlanner.scheduleOrder(tiles, null, false).map { it.key })
        assertEquals(listOf("p", "a", "ex:1"), FeedPlanner.scheduleOrder(tiles, ms("2026-03-02"), false).map { it.key })
        assertEquals(listOf("b", "a"), FeedPlanner.scheduleOrder(tiles, null, true).map { it.key })
        val swaps = FeedPlanner.proposeSwaps(listOf("a", "b"), tiles.filter { it.planned })
        assertEquals(2, swaps.size)
        assertEquals(ms("2026-03-05"), swaps.first { it.key == "a" }.newMillis)
        assertTrue(FeedPlanner.proposeSwaps(listOf("b", "a"), tiles.filter { it.planned }).isEmpty())
    }

    @Test fun calendarGridAndMove() {
        val g = Cal.monthGrid(2026, 10)
        assertTrue(g.all { it.size == 7 }); assertEquals(java.time.DayOfWeek.MONDAY, g[0][0].dayOfWeek)
        val moved = Cal.moveToDate(ms("2026-03-05", 14), LocalDate.parse("2026-03-09"), zone)
        assertEquals(ms("2026-03-09", 14), moved)
    }

    @Test fun csvImportMappingAndRows() {
        val t = Csv.parse("Post Title,Platform,Date,Views,Saves,Likes\nHello,INSTAGRAM,2026-03-04,\"1,200\",,x\n")
        val m = CsvImport.guessMapping(t[0])
        assertEquals(ImportField.TITLE, m[0]); assertEquals(ImportField.SAVES, m[4])
        val rows = CsvImport.rows(t, m, true, 0)
        assertEquals(1200.0, rows[0].values[Metric.VIEWS]); assertFalse(rows[0].values.containsKey(Metric.SAVES))
        assertFalse(rows[0].valid) // "x" is not a number
    }
}
