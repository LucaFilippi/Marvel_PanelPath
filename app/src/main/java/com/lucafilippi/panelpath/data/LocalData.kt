package com.lucafilippi.panelpath.data

import android.content.Context
import androidx.room.*
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
    val modifiedAt: Long = System.currentTimeMillis()
)

@Dao interface IssueDao {
    @Query("SELECT * FROM issues ORDER BY modifiedAt DESC") fun observeAll(): Flow<List<SavedIssue>>
    @Query("SELECT * FROM issues WHERE id = :id LIMIT 1") suspend fun byId(id: String): SavedIssue?
    @Query("SELECT * FROM issues WHERE metronId = :id LIMIT 1") suspend fun byMetronId(id: Int): SavedIssue?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(issue: SavedIssue): Long
    @Update suspend fun update(issue: SavedIssue)
    @Delete suspend fun delete(issue: SavedIssue)
}

class IssueConverters {
    @TypeConverter fun source(value: SourceType) = value.name
    @TypeConverter fun toSource(value: String) = SourceType.valueOf(value)
    @TypeConverter fun status(value: ReadingStatus) = value.name
    @TypeConverter fun toStatus(value: String) = ReadingStatus.valueOf(value)
}

@Database(entities = [SavedIssue::class], version = 1, exportSchema = false)
@TypeConverters(IssueConverters::class)
abstract class PanelDatabase : RoomDatabase() {
    abstract fun issues(): IssueDao
    companion object {
        @Volatile private var instance: PanelDatabase? = null
        fun get(context: Context): PanelDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, PanelDatabase::class.java, "panelpath.db").build().also { instance = it }
        }
    }
}

class LibraryRepository(private val dao: IssueDao) {
    val issues = dao.observeAll()
    suspend fun get(id: String) = dao.byId(id)
    suspend fun findMetron(id: Int) = dao.byMetronId(id)
    suspend fun add(issue: SavedIssue) = dao.insert(issue)
    suspend fun remove(issue: SavedIssue) = dao.delete(issue)
    suspend fun setStatus(issue: SavedIssue, status: ReadingStatus) {
        dao.update(issue.withStatus(status, System.currentTimeMillis()))
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
