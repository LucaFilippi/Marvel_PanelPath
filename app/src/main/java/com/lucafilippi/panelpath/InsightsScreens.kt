package com.lucafilippi.panelpath

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucafilippi.panelpath.data.*
import java.util.Locale

private fun Double.stars() = String.format(Locale.getDefault(), "%.1f", this)

@Composable internal fun SeriesRatingsScreen(vm: PanelViewModel, onIssue: (String) -> Unit, onSettings: () -> Unit) {
    val issues by vm.issues.collectAsStateWithLifecycle()
    val savedSeries by vm.savedSeries.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("Rated") }
    var sort by rememberSaveable { mutableStateOf("Highest Rated") }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val groups = remember(issues, savedSeries) { seriesRatings(issues, savedSeries) }
    val shown = remember(groups, filter, sort) {
        groups.filter { group -> when (filter) {
            "Rated" -> group.rated.isNotEmpty()
            "Not Rated" -> group.issues.any { it.rating == null }
            else -> true
        } }.let { list -> when (sort) {
            "Highest Rated" -> list.sortedWith(compareByDescending<SeriesRating> { it.average }.thenBy { it.title })
            "Lowest Rated" -> list.sortedWith(compareBy<SeriesRating> { it.average ?: Double.MAX_VALUE }.thenBy { it.title })
            "Most Recent" -> list.sortedByDescending { it.latestRating }
            "Alphabetical" -> list.sortedBy { it.title.lowercase() }
            else -> list.sortedWith(compareBy<SeriesRating> { it.year ?: Int.MAX_VALUE }.thenBy { it.title })
        } }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PanelHeader("My Ratings", "Your stories, your scores.", onSettings)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Rated", "Not Rated", "All").forEach { option ->
                FilterChip(filter == option, onClick = { filter = option }, label = { Text(option) })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Highest Rated", "Lowest Rated", "Most Recent", "Alphabetical", "Publication Year").forEach { option ->
                FilterChip(sort == option, onClick = { sort = option }, label = { Text(option) })
            }
        }
        if (shown.isEmpty()) Text(if (filter == "Rated") "Rate your first comic to build your collection." else "No series match this filter.", color = PanelPalette.muted, modifier = Modifier.padding(top = 24.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(shown, key = { it.key }) { group ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = PanelPalette.surface)) {
                    Column {
                        Row(Modifier.fillMaxWidth().clickable { expanded[group.key] = expanded[group.key] != true }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CoverArt(group.cover, group.title, Modifier.size(58.dp, 78.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(group.title + (group.year?.let { " ($it)" } ?: ""), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(group.average?.let { "Average ${it.stars()} / 5" } ?: "Not Rated", color = PanelPalette.accent)
                                Text("${group.rated.size} of ${group.knownCount ?: group.issues.size} issues rated · ${if (group.knownCount != null && group.completed >= group.knownCount) "Completed" else "${group.completed} read"}", color = PanelPalette.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(if (expanded[group.key] == true) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (expanded[group.key] == true) "Collapse" else "Expand")
                        }
                        AnimatedVisibility(expanded[group.key] == true) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                val rows = if (filter == "Not Rated") group.issues.filter { it.rating == null } else group.issues
                                rows.forEach { issue ->
                                    HorizontalDivider(color = PanelPalette.border)
                                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                        CoverArt(issue.image, issue.title, Modifier.size(34.dp, 45.dp))
                                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                            Text("#${issue.number.ifBlank { "?" }} · ${issue.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${issue.status.label()} · ${issue.rating?.let { "$it / 5" } ?: "Not Rated"}", color = PanelPalette.muted, style = MaterialTheme.typography.bodySmall)
                                        }
                                        TextButton(onClick = { onIssue(issue.id) }) { Text("Open") }
                                    }
                                    RatingPicker(issue.rating) { vm.rate(issue, it) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun DashboardScreen(vm: PanelViewModel, onSeries: (Int) -> Unit, onIssue: (String) -> Unit, onSettings: () -> Unit) {
    val issues by vm.issues.collectAsStateWithLifecycle()
    val series by vm.savedSeries.collectAsStateWithLifecycle()
    val sagas by vm.sagas.collectAsStateWithLifecycle()
    val entries by vm.sagaEntries.collectAsStateWithLifecycle()
    val tracked by vm.trackedIssues.collectAsStateWithLifecycle()
    val data = remember(issues, series, sagas, entries, tracked) { readingSnapshot(issues, series, sagas, entries, tracked) }
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var requireThree by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PanelHeader("Dashboard", "Your reading journey, in numbers.", onSettings) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("Issues Read", data.issuesRead.toString(), Modifier.weight(1f))
                    Metric("Library Series", data.librarySeries.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("Average Rating", data.averageRating?.let { "${it.stars()} / 5" } ?: "—", Modifier.weight(1f))
                    Metric("Completed Sagas", data.completedSagas.toString(), Modifier.weight(1f))
                }
                Text("${data.ratedIssues} rated · ${data.readingIssues} reading · ${data.activeSagas} active sagas", color = PanelPalette.muted)
                if (data.libraryIssues > 0) Text("Library issue completion: ${issues.count { it.inLibrary && it.status == ReadingStatus.COMPLETED } * 100 / data.libraryIssues}%", color = PanelPalette.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (data.issuesRead == 0) item { Text("Start reading to see your journey take shape.", color = PanelPalette.muted) }
        item {
            InsightCard("Average rating by publication year") {
                if (data.byYear.isEmpty()) Text("Rate an issue with a publication year to see this chart.", color = PanelPalette.muted)
                else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                        data.byYear.forEach { point ->
                            Column(Modifier.width(54.dp).clickable { selectedYear = point.year }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(point.average.stars(), style = MaterialTheme.typography.labelSmall)
                                Box(Modifier.width(28.dp).height((point.average * 26).dp).background(PanelPalette.accent, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
                                Text(point.year.toString(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Text("Scale: 0–5 stars", color = PanelPalette.muted, style = MaterialTheme.typography.bodySmall)
                    data.byYear.find { it.year == selectedYear }?.let { Text("${it.year}: ${it.average.stars()} / 5 from ${it.count} issues") }
                }
            }
        }
        item {
            InsightCard("Rating distribution · ${data.ratedIssues} issues") {
                if (data.ratedIssues == 0) Text("Rate your first comic to unlock rating insights.", color = PanelPalette.muted)
                else (0..5).forEach { rating ->
                    val count = data.distribution[rating] ?: 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$rating ★", Modifier.width(42.dp))
                        PanelProgress(count.toFloat() / data.ratedIssues, Modifier.weight(1f))
                        Text(" $count", Modifier.width(35.dp))
                    }
                }
            }
        }
        item {
            InsightCard("Top rated series") {
                FilterChip(requireThree, onClick = { requireThree = !requireThree }, label = { Text("At least 3 rated issues") })
                val ranked = data.topSeries.filter { !requireThree || it.rated.size >= 3 }
                if (ranked.isEmpty()) Text(if (requireThree) "No series have three rated issues yet." else "Rate issues to discover your favorite series.", color = PanelPalette.muted)
                else ranked.forEachIndexed { index, group ->
                    Row(Modifier.fillMaxWidth().clickable { group.metronSeriesId?.let(onSeries) ?: group.issues.firstOrNull()?.id?.let(onIssue) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}.", Modifier.width(25.dp))
                        CoverArt(group.cover, group.title, Modifier.size(36.dp, 48.dp))
                        Text(group.title, Modifier.weight(1f).padding(start = 9.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${group.average?.stars()} ★ · ${group.rated.size}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            InsightCard("Favorite publication eras") {
                if (data.byDecade.isEmpty()) Text("Publication eras appear after rating dated issues.", color = PanelPalette.muted)
                else data.byDecade.forEach { era -> Text("${era.decade}s  ·  ${era.average.stars()} / 5  ·  ${era.count} rated") }
            }
        }
        item {
            InsightCard("Reading activity") {
                if (data.byMonth.isEmpty()) Text("Monthly activity appears as you complete issues. Existing records without dates cannot be placed on a timeline.", color = PanelPalette.muted)
                else {
                    var running = 0
                    data.byMonth.forEach { month ->
                        running += month.count
                        Text("${month.year}-${month.month.toString().padStart(2, '0')}  ·  ${month.count} read  ·  $running total")
                    }
                }
                if (data.missingCompletionDates > 0) Text("${data.missingCompletionDates} older completed issues have no completion date.", color = PanelPalette.muted)
                val recent = issues.filter { it.status == ReadingStatus.COMPLETED && it.completedAt != null }
                    .sortedByDescending { it.completedAt }.take(5)
                if (recent.isNotEmpty()) {
                    Text("Recently read", fontWeight = FontWeight.Bold)
                    recent.forEach { Text("#${it.number.ifBlank { "?" }} ${it.series.ifBlank { it.title }}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = PanelPalette.surface), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = PanelPalette.accent)
            Text(label, style = MaterialTheme.typography.bodySmall, color = PanelPalette.muted)
        }
    }
}

@Composable private fun InsightCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = PanelPalette.surface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
