package com.lucafilippi.panelpath.data

/** Derived Library filters and deterministic next issue selection. */
enum class LibraryFilter { ALL, NOT_STARTED, READING, COMPLETED, FAVORITES }
enum class LibrarySort { TITLE_ASC, TITLE_DESC, RECENTLY_ADDED, RECENTLY_READ, PROGRESS, HIGHEST_RATED }
data class SeriesReadingProgress(val series: SavedSeries, val issues: List<SavedIssue>, val completed: Int, val nextIssue: SavedIssue?)

fun seriesReadingProgress(series: SavedSeries, issues: List<SavedIssue>): SeriesReadingProgress {
    val members = issues.distinctBy { it.id }.filter { it.metronSeriesId == series.metronId }
        .sortedWith(compareBy<SavedIssue>({ it.number.toDoubleOrNull() ?: Double.MAX_VALUE }, { it.number }, { it.addedAt }))
    return SeriesReadingProgress(series, members, members.count { it.status == ReadingStatus.COMPLETED },
        members.firstOrNull { it.status != ReadingStatus.COMPLETED })
}

fun filterLibrarySeries(series: List<SavedSeries>, issues: List<SavedIssue>, filter: LibraryFilter, query: String): List<SeriesReadingProgress> {
    val normalized = query.searchKey()
    return series.filter { it.inLibrary && (normalized.isEmpty() || it.title.searchKey().contains(normalized)) }
        .map { seriesReadingProgress(it, issues) }
        .filter { progress -> when (filter) {
            LibraryFilter.ALL -> true
            LibraryFilter.FAVORITES -> progress.series.favorite
            LibraryFilter.NOT_STARTED -> progress.completed == 0
            LibraryFilter.READING -> progress.completed > 0 &&
                !(progress.series.issueCount != null && progress.series.issueCount > 0 && progress.issues.size >= progress.series.issueCount && progress.completed >= progress.series.issueCount)
            LibraryFilter.COMPLETED -> progress.series.issueCount != null && progress.series.issueCount > 0 &&
                progress.issues.size >= progress.series.issueCount && progress.completed >= progress.series.issueCount
        } }
}

fun sortLibrarySeries(rows: List<SeriesReadingProgress>, sort: LibrarySort): List<SeriesReadingProgress> = when (sort) {
    LibrarySort.TITLE_ASC -> rows.sortedBy { it.series.title.lowercase() }
    LibrarySort.TITLE_DESC -> rows.sortedByDescending { it.series.title.lowercase() }
    LibrarySort.RECENTLY_ADDED -> rows.sortedByDescending { it.series.savedAt }
    LibrarySort.RECENTLY_READ -> rows.sortedByDescending { row -> row.issues.filter { it.status == ReadingStatus.COMPLETED }.maxOfOrNull { it.completedAt ?: 0L } ?: 0L }
    LibrarySort.PROGRESS -> rows.sortedByDescending { row -> if (row.series.issueCount != null && row.series.issueCount > 0) row.completed.toDouble() / row.series.issueCount else 0.0 }
    LibrarySort.HIGHEST_RATED -> rows.sortedWith(compareByDescending<SeriesReadingProgress> { row -> row.issues.filter { it.rating != null }.map { it.rating!!.toDouble() }.takeIf { it.isNotEmpty() }?.average() }.thenBy { it.series.title })
}

fun continueReadingSeries(series: List<SavedSeries>, issues: List<SavedIssue>): List<SeriesReadingProgress> =
    series.filter { it.inLibrary }.map { seriesReadingProgress(it, issues) }
        .filter { row -> row.completed > 0 && row.nextIssue != null && !(row.series.issueCount != null && row.issues.size >= row.series.issueCount && row.completed >= row.series.issueCount) }
        .sortedByDescending { row -> row.issues.filter { it.status == ReadingStatus.COMPLETED }.maxOfOrNull { it.completedAt ?: it.modifiedAt } ?: 0L }

fun String.searchKey(): String = java.text.Normalizer.normalize(trim().lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

fun readingHistory(issues: List<SavedIssue>, series: String = "", sagaIssueIds: Set<String>? = null, from: Long? = null, until: Long? = null, oldest: Boolean = false): List<SavedIssue> {
    val result = issues.distinctBy { it.id }.filter { it.status == ReadingStatus.COMPLETED && it.series.searchKey().contains(series.searchKey()) &&
        (sagaIssueIds == null || it.id in sagaIssueIds) && (from == null || (it.completedAt != null && it.completedAt >= from)) &&
        (until == null || (it.completedAt != null && it.completedAt < until)) }
    return result.sortedWith(compareBy<SavedIssue> { it.completedAt == null }.thenComparator { a, b -> if (oldest) compareValues(a.completedAt, b.completedAt) else compareValues(b.completedAt, a.completedAt) })
}
