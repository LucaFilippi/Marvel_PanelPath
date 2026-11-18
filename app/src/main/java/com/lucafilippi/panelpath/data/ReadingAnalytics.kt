package com.lucafilippi.panelpath.data

import java.util.Calendar

data class SeriesRating(
    val key: String, val title: String, val year: Int?, val cover: String?,
    val metronSeriesId: Int?, val issues: List<SavedIssue>, val knownCount: Int?
) {
    val rated = issues.filter { it.rating != null }
    val average: Double? = rated.takeIf { it.isNotEmpty() }?.map { it.rating!!.toDouble() }?.average()
    val latestRating: Long = rated.mapNotNull { it.ratingUpdatedAt }.maxOrNull() ?: 0L
    val completed: Int = issues.count { it.status == ReadingStatus.COMPLETED }
}

data class RatingYear(val year: Int, val average: Double, val count: Int)
data class RatingDecade(val decade: Int, val average: Double, val count: Int)
data class ReadingMonth(val year: Int, val month: Int, val count: Int)

data class ReadingSnapshot(
    val librarySeries: Int, val libraryIssues: Int, val issuesRead: Int,
    val ratedIssues: Int, val averageRating: Double?, val completedSagas: Int,
    val activeSagas: Int, val readingIssues: Int, val byYear: List<RatingYear>,
    val distribution: Map<Int, Int>, val topSeries: List<SeriesRating>,
    val byDecade: List<RatingDecade>, val byMonth: List<ReadingMonth>,
    val missingCompletionDates: Int
)

fun seriesRatings(issues: List<SavedIssue>, series: List<SavedSeries>): List<SeriesRating> {
    val saved = series.associateBy { it.metronId }
    return issues.distinctBy { it.id }.groupBy { issue ->
        issue.metronSeriesId?.let { "metron:$it" }
            ?: "manual:${issue.series.ifBlank { issue.title }.trim().lowercase()}:${issue.year ?: "unknown"}"
    }.map { (key, members) ->
        val first = members.first()
        val parent = first.metronSeriesId?.let(saved::get)
        SeriesRating(key, parent?.title ?: first.series.ifBlank { first.title }, parent?.year ?: first.year,
            parent?.image ?: members.firstNotNullOfOrNull { it.image }, first.metronSeriesId,
            members.sortedWith(compareBy({ it.number.toDoubleOrNull() ?: Double.MAX_VALUE }, { it.number })),
            parent?.issueCount)
    }
}

fun readingSnapshot(
    issues: List<SavedIssue>, series: List<SavedSeries>, sagas: List<Saga>,
    entries: List<SagaEntry>, tracked: List<SagaTrackedIssue>
): ReadingSnapshot {
    val unique = issues.distinctBy { it.id }
    val library = unique.filter { it.inLibrary }
    val rated = unique.filter { it.rating != null }
    val groups = seriesRatings(unique, series)
    val sagaProgresses = sagas.map { saga -> sagaProgress(entries.filter { it.sagaId == saga.id }, tracked, unique) }
    val years = rated.filter { it.year != null }.groupBy { it.year!! }.map { (year, values) ->
        RatingYear(year, values.map { it.rating!!.toDouble() }.average(), values.size)
    }.sortedBy { it.year }
    val decades = rated.filter { it.year != null }.groupBy { it.year!! / 10 * 10 }.map { (decade, values) ->
        RatingDecade(decade, values.map { it.rating!!.toDouble() }.average(), values.size)
    }.sortedBy { it.decade }
    val dated = unique.filter { it.status == ReadingStatus.COMPLETED && it.completedAt != null }
    val months = dated.groupBy { issue ->
        Calendar.getInstance().apply { timeInMillis = issue.completedAt!! }.let {
            it.get(Calendar.YEAR) to it.get(Calendar.MONTH) + 1
        }
    }.map { (date, values) -> ReadingMonth(date.first, date.second, values.size) }
        .sortedWith(compareBy({ it.year }, { it.month }))
    return ReadingSnapshot(
        librarySeries = (series.filter { it.inLibrary }.map { "metron:${it.metronId}" } +
            seriesRatings(library, series).map { it.key }).toSet().size, libraryIssues = library.size,
        issuesRead = unique.count { it.status == ReadingStatus.COMPLETED }, ratedIssues = rated.size,
        averageRating = rated.takeIf { it.isNotEmpty() }?.map { it.rating!!.toDouble() }?.average(),
        completedSagas = sagaProgresses.count { it.total > 0 && it.completed == it.total },
        activeSagas = sagaProgresses.count { it.total > 0 && it.completed < it.total },
        readingIssues = unique.count { it.status == ReadingStatus.READING }, byYear = years,
        distribution = (0..5).associateWith { star -> rated.count { it.rating == star } },
        topSeries = groups.filter { it.average != null }.sortedWith(compareByDescending<SeriesRating> { it.average }.thenByDescending { it.rated.size }).take(10),
        byDecade = decades, byMonth = months,
        missingCompletionDates = unique.count { it.status == ReadingStatus.COMPLETED && it.completedAt == null }
    )
}
