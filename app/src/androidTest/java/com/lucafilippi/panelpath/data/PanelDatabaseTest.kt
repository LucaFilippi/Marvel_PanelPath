package com.lucafilippi.panelpath.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PanelDatabaseTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: PanelDatabase? = null
    @After fun close() { database?.close() }

    @Test fun migrationV2ToV3PreservesIssueAndSeriesAndAddsDefaults() {
        val name = "migration-v2-v3.db"
        context.deleteDatabase(name)
        val old = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        old.execSQL("CREATE TABLE IF NOT EXISTS `issues` (`id` TEXT NOT NULL, `source` TEXT NOT NULL, `metronId` INTEGER, `title` TEXT NOT NULL, `series` TEXT NOT NULL, `number` TEXT NOT NULL, `year` INTEGER, `image` TEXT, `description` TEXT, `publisher` TEXT, `status` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, `completedAt` INTEGER, `modifiedAt` INTEGER NOT NULL, `inLibrary` INTEGER NOT NULL DEFAULT 1, `rating` INTEGER, `ratingUpdatedAt` INTEGER, `metronSeriesId` INTEGER, PRIMARY KEY(`id`))")
        old.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_issues_metronId` ON `issues` (`metronId`)")
        old.execSQL("CREATE TABLE IF NOT EXISTS `saved_series` (`id` TEXT NOT NULL, `metronId` INTEGER NOT NULL, `title` TEXT NOT NULL, `publisher` TEXT, `year` INTEGER, `image` TEXT, `description` TEXT, `issueCount` INTEGER, `savedAt` INTEGER NOT NULL, `inLibrary` INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`id`))")
        old.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_series_metronId` ON `saved_series` (`metronId`)")
        old.execSQL("CREATE TABLE IF NOT EXISTS `sagas` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `cover` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        old.execSQL("CREATE TABLE IF NOT EXISTS `saga_entries` (`id` TEXT NOT NULL, `sagaId` TEXT NOT NULL, `type` TEXT NOT NULL, `seriesId` TEXT, `issueId` TEXT, `position` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`sagaId`) REFERENCES `sagas`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`seriesId`) REFERENCES `saved_series`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`issueId`) REFERENCES `issues`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
        old.execSQL("CREATE INDEX IF NOT EXISTS `index_saga_entries_sagaId` ON `saga_entries` (`sagaId`)")
        old.execSQL("CREATE INDEX IF NOT EXISTS `index_saga_entries_seriesId` ON `saga_entries` (`seriesId`)")
        old.execSQL("CREATE INDEX IF NOT EXISTS `index_saga_entries_issueId` ON `saga_entries` (`issueId`)")
        old.execSQL("CREATE TABLE IF NOT EXISTS `saga_tracked_issues` (`id` TEXT NOT NULL, `entryId` TEXT NOT NULL, `issueId` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`entryId`) REFERENCES `saga_entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`issueId`) REFERENCES `issues`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )")
        old.execSQL("CREATE INDEX IF NOT EXISTS `index_saga_tracked_issues_entryId` ON `saga_tracked_issues` (`entryId`)")
        old.execSQL("CREATE INDEX IF NOT EXISTS `index_saga_tracked_issues_issueId` ON `saga_tracked_issues` (`issueId`)")
        old.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_saga_tracked_issues_entryId_issueId` ON `saga_tracked_issues` (`entryId`, `issueId`)")
        old.execSQL("INSERT INTO issues VALUES ('kept','MANUAL',NULL,'Kept','','',NULL,NULL,NULL,NULL,'COMPLETED',1,2,3,1,4,4,NULL)")
        old.execSQL("INSERT INTO saved_series VALUES ('series',5,'Series',NULL,NULL,NULL,NULL,1,1,1)")
        old.version = 2; old.close()
        database = Room.databaseBuilder(context, PanelDatabase::class.java, name).addMigrations(PanelDatabase.MIGRATION_2_3).build()
        val issue = runBlocking { database!!.issues().byId("kept")!! }
        assertTrue(issue.queued.not()); assertEquals(-1, issue.queuePosition)
        assertEquals(ReadingStatus.COMPLETED, issue.status); assertEquals(4, issue.rating)
        runBlocking { assertFalse(database!!.sagas().seriesById("series")!!.favorite) }
        database!!.close(); database = null; context.deleteDatabase(name)
    }

    @Test fun migrationPreservesV1ReadingHistory() = runBlocking {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val old = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        old.execSQL("CREATE TABLE IF NOT EXISTS issues (id TEXT NOT NULL PRIMARY KEY, source TEXT NOT NULL, metronId INTEGER, title TEXT NOT NULL, series TEXT NOT NULL, number TEXT NOT NULL, year INTEGER, image TEXT, description TEXT, publisher TEXT, status TEXT NOT NULL, addedAt INTEGER NOT NULL, completedAt INTEGER, modifiedAt INTEGER NOT NULL)")
        old.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_issues_metronId ON issues (metronId)")
        old.execSQL("INSERT INTO issues (id,source,metronId,title,series,number,year,image,description,publisher,status,addedAt,completedAt,modifiedAt) VALUES ('old','METRON',42,'Civil War #1','Civil War','1',2006,NULL,NULL,'Marvel','COMPLETED',100,200,300)")
        old.version = 1
        old.close()
        database = Room.databaseBuilder(context, PanelDatabase::class.java, name)
            .addMigrations(PanelDatabase.MIGRATION_1_2, PanelDatabase.MIGRATION_2_3).build()
        val issue = database!!.issues().byId("old")
        assertNotNull(issue)
        assertEquals(ReadingStatus.COMPLETED, issue!!.status)
        assertEquals(200L, issue.completedAt)
        assertTrue(issue.inLibrary)
        assertNull(issue.rating)
        assertNull(issue.metronSeriesId)
        assertEquals(42, issue.metronId)
        database!!.close(); database = null
        context.deleteDatabase(name)
        Unit
    }

    @Test fun sagaSeriesAndRatingsShareCanonicalIssue() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, PanelDatabase::class.java).build()
        val db = database!!
        val sagas = SagaRepository(db)
        val library = LibraryRepository(db.issues())
        val dto = SeriesDto(id = 8, series = "Civil War", yearBegan = 2006, publisher = PublisherDto(1, "Marvel"), issueCount = 2)
        val series = sagas.saveSeries(dto)
        assertEquals(series.id, sagas.saveSeries(dto).id)
        val first = IssueDto(id = 101, series = IssueSeriesDto(8, "Civil War"), number = "1", issue = "Civil War #1")
        val second = IssueDto(id = 102, series = IssueSeriesDto(8, "Civil War"), number = "2", issue = "Civil War #2")
        val sagaA = sagas.createSaga("Saga MCU", "Personal order")
        val sagaB = sagas.createSaga("Marvel Essentials", "")
        sagas.addSeries(sagaA.id, dto, listOf(first, second))
        val canonical = db.issues().byMetronId(101)!!
        assertFalse(sagas.addIssue(sagaA.id, canonical))
        assertTrue(sagas.addIssue(sagaB.id, canonical))
        library.setStatus(canonical, ReadingStatus.COMPLETED)
        library.setRating(canonical, 0)
        val updated = db.issues().byMetronId(101)!!
        assertEquals(0, updated.rating)
        assertEquals(ReadingStatus.COMPLETED, updated.status)
        val entries = db.sagas().observeEntries().first()
        val tracked = db.sagas().observeTracked().first()
        val issues = db.issues().observeAll().first()
        assertEquals(50, sagaProgress(entries.filter { it.sagaId == sagaA.id }, tracked, issues).percent)
        assertEquals(100, sagaProgress(entries.filter { it.sagaId == sagaB.id }, tracked, issues).percent)
        sagas.removeSeries(series)
        assertFalse(db.sagas().seriesById(series.id)!!.inLibrary)
        assertEquals(0, db.issues().byMetronId(101)!!.rating)
        library.setRating(updated, null)
        assertNull(db.issues().byMetronId(101)!!.rating)
        sagas.deleteSaga(sagaA)
        assertNotNull(db.issues().byMetronId(101))
    }

    @Test fun sagaRenameAndEntryOrderPersist() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, PanelDatabase::class.java).build()
        val db = database!!; val repo = SagaRepository(db)
        val saga = repo.createSaga("Original", "")
        repo.renameSaga(saga, "Renamed", "Description")
        assertEquals("Renamed", db.sagas().sagaById(saga.id)?.name)
        val a = SavedIssue(title = "A"); val b = SavedIssue(title = "B")
        db.issues().insert(a); db.issues().insert(b)
        assertTrue(repo.addIssue(saga.id, a))
        assertTrue(repo.addIssue(saga.id, b))
        val second = db.sagas().entriesFor(saga.id)[1]
        repo.moveEntry(second, -1)
        assertEquals(b.id, db.sagas().entriesFor(saga.id).first().issueId)
    }
}
