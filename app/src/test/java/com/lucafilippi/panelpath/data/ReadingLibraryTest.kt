package com.lucafilippi.panelpath.data

import org.junit.Assert.*
import org.junit.Test

class ReadingLibraryTest {
    @Test fun historyKeepsUnknownDatesSeparateAndFiltersRealDates() {
        val old = SavedIssue(id = "old", title = "Old", series = "Café", status = ReadingStatus.COMPLETED)
        val recent = old.copy(id = "recent", completedAt = 200L)
        assertEquals(listOf("recent", "old"), readingHistory(listOf(old, recent), "cafe").map { it.id })
        assertEquals(listOf("recent"), readingHistory(listOf(old, recent), from = 100L).map { it.id })
        assertTrue(readingHistory(listOf(recent.copy(status = ReadingStatus.WANT_TO_READ))).isEmpty())
    }

    @Test fun nextIssueFollowsCanonicalNumberOrderAndKeepsSkippedIssueUnread() {
        val series = SavedSeries(metronId = 1, title = "Alpha", issueCount = 4)
        val issues = listOf(
            SavedIssue(id = "one", title = "One", metronSeriesId = 1, number = "1", status = ReadingStatus.COMPLETED),
            SavedIssue(id = "three", title = "Three", metronSeriesId = 1, number = "3", status = ReadingStatus.COMPLETED),
            SavedIssue(id = "two", title = "Two", metronSeriesId = 1, number = "2"))
        assertEquals("2", seriesReadingProgress(series, issues).nextIssue?.number)
        assertEquals("Alpha", filterLibrarySeries(listOf(series), issues, LibraryFilter.READING, "alp").single().series.title)
        assertTrue(filterLibrarySeries(listOf(series), issues, LibraryFilter.COMPLETED, "").isEmpty())
        assertEquals(1, continueReadingSeries(listOf(series), issues).size)
    }

    @Test fun favoritesAndRatingSortUseSeriesData() {
        val favorite = SavedSeries(metronId = 1, title = "Alpha", favorite = true)
        val rated = SavedIssue(title = "Issue", series = "Beta", rating = 5, inLibrary = true)
        assertTrue(filterLibrarySeries(listOf(favorite), emptyList(), LibraryFilter.FAVORITES, "").single().series.favorite)
        assertEquals(5.0, seriesRatings(listOf(rated), emptyList()).single().average!!, 0.01)
    }
}
