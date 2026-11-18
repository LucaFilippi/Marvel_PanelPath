package com.lucafilippi.panelpath

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucafilippi.panelpath.data.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable internal fun ReadingHistoryScreen(vm: PanelViewModel, onIssue: (String) -> Unit) {
    val issues by vm.issues.collectAsStateWithLifecycle()
    val sagas by vm.sagas.collectAsStateWithLifecycle()
    val entries by vm.sagaEntries.collectAsStateWithLifecycle()
    val tracked by vm.trackedIssues.collectAsStateWithLifecycle()
    var series by remember { mutableStateOf("") }
    var sagaId by remember { mutableStateOf<String?>(null) }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var oldest by remember { mutableStateOf(false) }
    val sagaIds = remember(sagaId, entries, tracked) { sagaOrderedIssueIds(entries.filter { it.sagaId == sagaId }, tracked).toSet() }
    val from = runCatching { LocalDate.parse(start).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
    val until = runCatching { LocalDate.parse(end).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
    val shown = remember(issues, series, sagaId, sagaIds, from, until, oldest) {
        readingHistory(issues, series, if (sagaId == null) null else sagaIds, from, until, oldest)
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Reading History", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(series, { series = it }, label = { Text("Filter series") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(sagaId == null, { sagaId = null }, label = { Text("All sagas") })
            sagas.forEach { saga -> FilterChip(sagaId == saga.id, { sagaId = saga.id }, label = { Text(saga.name) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(start, { start = it }, label = { Text("From YYYY-MM-DD") }, modifier = Modifier.weight(1f), singleLine = true, isError = start.isNotBlank() && from == null)
            OutlinedTextField(end, { end = it }, label = { Text("To YYYY-MM-DD") }, modifier = Modifier.weight(1f), singleLine = true, isError = end.isNotBlank() && until == null)
        }
        Row { TextButton(onClick = { oldest = !oldest }) { Text(if (oldest) "Oldest first" else "Most recent") }
            TextButton(onClick = { series = ""; sagaId = null; start = ""; end = "" }) { Text("Reset filters") } }
        if (shown.isEmpty()) Text("No reading history matches. Complete an issue or reset your filters.", color = PanelPalette.muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val grouped = shown.groupBy { issue -> issue.completedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } }
            grouped.forEach { (date, values) ->
                item(key = "date-$date") { Text(when(date) { null -> "Completion date unknown"; LocalDate.now() -> "Today"; LocalDate.now().minusDays(1) -> "Yesterday"; else -> date.format(DateTimeFormatter.ISO_LOCAL_DATE) }, style = MaterialTheme.typography.titleMedium) }
                items(values, key = { it.id }) { issue ->
                    Column {
                        IssueCard(issue.title, issue.series, issue.number, issue.image, "Completed") { onIssue(issue.id) }
                        val names = sagas.filter { saga -> issue.id in sagaOrderedIssueIds(entries.filter { it.sagaId == saga.id }, tracked) }.joinToString { it.name }
                        if (names.isNotEmpty()) Text("Sagas: $names", color = PanelPalette.muted)
                        IssueQuickActions(vm, issue, onIssue)
                    }
                }
            }
        }
    }
}

@Composable internal fun IssueQuickActions(vm: PanelViewModel, issue: SavedIssue, onIssue: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { menu = true }) { Text("Issue actions") }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text(if (issue.status == ReadingStatus.COMPLETED) "Mark Unread" else "Mark Read") }, onClick = { menu = false; vm.setStatus(issue, if (issue.status == ReadingStatus.COMPLETED) ReadingStatus.WANT_TO_READ else ReadingStatus.COMPLETED) })
            DropdownMenuItem(text = { Text(if (issue.queued) "Remove from Queue" else "Add to Queue") }, enabled = issue.queued || issue.status != ReadingStatus.COMPLETED, onClick = { menu = false; if (issue.queued) vm.unqueueIssue(issue) else vm.queueIssue(issue) })
            DropdownMenuItem(text = { Text("Open Details") }, onClick = { menu = false; onIssue(issue.id) })
        }
    }
}
