package com.lucafilippi.panelpath

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucafilippi.panelpath.data.*

@Composable internal fun SagaDetailScreen(
    vm: PanelViewModel, sagaId: String, onAdd: () -> Unit, onEdit: () -> Unit,
    onIssue: (String) -> Unit, onSeries: (Int) -> Unit, onDeleted: () -> Unit
) {
    val sagas by vm.sagas.collectAsStateWithLifecycle()
    val allEntries by vm.sagaEntries.collectAsStateWithLifecycle()
    val tracked by vm.trackedIssues.collectAsStateWithLifecycle()
    val issues by vm.issues.collectAsStateWithLifecycle()
    val series by vm.savedSeries.collectAsStateWithLifecycle()
    val saga = sagas.find { it.id == sagaId }
    val entries = remember(allEntries, sagaId) { allEntries.filter { it.sagaId == sagaId }.sortedBy { it.position } }
    val issueById = remember(issues) { issues.associateBy { it.id } }
    val seriesById = remember(series) { series.associateBy { it.id } }
    val trackedByEntry = remember(tracked) { tracked.groupBy { it.entryId }.mapValues { it.value.sortedBy { row -> row.position } } }
    val progress = remember(entries, tracked, issues) { sagaProgress(entries, tracked, issues) }
    val expanded = remember(sagaId) { mutableStateMapOf<String, Boolean>() }
    var menu by remember { mutableStateOf(false) }
    var chooseCover by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(sagaId) { vm.openSaga(sagaId) }
    if (saga == null) { Text("Loading Saga…"); return }
    val next = progress.nextIssueId?.let(issueById::get)
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete Saga?") },
        text = { Text("This removes the Saga and its reading list. Library issues, statuses and ratings stay intact.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteSaga(saga, onDeleted) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    if (chooseCover) AlertDialog(onDismissRequest = { chooseCover = false }, title = { Text("Choose Saga cover") },
        text = { Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            entries.forEach { entry ->
                val parent = entry.seriesId?.let(seriesById::get)
                val issue = entry.issueId?.let(issueById::get)
                val cover = parent?.image ?: issue?.image
                if (cover != null) TextButton(onClick = { vm.setSagaCover(saga, cover); chooseCover = false }) {
                    Text(parent?.title ?: issue?.title ?: "Issue", maxLines = 1)
                }
            }
        } }, confirmButton = { TextButton(onClick = { chooseCover = false }) { Text("Done") } })
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 22.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = PanelPalette.surface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        CoverArt(saga.cover, saga.name, Modifier.size(96.dp, 132.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(saga.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (saga.description.isNotBlank()) Text(saga.description, color = PanelPalette.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            Text("${entries.count { it.type == SagaEntryType.SERIES }} series · ${progress.total} issues", color = PanelPalette.muted)
                            Text("${progress.completed} read · ${progress.percent}%", color = PanelPalette.accent)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Saga actions") }
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Edit Saga") }, onClick = { menu = false; onEdit() })
                                DropdownMenuItem(text = { Text("Change Cover") }, onClick = { menu = false; chooseCover = true })
                                DropdownMenuItem(text = { Text("Add Content") }, onClick = { menu = false; onAdd() })
                                DropdownMenuItem(text = { Text("Delete Saga") }, onClick = { menu = false; confirmDelete = true })
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val animated by animateFloatAsState(progress.percent / 100f, label = "Saga progress")
                    PanelProgress(animated, Modifier.fillMaxWidth())
                }
            }
        }
        item {
            Card(onClick = { next?.let { onIssue(it.id) } ?: if (progress.total == 0) onAdd() else Unit },
                colors = CardDefaults.cardColors(containerColor = PanelPalette.raised), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    val parent = next?.metronSeriesId?.let { id -> series.find { it.metronId == id } }
                    CoverArt(parent?.image ?: next?.image, next?.title ?: saga.name, Modifier.size(55.dp, 70.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(when { progress.total == 0 -> "Start your Saga"; next == null -> "Saga Completed"; progress.completed == 0 -> "Start Reading"; else -> "Continue Reading" }, color = PanelPalette.accent, fontWeight = FontWeight.Bold)
                        Text(next?.let { it.series.ifBlank { it.title } } ?: if (progress.total == 0) "Add a series or issue" else "Every selected issue is complete", maxLines = 2, overflow = TextOverflow.Ellipsis)
                        next?.let { issue ->
                            val matching = entries.firstOrNull { entry -> trackedByEntry[entry.id]?.any { it.issueId == issue.id } == true }
                            val members = matching?.let { trackedByEntry[it.id] }.orEmpty()
                            Text("Issue #${issue.number.ifBlank { "?" }} · ${members.count { issueById[it.issueId]?.status == ReadingStatus.COMPLETED }} / ${members.size} in series", color = PanelPalette.muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (next != null) Text("Open ›", color = PanelPalette.accent)
                }
            }
        }
        item { Text("Reading List", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(entries, key = { it.id }) { entry ->
            val parent = entry.seriesId?.let(seriesById::get)
            val single = entry.issueId?.let(issueById::get)
            val members = trackedByEntry[entry.id].orEmpty()
            val completed = if (single != null) listOf(single).count { it.status == ReadingStatus.COMPLETED } else members.count { issueById[it.issueId]?.status == ReadingStatus.COMPLETED }
            val total = if (single != null) 1 else members.size
            var actions by remember(entry.id) { mutableStateOf(false) }
            Card(colors = CardDefaults.cardColors(containerColor = PanelPalette.surface), shape = RoundedCornerShape(16.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth().clickable { expanded[entry.id] = expanded[entry.id] != true }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverArt(parent?.image ?: single?.image, parent?.title ?: single?.title ?: "Issue", Modifier.size(54.dp, 70.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(parent?.title ?: single?.title ?: "Unavailable entry", fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${parent?.year ?: single?.year ?: ""} · $completed / $total issues read", color = PanelPalette.muted, style = MaterialTheme.typography.bodySmall)
                            PanelProgress(if (total == 0) 0f else completed.toFloat() / total, Modifier.fillMaxWidth().padding(top = 6.dp))
                        }
                        if (total > 0 && completed == total) Text("✓", color = Color(0xFF8EC9A1))
                        Icon(if (expanded[entry.id] == true) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, "Expand issues")
                        Box {
                            IconButton(onClick = { actions = true }) { Icon(Icons.Outlined.MoreVert, "Entry actions") }
                            DropdownMenu(actions, onDismissRequest = { actions = false }) {
                                DropdownMenuItem(text = { Text("Move Up") }, enabled = entry.position > 0, onClick = { actions = false; vm.moveSagaEntry(entry, -1) })
                                DropdownMenuItem(text = { Text("Move Down") }, enabled = entry.position < entries.lastIndex, onClick = { actions = false; vm.moveSagaEntry(entry, 1) })
                                if (parent != null) DropdownMenuItem(text = { Text("Edit Selected Issues") }, onClick = { actions = false; onSeries(parent.metronId) })
                                if ((parent?.image ?: single?.image) != null) DropdownMenuItem(text = { Text("Use as Saga Cover") }, onClick = { actions = false; vm.setSagaCover(saga, parent?.image ?: single?.image) })
                                DropdownMenuItem(text = { Text("Remove from Saga") }, onClick = { actions = false; vm.removeSagaEntry(entry) })
                            }
                        }
                    }
                    AnimatedVisibility(expanded[entry.id] == true) {
                        Column(Modifier.padding(horizontal = 12.dp)) {
                            val rows = if (single != null) listOf(single) else members.mapNotNull { issueById[it.issueId] }
                            rows.forEach { issue ->
                                HorizontalDivider(color = PanelPalette.border)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("#${issue.number.ifBlank { "?" }} ${issue.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${issue.status.label()}${issue.rating?.let { " · $it / 5 ★" } ?: ""}", color = PanelPalette.muted, style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = { vm.setStatus(issue, if (issue.status == ReadingStatus.COMPLETED) ReadingStatus.WANT_TO_READ else ReadingStatus.COMPLETED) }) { Text(if (issue.status == ReadingStatus.COMPLETED) "Unread" else "Read") }
                                    TextButton(onClick = { onIssue(issue.id) }) { Text("Open") }
                                }
                            }
                            if (rows.isEmpty()) Text("No issues selected.", color = PanelPalette.muted)
                        }
                    }
                }
            }
        }
    }
}
