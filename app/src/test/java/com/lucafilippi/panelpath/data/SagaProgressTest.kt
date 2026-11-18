package com.lucafilippi.panelpath.data

import org.junit.Assert.*
import org.junit.Test

class SagaProgressTest {
    @Test fun emptySagaIsZeroPercent() {
        assertEquals(SagaProgress(0, 0, null), sagaProgress(emptyList(), emptyList(), emptyList()))
        assertEquals(0, sagaProgress(emptyList(), emptyList(), emptyList()).percent)
    }

    @Test fun duplicateIssueCountsOnceAndOrderSelectsNextIncomplete() {
        val a = SavedIssue(id = "a", title = "A", status = ReadingStatus.COMPLETED)
        val b = SavedIssue(id = "b", title = "B")
        val series = SagaEntry(id = "series", sagaId = "s", type = SagaEntryType.SERIES, seriesId = "x", position = 1)
        val single = SagaEntry(id = "single", sagaId = "s", type = SagaEntryType.ISSUE, issueId = "a", position = 0)
        val membership = listOf(SagaTrackedIssue(entryId = "series", issueId = "a", position = 0), SagaTrackedIssue(entryId = "series", issueId = "b", position = 1))
        val progress = sagaProgress(listOf(series, single), membership, listOf(a, b))
        assertEquals(2, progress.total)
        assertEquals(1, progress.completed)
        assertEquals(50, progress.percent)
        assertEquals("b", progress.nextIssueId)
    }

    @Test fun statusChangeIsSharedAcrossSagaCalculations() {
        val entryA = SagaEntry(sagaId = "s1", type = SagaEntryType.ISSUE, issueId = "shared", position = 0)
        val entryB = SagaEntry(sagaId = "s2", type = SagaEntryType.ISSUE, issueId = "shared", position = 0)
        val issue = SavedIssue(id = "shared", title = "Shared")
        assertEquals(0, sagaProgress(listOf(entryA), emptyList(), listOf(issue)).percent)
        val completed = issue.withStatus(ReadingStatus.COMPLETED, 100L)
        assertEquals(100, sagaProgress(listOf(entryA), emptyList(), listOf(completed)).percent)
        assertEquals(100, sagaProgress(listOf(entryB), emptyList(), listOf(completed)).percent)
    }
}
