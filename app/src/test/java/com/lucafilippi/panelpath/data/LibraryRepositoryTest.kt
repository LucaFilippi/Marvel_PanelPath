package com.lucafilippi.panelpath.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryRepositoryTest {
    private class FakeDao : IssueDao {
        val state = MutableStateFlow<List<SavedIssue>>(emptyList())
        override suspend fun queuedIssues() = state.value.filter { it.queued }.sortedBy { it.queuePosition }
        override fun observeAll(): Flow<List<SavedIssue>> = state
        override suspend fun byId(id: String) = state.value.find { it.id == id }
        override suspend fun byMetronId(id: Int) = state.value.find { it.metronId == id }
        override suspend fun insert(issue: SavedIssue): Long {
            if (issue.metronId != null && byMetronId(issue.metronId) != null) return -1
            state.value += issue
            return state.value.size.toLong()
        }
        override suspend fun update(issue: SavedIssue) { state.value = state.value.map { if (it.id == issue.id) issue else it } }
        override suspend fun delete(issue: SavedIssue) { state.value = state.value.filterNot { it.id == issue.id } }
        override suspend fun setInLibrary(id: String, saved: Boolean, now: Long) { state.value = state.value.map { if (it.id == id) it.copy(inLibrary = saved, modifiedAt = now) else it } }
        override suspend fun setQueued(id: String, queued: Boolean, position: Int, now: Long) { state.value = state.value.map { if (it.id == id) it.copy(queued = queued, queuePosition = position, modifiedAt = now) else it } }
        override suspend fun setQueuePosition(id: String, position: Int) { state.value = state.value.map { if (it.id == id) it.copy(queuePosition = position) else it } }
        override suspend fun complete(id: String, now: Long) { state.value = state.value.map { if (it.id == id) it.withStatus(ReadingStatus.COMPLETED, now) else it } }
        override suspend fun setUncompleted(id: String, status: ReadingStatus, now: Long) { state.value = state.value.map { if (it.id == id) it.withStatus(status, now) else it } }
        override suspend fun setRating(id: String, rating: Int?, ratedAt: Long?, now: Long) { state.value = state.value.map { if (it.id == id) it.copy(rating = rating, ratingUpdatedAt = ratedAt, modifiedAt = now) else it } }
        override suspend fun fillRemoteMetadata(id: String, seriesId: Int?, image: String?, description: String?) { state.value = state.value.map { if (it.id == id) it.copy(metronSeriesId = it.metronSeriesId ?: seriesId, image = it.image ?: image, description = it.description ?: description) else it } }
    }

    @Test fun manualIssueNeedsNoMetronId() = runBlocking {
        val dao = FakeDao(); val repo = LibraryRepository(dao)
        repo.add(SavedIssue(title = "A comic"))
        assertEquals(SourceType.MANUAL, dao.state.value.single().source)
        assertNull(dao.state.value.single().metronId)
    }

    @Test fun duplicateMetronIssueIsIgnored() = runBlocking {
        val dao = FakeDao(); val repo = LibraryRepository(dao)
        repo.add(SavedIssue(source = SourceType.METRON, metronId = 42, title = "First"))
        assertEquals(-1L, repo.add(SavedIssue(source = SourceType.METRON, metronId = 42, title = "Duplicate")))
        assertEquals(1, dao.state.value.size)
    }

    @Test fun completionDateIsSetAndCleared() {
        val issue = SavedIssue(title = "Test")
        val completed = issue.withStatus(ReadingStatus.COMPLETED, 1234L)
        assertEquals(1234L, completed.completedAt)
        assertEquals(1234L, completed.withStatus(ReadingStatus.COMPLETED, 2345L).completedAt)
        assertNull(completed.withStatus(ReadingStatus.READING, 3456L).completedAt)
    }

    @Test fun zeroStarsDiffersFromNoRatingAndClearingRestoresNull() = runBlocking {
        val dao = FakeDao(); val repo = LibraryRepository(dao)
        val issue = SavedIssue(title = "Rated")
        repo.add(issue)
        assertNull(dao.state.value.single().rating)
        repo.setRating(issue, 0)
        assertEquals(0, dao.state.value.single().rating)
        repo.setRating(issue, 5)
        assertEquals(5, dao.state.value.single().rating)
        repo.setRating(issue, null)
        assertNull(dao.state.value.single().rating)
    }

    @Test fun removingIssueFromLibraryKeepsReadingHistoryAndRating() = runBlocking {
        val dao = FakeDao(); val repo = LibraryRepository(dao)
        val issue = SavedIssue(title = "History", status = ReadingStatus.COMPLETED, rating = 4)
        repo.add(issue)
        repo.remove(issue)
        val retained = dao.state.value.single()
        assertFalse(retained.inLibrary)
        assertEquals(ReadingStatus.COMPLETED, retained.status)
        assertEquals(4, retained.rating)
    }

    @Test fun queueIsIdempotentAndCompletionRemovesIssue() = runBlocking {
        val dao = FakeDao(); val repo = LibraryRepository(dao)
        val issue = SavedIssue(id = "queued", title = "Queued")
        repo.add(issue); repo.queue(issue); repo.queue(issue)
        assertTrue(dao.state.value.single().queued)
        assertEquals(1, dao.state.value.size)
        repo.setStatus(issue, ReadingStatus.COMPLETED)
        assertFalse(dao.state.value.single().queued)
        assertEquals(ReadingStatus.COMPLETED, dao.state.value.single().status)
    }
}
