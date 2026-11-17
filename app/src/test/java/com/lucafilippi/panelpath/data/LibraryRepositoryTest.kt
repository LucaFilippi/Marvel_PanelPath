package com.lucafilippi.panelpath.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryRepositoryTest {
    private class FakeDao : IssueDao {
        val state = MutableStateFlow<List<SavedIssue>>(emptyList())
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
}
