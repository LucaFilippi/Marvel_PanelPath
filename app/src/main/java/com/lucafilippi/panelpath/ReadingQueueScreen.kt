package com.lucafilippi.panelpath

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucafilippi.panelpath.data.*

@Composable internal fun ReadingQueueScreen(vm: PanelViewModel, onIssue: (String) -> Unit, onSettings: () -> Unit) {
    val all by vm.issues.collectAsStateWithLifecycle()
    val queue = remember(all) { all.filter { it.queued && it.status != ReadingStatus.COMPLETED }.sortedWith(compareBy({ it.queuePosition }, { it.addedAt })) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PanelHeader("Reading Queue", "Your next issues, in order.", onSettings)
        if (queue.isEmpty()) Text("Your queue is empty. Add issues from their details.", color = PanelPalette.muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(queue, key = { it.id }) { issue ->
                Card(colors = CardDefaults.cardColors(containerColor = PanelPalette.surface)) {
                    Column(Modifier.padding(10.dp)) {
                        IssueCard(issue.title, issue.series, issue.number, issue.image, issue.status.label()) { onIssue(issue.id) }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { if (queue.indexOf(issue) > 0) vm.reorderQueue(queue.toMutableList().apply { val i = indexOf(issue); val item = removeAt(i); add(i - 1, item) }) }) { Text("↑") }
                            TextButton(onClick = { if (queue.indexOf(issue) < queue.lastIndex) vm.reorderQueue(queue.toMutableList().apply { val i = indexOf(issue); val item = removeAt(i); add(i + 1, item) }) }) { Text("↓") }
                            TextButton(onClick = { vm.setStatus(issue, ReadingStatus.COMPLETED) }) { Text("Mark Read") }
                            TextButton(onClick = { onIssue(issue.id) }) { Text("Details") }
                            TextButton(onClick = { vm.unqueueIssue(issue) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}
