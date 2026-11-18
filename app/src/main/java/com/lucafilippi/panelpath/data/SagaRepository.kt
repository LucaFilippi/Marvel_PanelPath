package com.lucafilippi.panelpath.data

import androidx.room.withTransaction

class SagaRepository(private val db: PanelDatabase) {
    private val dao = db.sagas()
    private val library = LibraryRepository(db.issues())
    val series = dao.observeSeries()
    val sagas = dao.observeSagas()
    val entries = dao.observeEntries()
    val tracked = dao.observeTracked()

    suspend fun saveSeries(dto: SeriesDto, cover: String? = null): SavedSeries = db.withTransaction {
        val existing = dao.seriesByMetronId(dto.id)
        if (existing != null) {
            val updated = existing.copy(inLibrary = true, image = existing.image ?: cover,
                description = existing.description ?: dto.desc, issueCount = dto.issueCount ?: existing.issueCount)
            dao.updateSeries(updated); updated
        } else {
            val series = dto.toSavedSeries(cover, true)
            dao.insertSeries(series); series
        }
    }

    suspend fun ensureSeries(dto: SeriesDto, cover: String? = null): SavedSeries = db.withTransaction {
        dao.seriesByMetronId(dto.id) ?: dto.toSavedSeries(cover, false).also { dao.insertSeries(it) }
    }

    suspend fun removeSeries(series: SavedSeries) = dao.updateSeries(series.copy(inLibrary = false))
    suspend fun fillSeriesMetadata(dto: SeriesDto) = dao.fillSeriesMetadata(dto.id, dto.publisher?.name, dto.yearBegan, dto.desc, dto.issueCount)
    suspend fun fillSeriesCover(metronId: Int, image: String?) = dao.fillSeriesCover(metronId, image)
    suspend fun createSaga(name: String, description: String): Saga {
        require(name.isNotBlank())
        return Saga(name = name.trim(), description = description.trim()).also { dao.insertSaga(it) }
    }
    suspend fun renameSaga(saga: Saga, name: String, description: String) {
        require(name.isNotBlank())
        dao.updateSaga(saga.copy(name = name.trim(), description = description.trim(), updatedAt = System.currentTimeMillis()))
    }
    suspend fun deleteSaga(saga: Saga) = dao.deleteSaga(saga)
    suspend fun setCover(saga: Saga, cover: String?) = dao.updateSaga(saga.copy(cover = cover, updatedAt = System.currentTimeMillis()))
    suspend fun recordIssueActivity(issueId: String) = dao.touchSagasForIssue(issueId, System.currentTimeMillis())
    suspend fun openSaga(sagaId: String) = dao.touchSaga(sagaId, System.currentTimeMillis())

    suspend fun addIssue(sagaId: String, issue: SavedIssue): Boolean = db.withTransaction {
        val entries = dao.entriesFor(sagaId)
        val existingIds = entries.flatMap { entry ->
            if (entry.type == SagaEntryType.ISSUE) listOfNotNull(entry.issueId)
            else dao.trackedFor(entry.id).map { it.issueId }
        }
        if (issue.id in existingIds) return@withTransaction false
        dao.insertEntry(SagaEntry(sagaId = sagaId, type = SagaEntryType.ISSUE,
            issueId = issue.id, position = entries.size))
        touch(sagaId, issue.image)
        true
    }

    suspend fun addRemoteIssue(sagaId: String, dto: IssueDto): Boolean {
        val issue = library.ensureRemote(dto)
        return addIssue(sagaId, issue)
    }

    suspend fun addSeries(sagaId: String, dto: SeriesDto, selectedIssues: List<IssueDto>): SagaEntry = db.withTransaction {
        val series = dao.seriesByMetronId(dto.id) ?: dto.toSavedSeries(selectedIssues.firstOrNull()?.image, false).also { dao.insertSeries(it) }
        val entries = dao.entriesFor(sagaId)
        val entry = entries.firstOrNull { it.seriesId == series.id } ?: SagaEntry(sagaId = sagaId,
            type = SagaEntryType.SERIES, seriesId = series.id, position = entries.size).also { dao.insertEntry(it) }
        dao.clearTracked(entry.id)
        selectedIssues.distinctBy { it.id }.forEachIndexed { index, remote ->
            val canonical = library.ensureRemote(remote)
            dao.insertTracked(SagaTrackedIssue(entryId = entry.id, issueId = canonical.id, position = index))
        }
        touch(sagaId, selectedIssues.firstOrNull()?.image)
        entry
    }

    suspend fun updateSeriesMembership(entry: SagaEntry, selectedIssues: List<IssueDto>) = db.withTransaction {
        require(entry.type == SagaEntryType.SERIES)
        dao.clearTracked(entry.id)
        selectedIssues.distinctBy { it.id }.forEachIndexed { index, remote ->
            val canonical = library.ensureRemote(remote)
            dao.insertTracked(SagaTrackedIssue(entryId = entry.id, issueId = canonical.id, position = index))
        }
        touch(entry.sagaId, selectedIssues.firstOrNull()?.image)
    }

    suspend fun removeEntry(entry: SagaEntry) = db.withTransaction {
        dao.deleteEntry(entry)
        dao.entriesFor(entry.sagaId).forEachIndexed { index, item -> dao.updateEntry(item.copy(position = index)) }
        touch(entry.sagaId)
    }

    suspend fun moveEntry(entry: SagaEntry, direction: Int) = db.withTransaction {
        val ordered = dao.entriesFor(entry.sagaId)
        val index = ordered.indexOfFirst { it.id == entry.id }
        val next = index + direction
        if (index < 0 || next !in ordered.indices) return@withTransaction
        val reordered = ordered.toMutableList().apply { add(next, removeAt(index)) }
        reordered.forEachIndexed { position, item -> dao.updateEntry(item.copy(position = position)) }
        touch(entry.sagaId)
    }

    suspend fun moveTracked(item: SagaTrackedIssue, direction: Int) = db.withTransaction {
        val ordered = dao.trackedFor(item.entryId)
        val index = ordered.indexOfFirst { it.id == item.id }
        val next = index + direction
        if (index < 0 || next !in ordered.indices) return@withTransaction
        val reordered = ordered.toMutableList().apply { add(next, removeAt(index)) }
        reordered.forEachIndexed { position, tracked -> dao.updateTracked(tracked.copy(position = position)) }
        dao.entryById(item.entryId)?.let { touch(it.sagaId) }
    }

    private suspend fun touch(sagaId: String, cover: String? = null) {
        dao.sagaById(sagaId)?.let { saga ->
            dao.updateSaga(saga.copy(cover = saga.cover ?: cover, updatedAt = System.currentTimeMillis()))
        }
    }
}

private fun SeriesDto.toSavedSeries(cover: String?, inLibrary: Boolean) = SavedSeries(
    metronId = id, title = series ?: "Series #$id", publisher = publisher?.name,
    year = yearBegan, image = cover, description = desc, issueCount = issueCount, inLibrary = inLibrary
)
