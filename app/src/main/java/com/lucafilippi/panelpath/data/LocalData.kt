package com.lucafilippi.panelpath.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

enum class ReadingStatus { WANT_TO_READ, READING, COMPLETED }
enum class SourceType { MANUAL, METRON }

@Entity(tableName = "issues", indices = [Index(value = ["metronId"], unique = true)])
data class SavedIssue(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val source: SourceType = SourceType.MANUAL,
    val metronId: Int? = null,
    val title: String,
    val series: String = "",
    val number: String = "",
    val year: Int? = null,
    val image: String? = null,
    val description: String? = null,
    val publisher: String? = null,
    val status: ReadingStatus = ReadingStatus.WANT_TO_READ,
    val addedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val modifiedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1") val inLibrary: Boolean = true,
    val rating: Int? = null,
    val ratingUpdatedAt: Long? = null,
    val metronSeriesId: Int? = null
)

@Dao interface IssueDao {
    @Query("SELECT * FROM issues ORDER BY modifiedAt DESC") fun observeAll(): Flow<List<SavedIssue>>
    @Query("SELECT * FROM issues WHERE id = :id LIMIT 1") suspend fun byId(id: String): SavedIssue?
    @Query("SELECT * FROM issues WHERE metronId = :id LIMIT 1") suspend fun byMetronId(id: Int): SavedIssue?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(issue: SavedIssue): Long
    @Update suspend fun update(issue: SavedIssue)
    @Delete suspend fun delete(issue: SavedIssue)
    @Query("UPDATE issues SET inLibrary = :saved, modifiedAt = :now WHERE id = :id") suspend fun setInLibrary(id: String, saved: Boolean, now: Long)
    @Query("UPDATE issues SET status = 'COMPLETED', completedAt = COALESCE(completedAt, :now), modifiedAt = :now WHERE id = :id") suspend fun complete(id: String, now: Long)
    @Query("UPDATE issues SET status = :status, completedAt = NULL, modifiedAt = :now WHERE id = :id") suspend fun setUncompleted(id: String, status: ReadingStatus, now: Long)
    @Query("UPDATE issues SET rating = :rating, ratingUpdatedAt = :ratedAt, modifiedAt = :now WHERE id = :id") suspend fun setRating(id: String, rating: Int?, ratedAt: Long?, now: Long)
    @Query("UPDATE issues SET metronSeriesId = COALESCE(metronSeriesId, :seriesId), image = COALESCE(image, :image), description = COALESCE(description, :description) WHERE id = :id")
    suspend fun fillRemoteMetadata(id: String, seriesId: Int?, image: String?, description: String?)
}

class IssueConverters {
    @TypeConverter fun source(value: SourceType) = value.name
    @TypeConverter fun toSource(value: String) = SourceType.valueOf(value)
    @TypeConverter fun status(value: ReadingStatus) = value.name
    @TypeConverter fun toStatus(value: String) = ReadingStatus.valueOf(value)
}

@Database(entities = [SavedIssue::class, SavedSeries::class, Saga::class, SagaEntry::class, SagaTrackedIssue::class], version = 2, exportSchema = true)
@TypeConverters(IssueConverters::class, SagaEntryConverters::class)
abstract class PanelDatabase : RoomDatabase() {
    abstract fun issues(): IssueDao
    abstract fun sagas(): SagaDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE issues ADD COLUMN inLibrary INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE issues ADD COLUMN rating INTEGER")
                db.execSQL("ALTER TABLE issues ADD COLUMN ratingUpdatedAt INTEGER")
                db.execSQL("ALTER TABLE issues ADD COLUMN metronSeriesId INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS saved_series (id TEXT NOT NULL PRIMARY KEY, metronId INTEGER NOT NULL, title TEXT NOT NULL, publisher TEXT, year INTEGER, image TEXT, description TEXT, issueCount INTEGER, savedAt INTEGER NOT NULL, inLibrary INTEGER NOT NULL DEFAULT 1)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_saved_series_metronId ON saved_series (metronId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sagas (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL, cover TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS saga_entries (id TEXT NOT NULL PRIMARY KEY, sagaId TEXT NOT NULL, type TEXT NOT NULL, seriesId TEXT, issueId TEXT, position INTEGER NOT NULL, FOREIGN KEY(sagaId) REFERENCES sagas(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(seriesId) REFERENCES saved_series(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(issueId) REFERENCES issues(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_saga_entries_sagaId ON saga_entries (sagaId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_saga_entries_seriesId ON saga_entries (seriesId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_saga_entries_issueId ON saga_entries (issueId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS saga_tracked_issues (id TEXT NOT NULL PRIMARY KEY, entryId TEXT NOT NULL, issueId TEXT NOT NULL, position INTEGER NOT NULL, FOREIGN KEY(entryId) REFERENCES saga_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(issueId) REFERENCES issues(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_saga_tracked_issues_entryId ON saga_tracked_issues (entryId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_saga_tracked_issues_issueId ON saga_tracked_issues (issueId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_saga_tracked_issues_entryId_issueId ON saga_tracked_issues (entryId, issueId)")
            }
        }
        @Volatile private var instance: PanelDatabase? = null
        fun get(context: Context): PanelDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PanelDatabase::class.java, "panelpath.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}

class LibraryRepository(private val dao: IssueDao) {
    val issues = dao.observeAll()
    suspend fun get(id: String) = dao.byId(id)
    suspend fun findMetron(id: Int) = dao.byMetronId(id)
    suspend fun add(issue: SavedIssue) = dao.insert(issue)
    suspend fun remove(issue: SavedIssue) = dao.setInLibrary(issue.id, false, System.currentTimeMillis())
    suspend fun save(issue: SavedIssue) = dao.setInLibrary(issue.id, true, System.currentTimeMillis())
    suspend fun setStatus(issue: SavedIssue, status: ReadingStatus) {
        val now = System.currentTimeMillis()
        if (status == ReadingStatus.COMPLETED) dao.complete(issue.id, now) else dao.setUncompleted(issue.id, status, now)
    }
    suspend fun setRating(issue: SavedIssue, rating: Int?) {
        require(rating == null || rating in 0..5)
        val now = System.currentTimeMillis()
        dao.setRating(issue.id, rating, if (rating == null) null else now, now)
    }
    suspend fun ensureRemote(dto: IssueDto, inLibrary: Boolean = false): SavedIssue {
        val existing = dao.byMetronId(dto.id)
        if (existing != null) {
            dao.fillRemoteMetadata(existing.id, dto.series?.id, dto.image, dto.desc)
            if (inLibrary && !existing.inLibrary) dao.setInLibrary(existing.id, true, System.currentTimeMillis())
            return dao.byMetronId(dto.id) ?: existing
        }
        val issue = SavedIssue(source = SourceType.METRON, metronId = dto.id,
            title = dto.issue ?: dto.series?.name ?: "Issue #${dto.number.orEmpty()}",
            series = dto.series?.name.orEmpty(), number = dto.number.orEmpty(),
            year = dto.coverDate?.take(4)?.toIntOrNull(), image = dto.image,
            description = dto.desc, publisher = dto.series?.publisher?.name, inLibrary = inLibrary,
            metronSeriesId = dto.series?.id)
        val inserted = dao.insert(issue)
        return if (inserted == -1L) dao.byMetronId(dto.id) ?: issue else issue
    }
}

fun SavedIssue.withStatus(status: ReadingStatus, now: Long): SavedIssue =
    copy(status = status, completedAt = if (status == ReadingStatus.COMPLETED) completedAt ?: now else null, modifiedAt = now)

private val Context.profileStore by preferencesDataStore("profile")
data class LocalProfile(val id: String, val name: String, val createdAt: Long)
class ProfileRepository(private val context: Context) {
    private val idKey = stringPreferencesKey("id")
    private val nameKey = stringPreferencesKey("name")
    private val createdKey = longPreferencesKey("created")
    val profile: Flow<LocalProfile?> = context.profileStore.data.map { p ->
        p[idKey]?.let { LocalProfile(it, p[nameKey].orEmpty(), p[createdKey] ?: 0L) }
    }
    suspend fun create(name: String) { context.profileStore.edit { p ->
        p[idKey] = UUID.randomUUID().toString(); p[nameKey] = name.trim(); p[createdKey] = System.currentTimeMillis()
    } }
    suspend fun rename(name: String) { context.profileStore.edit { it[nameKey] = name.trim() } }
}
