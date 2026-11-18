package com.lucafilippi.panelpath.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "saved_series", indices = [Index(value = ["metronId"], unique = true)])
data class SavedSeries(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val metronId: Int,
    val title: String,
    val publisher: String? = null,
    val year: Int? = null,
    val image: String? = null,
    val description: String? = null,
    val issueCount: Int? = null,
    val savedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1") val inLibrary: Boolean = true,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false
)

@Entity(tableName = "sagas")
data class Saga(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val cover: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class SagaEntryType { SERIES, ISSUE }

@Entity(tableName = "saga_entries", foreignKeys = [
    ForeignKey(entity = Saga::class, parentColumns = ["id"], childColumns = ["sagaId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = SavedSeries::class, parentColumns = ["id"], childColumns = ["seriesId"]),
    ForeignKey(entity = SavedIssue::class, parentColumns = ["id"], childColumns = ["issueId"])
], indices = [Index("sagaId"), Index("seriesId"), Index("issueId")])
data class SagaEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sagaId: String,
    val type: SagaEntryType,
    val seriesId: String? = null,
    val issueId: String? = null,
    val position: Int
)

@Entity(tableName = "saga_tracked_issues", foreignKeys = [
    ForeignKey(entity = SagaEntry::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = SavedIssue::class, parentColumns = ["id"], childColumns = ["issueId"])
], indices = [Index("entryId"), Index("issueId"), Index(value = ["entryId", "issueId"], unique = true)])
data class SagaTrackedIssue(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val entryId: String,
    val issueId: String,
    val position: Int
)

@Dao interface SagaDao {
    @Query("SELECT * FROM saved_series ORDER BY savedAt DESC") fun observeSeries(): Flow<List<SavedSeries>>
    @Query("SELECT * FROM sagas ORDER BY updatedAt DESC") fun observeSagas(): Flow<List<Saga>>
    @Query("SELECT * FROM saga_entries ORDER BY position ASC") fun observeEntries(): Flow<List<SagaEntry>>
    @Query("SELECT * FROM saga_tracked_issues ORDER BY position ASC") fun observeTracked(): Flow<List<SagaTrackedIssue>>
    @Query("SELECT * FROM saved_series WHERE metronId = :metronId LIMIT 1") suspend fun seriesByMetronId(metronId: Int): SavedSeries?
    @Query("SELECT * FROM saved_series WHERE id = :id LIMIT 1") suspend fun seriesById(id: String): SavedSeries?
    @Query("SELECT * FROM sagas WHERE id = :id LIMIT 1") suspend fun sagaById(id: String): Saga?
    @Query("SELECT * FROM saga_entries WHERE sagaId = :sagaId ORDER BY position ASC") suspend fun entriesFor(sagaId: String): List<SagaEntry>
    @Query("SELECT * FROM saga_entries WHERE id = :id LIMIT 1") suspend fun entryById(id: String): SagaEntry?
    @Query("SELECT * FROM saga_tracked_issues WHERE entryId = :entryId ORDER BY position ASC") suspend fun trackedFor(entryId: String): List<SagaTrackedIssue>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSeries(series: SavedSeries): Long
    @Update suspend fun updateSeries(series: SavedSeries)
    @Query("UPDATE saved_series SET favorite = :favorite WHERE metronId = :metronId") suspend fun setFavorite(metronId: Int, favorite: Boolean)
    @Query("UPDATE saved_series SET publisher = COALESCE(publisher, :publisher), year = COALESCE(year, :year), description = COALESCE(description, :description), issueCount = COALESCE(:issueCount, issueCount) WHERE metronId = :metronId")
    suspend fun fillSeriesMetadata(metronId: Int, publisher: String?, year: Int?, description: String?, issueCount: Int?)
    @Query("UPDATE saved_series SET image = COALESCE(image, :image) WHERE metronId = :metronId")
    suspend fun fillSeriesCover(metronId: Int, image: String?)
    @Insert suspend fun insertSaga(saga: Saga)
    @Update suspend fun updateSaga(saga: Saga)
    @Delete suspend fun deleteSaga(saga: Saga)
    @Insert suspend fun insertEntry(entry: SagaEntry)
    @Update suspend fun updateEntry(entry: SagaEntry)
    @Delete suspend fun deleteEntry(entry: SagaEntry)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTracked(issue: SagaTrackedIssue): Long
    @Update suspend fun updateTracked(issue: SagaTrackedIssue)
    @Query("DELETE FROM saga_tracked_issues WHERE entryId = :entryId") suspend fun clearTracked(entryId: String)
    @Query("UPDATE sagas SET updatedAt = :now WHERE id IN (SELECT e.sagaId FROM saga_entries e LEFT JOIN saga_tracked_issues t ON t.entryId = e.id WHERE e.issueId = :issueId OR t.issueId = :issueId)")
    suspend fun touchSagasForIssue(issueId: String, now: Long)
    @Query("UPDATE sagas SET updatedAt = :now WHERE id = :sagaId") suspend fun touchSaga(sagaId: String, now: Long)
}

class SagaEntryConverters {
    @TypeConverter fun type(value: SagaEntryType) = value.name
    @TypeConverter fun toType(value: String) = SagaEntryType.valueOf(value)
}

data class SagaProgress(val completed: Int, val total: Int, val nextIssueId: String?) {
    val percent: Int get() = if (total == 0) 0 else completed * 100 / total
}

fun sagaOrderedIssueIds(entries: List<SagaEntry>, tracked: List<SagaTrackedIssue>): List<String> {
    val ordered = LinkedHashSet<String>()
    entries.sortedBy { it.position }.forEach { entry ->
        if (entry.type == SagaEntryType.ISSUE) entry.issueId?.let(ordered::add)
        else tracked.filter { it.entryId == entry.id }.sortedBy { it.position }.forEach { ordered.add(it.issueId) }
    }
    return ordered.toList()
}

fun sagaProgress(entries: List<SagaEntry>, tracked: List<SagaTrackedIssue>, issues: List<SavedIssue>): SagaProgress {
    val byId = issues.associateBy { it.id }
    val ids = sagaOrderedIssueIds(entries, tracked)
    return SagaProgress(ids.count { byId[it]?.status == ReadingStatus.COMPLETED }, ids.size,
        ids.firstOrNull { byId[it]?.status != ReadingStatus.COMPLETED })
}
