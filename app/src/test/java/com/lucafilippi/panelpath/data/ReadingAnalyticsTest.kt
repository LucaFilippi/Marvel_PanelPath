package com.lucafilippi.panelpath.data

import org.junit.Assert.*
import org.junit.Test

class ReadingAnalyticsTest {
    @Test fun ratingsUseStableSeriesIdAndIgnoreUnratedIssues() {
        val a = SavedIssue(id = "a", title = "One", series = "Same", metronSeriesId = 1, rating = 0, year = 2005)
        val b = SavedIssue(id = "b", title = "Two", series = "Same", metronSeriesId = 1, rating = 4, year = 2006)
        val c = SavedIssue(id = "c", title = "Three", series = "Same", metronSeriesId = 1)
        val d = SavedIssue(id = "d", title = "Other", series = "Same", metronSeriesId = 2, rating = 5)
        val groups = seriesRatings(listOf(a, b, c, d), emptyList())
        assertEquals(2, groups.size)
        assertEquals(2.0, groups.first { it.metronSeriesId == 1 }.average!!, 0.001)
        assertEquals(2, groups.first { it.metronSeriesId == 1 }.rated.size)
        assertEquals(5.0, groups.first { it.metronSeriesId == 2 }.average!!, 0.001)
        val snapshot = readingSnapshot(listOf(a, b, c, d), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(3, snapshot.ratedIssues)
        assertEquals(1, snapshot.distribution[0])
        assertEquals(1, snapshot.byYear.first { it.year == 2005 }.count)
    }

    @Test fun sharedSagaIssueIsCountedOnceAndIncompleteEmptySagaIsNotCompleted() {
        val issue = SavedIssue(id = "a", title = "One", status = ReadingStatus.COMPLETED, completedAt = 1000L)
        val saga = Saga(id = "s", name = "Story")
        val first = SagaEntry(id = "e1", sagaId = "s", type = SagaEntryType.ISSUE, issueId = "a", position = 0)
        val second = SagaEntry(id = "e2", sagaId = "s", type = SagaEntryType.ISSUE, issueId = "a", position = 1)
        val snapshot = readingSnapshot(listOf(issue, issue), emptyList(), listOf(saga, Saga(id = "empty", name = "Empty")), listOf(first, second), emptyList())
        assertEquals(1, snapshot.issuesRead)
        assertEquals(1, snapshot.completedSagas)
        assertEquals(1, snapshot.byMonth.sumOf { it.count })
    }
}
