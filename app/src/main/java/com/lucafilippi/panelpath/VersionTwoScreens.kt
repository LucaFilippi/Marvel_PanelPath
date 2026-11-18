package com.lucafilippi.panelpath

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lucafilippi.panelpath.data.*

@Composable internal fun LibraryV2Screen(
    vm: PanelViewModel, onManual: () -> Unit, onIssue: (String) -> Unit,
    onSeries: (Int) -> Unit, onSaga: (String) -> Unit, onSettings: () -> Unit
) {
    val issues by vm.issues.collectAsStateWithLifecycle()
    val series by vm.savedSeries.collectAsStateWithLifecycle()
    val sagas by vm.sagas.collectAsStateWithLifecycle()
    val entries by vm.sagaEntries.collectAsStateWithLifecycle()
    val tracked by vm.trackedIssues.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val savedIssues = issues.filter { it.inLibrary }
    val savedSeries = series.filter { it.inLibrary }
    val completed = issues.filter { it.status == ReadingStatus.COMPLETED }
    val activeSaga = sagas.maxByOrNull { it.updatedAt }
    val activeProgress = activeSaga?.let { sagaProgress(entries.filter { e -> e.sagaId == it.id }, tracked, issues) }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        PanelHeader("PanelPath", "Your comic universe.", onSettings)
        LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (tab == 0 && activeSaga != null && activeProgress != null) item(span = { GridItemSpan(maxLineSpan) }) {
                Card(onClick = { onSaga(activeSaga.id) }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = PanelPalette.surface),
                    border = BorderStroke(1.dp, PanelPalette.border)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverArt(activeSaga.cover, activeSaga.name, Modifier.width(112.dp).height(150.dp))
                        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(activeSaga.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text("Continue your journey", color = PanelPalette.muted)
                            Text("${activeProgress.completed} of ${activeProgress.total} issues", color = PanelPalette.muted)
                            PanelProgress(activeProgress.percent / 100f, Modifier.fillMaxWidth())
                            activeProgress.nextIssueId?.let { next ->
                                Button(onClick = { onIssue(next) }, colors = ButtonDefaults.buttonColors(containerColor = PanelPalette.accent), shape = RoundedCornerShape(18.dp)) { Text("Continue  ›") }
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeading("My Library", "${savedIssues.size} issues  ·  ${completed.size} completed")
                    TextButton(onClick = onManual, contentPadding = PaddingValues(0.dp)) { Text("+ Add comic manually", color = PanelPalette.accent) }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "Series", "Issues", "Completed").forEachIndexed { index, label ->
                            FilterChip(tab == index, onClick = { tab = index }, label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PanelPalette.accent, containerColor = PanelPalette.surface, selectedLabelColor = Color.White),
                                shape = RoundedCornerShape(15.dp))
                        }
                    }
                }
            }
            if (tab == 0 || tab == 1) gridItems(savedSeries, key = { "series-${it.id}" }) { item ->
                val done = issues.count { it.metronSeriesId == item.metronId && it.status == ReadingStatus.COMPLETED }
                SeriesLibraryCard(item, done) { onSeries(item.metronId) }
            }
            if (tab == 0 || tab == 2) gridItems(savedIssues, key = { "issue-${it.id}" }) { item ->
                IssueLibraryCard(item) { onIssue(item.id) }
            }
            if (tab == 3) gridItems(completed, key = { "completed-${it.id}" }) { item ->
                IssueLibraryCard(item) { onIssue(item.id) }
            }
            if ((tab == 0 && savedSeries.isEmpty() && savedIssues.isEmpty()) || (tab == 1 && savedSeries.isEmpty()) ||
                (tab == 2 && savedIssues.isEmpty()) || (tab == 3 && completed.isEmpty())) item(span = { GridItemSpan(maxLineSpan) }) {
                Text(when (tab) { 1 -> "No series saved yet. Find one in Search."; 2 -> "No issues saved yet. Add a comic or search Metron."; 3 -> "Completed issues will appear here, including issues finished in Sagas."; else -> "Your library is empty. Add a comic to begin." }, color = PanelPalette.muted, modifier = Modifier.padding(top = 24.dp))
            }
        }
    }
}

@Composable private fun SeriesLibraryCard(series: SavedSeries, completed: Int, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PanelPalette.surface), border = BorderStroke(1.dp, PanelPalette.border)) {
        Column {
            CoverArt(series.image, series.title, Modifier.fillMaxWidth().height(178.dp))
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(series.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (series.issueCount != null) "$completed of ${series.issueCount} issues" else "$completed completed", color = PanelPalette.muted, style = MaterialTheme.typography.bodySmall)
                series.issueCount?.takeIf { it > 0 }?.let { PanelProgress(completed.toFloat() / it, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable private fun IssueLibraryCard(issue: SavedIssue, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PanelPalette.surface), border = BorderStroke(1.dp, PanelPalette.border)) {
        Column {
            CoverArt(issue.image, issue.title, Modifier.fillMaxWidth().height(178.dp))
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(issue.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(issue.status.label(), color = PanelPalette.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable internal fun SearchV2Screen(vm: PanelViewModel, onSeries: (Int) -> Unit, onIssue: (Int) -> Unit, onSettings: () -> Unit) {
    val hasToken by vm.hasToken.collectAsStateWithLifecycle()
    val series by vm.searchResults.collectAsStateWithLifecycle()
    val issues by vm.issueSearchResults.collectAsStateWithLifecycle()
    val seriesNext by vm.searchHasNext.collectAsStateWithLifecycle()
    val issuesNext by vm.issueSearchHasNext.collectAsStateWithLifecycle()
    val savedSeries by vm.savedSeries.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PanelHeader("Discover", "Explore the Marvel universe.", onSettings)
        if (!hasToken) {
            Text("Online search needs your personal Metron token. Your Library and Sagas still work offline.", color = PanelPalette.muted)
            Button(onClick = onSettings, colors = ButtonDefaults.buttonColors(containerColor = PanelPalette.accent)) { Text("Configure token") }
            return@Column
        }
        OutlinedTextField(query, { query = it; vm.search(it) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Search series or issues") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = ""; vm.search("") }) { Icon(Icons.Outlined.Close, "Clear search") } },
            shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PanelPalette.accent,
                unfocusedBorderColor = PanelPalette.border, focusedContainerColor = PanelPalette.surface, unfocusedContainerColor = PanelPalette.surface))
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Series", "Issues").forEachIndexed { index, label ->
                FilterChip(tab == index, onClick = { tab = index }, label = { Text(label) }, shape = RoundedCornerShape(18.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PanelPalette.accent, containerColor = PanelPalette.surface))
            }
        }
        if (busy) LinearProgressIndicator(color = PanelPalette.accent, modifier = Modifier.fillMaxWidth())
        SectionHeading("Search Results", if (query.isBlank()) "Enter a title" else "Matching comics")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp)) {
            if (query.isNotBlank() && (tab == 0 || tab == 1)) items(series, key = { "series-${it.id}" }) { item ->
                val cover = savedSeries.find { it.metronId == item.id }?.image ?: issues.firstOrNull { it.series?.id == item.id }?.image
                SearchResultCard("Series", item.series ?: "Series #${item.id}", item.yearBegan?.toString(), item.publisher?.name, cover,
                    { onSeries(item.id) }, { vm.saveSeries(item, cover) })
            }
            if (query.isNotBlank() && (tab == 0 || tab == 2)) items(issues, key = { "issue-${it.id}" }) { item ->
                SearchResultCard("Issue", item.issue ?: "Issue #${item.number.orEmpty()}", item.coverDate?.take(4), item.series?.publisher?.name, item.image,
                    { onIssue(item.id) }, { vm.saveMetron(item) {} })
            }
            if (tab != 2 && seriesNext) item { TextButton(onClick = vm::loadMoreSeries) { Text("Load more series") } }
            if (tab != 1 && issuesNext) item { TextButton(onClick = vm::loadMoreSearchIssues) { Text("Load more issues") } }
            if (query.isNotBlank() && !busy && (tab != 1 || series.isEmpty()) && (tab != 2 || issues.isEmpty()) && series.isEmpty() && issues.isEmpty()) item {
                Text("No matching comics found.", color = PanelPalette.muted, modifier = Modifier.padding(top = 18.dp))
            }
        }
    }
}

@Composable private fun SearchResultCard(type: String, title: String, year: String?, publisher: String?, cover: String?, onOpen: () -> Unit, onSave: () -> Unit) {
    Card(onClick = onOpen, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PanelPalette.surface)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverArt(cover, title, Modifier.width(84.dp).height(110.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Surface(color = if (type == "Series") PanelPalette.accent.copy(alpha = .45f) else PanelPalette.track, shape = RoundedCornerShape(8.dp)) {
                    Text(type, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                year?.let { Text("($it)", color = PanelPalette.muted) }
                publisher?.let { Text(it, color = PanelPalette.muted, maxLines = 1) }
            }
            IconButton(onClick = onSave) { Icon(Icons.Outlined.BookmarkAdd, contentDescription = "Save $type", tint = Color.White) }
        }
    }
}

@Composable internal fun SettingsV2Screen(vm: PanelViewModel, onBack: () -> Unit) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val connected by vm.hasToken.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showName by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    var name by remember(profile?.name) { mutableStateOf(profile?.name.orEmpty()) }
    var token by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") }
            Spacer(Modifier.width(12.dp))
            Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        Card(colors = CardDefaults.cardColors(containerColor = PanelPalette.surface), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(62.dp).background(PanelPalette.accent, RoundedCornerShape(32.dp)), contentAlignment = Alignment.Center) {
                    Text(profile?.name?.take(1)?.uppercase() ?: "P", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(profile?.name ?: "Reader", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Local reading profile", color = PanelPalette.muted)
                }
                TextButton(onClick = { showName = true }) { Text("Edit Profile", color = PanelPalette.accent) }
            }
        }
        Spacer(Modifier.height(24.dp))
        SettingsTitle("Metron API")
        Card(colors = CardDefaults.cardColors(containerColor = PanelPalette.surface), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (connected) "●  Connected" else "○  Not connected", fontWeight = FontWeight.Bold,
                    color = if (connected) Color(0xFF65DB83) else PanelPalette.muted)
                Text("Your personal API token", color = PanelPalette.muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (connected) "••••••••••••••••" else "No token saved", Modifier.weight(1f).background(PanelPalette.raised, RoundedCornerShape(10.dp)).padding(14.dp))
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { showToken = true }) { Text("Manage", color = PanelPalette.accent) }
                }
                HorizontalDivider(color = PanelPalette.border)
                SettingsRow("Test Connection", onClick = vm::testToken, enabled = connected && !busy)
                Text("♙  Your token is stored securely on this device.", color = PanelPalette.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(24.dp))
        SettingsTitle("Preferences")
        Card(colors = CardDefaults.cardColors(containerColor = PanelPalette.surface), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SettingsRow("Appearance", "Dark", { info = "Appearance" to "PanelPath currently uses the dark theme shown here." })
                HorizontalDivider(color = PanelPalette.border)
                SettingsRow("Comic Library", "Saved locally", { info = "Comic Library" to "Saved series, issues, reading progress, and ratings are available in your Library." })
                HorizontalDivider(color = PanelPalette.border)
                SettingsRow("Data & Storage", "On this device", { info = "Data & Storage" to "Your reading data is stored on this device. Uninstalling the app may remove it." })
            }
        }
        Spacer(Modifier.height(24.dp))
        SettingsTitle("About")
        Card(colors = CardDefaults.cardColors(containerColor = PanelPalette.surface), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SettingsRow("About PanelPath", onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/LucaFilippi/Marvel_PanelPath"))) })
                HorizontalDivider(color = PanelPalette.border)
                SettingsRow("Comic metadata provided by Metron", onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://metron.cloud/docs/"))) })
                HorizontalDivider(color = PanelPalette.border)
                SettingsRow("Version", "0.3.0", { info = "Version" to "PanelPath 0.3.0" })
            }
        }
        Text("Made for comic readers.", Modifier.fillMaxWidth().padding(vertical = 28.dp), color = PanelPalette.muted, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
    if (showName) AlertDialog(onDismissRequest = { showName = false }, title = { Text("Edit Profile") }, text = {
        OutlinedTextField(name, { name = it }, label = { Text("Profile name") }, singleLine = true)
    }, confirmButton = { TextButton(onClick = { vm.rename(name); showName = false }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = { showName = false }) { Text("Cancel") } })
    if (showToken) AlertDialog(onDismissRequest = { showToken = false }, title = { Text("Manage Metron token") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Enter your personal Metron API token to enable online search.")
            OutlinedTextField(token, { token = it }, label = { Text("API token") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            if (connected) TextButton(onClick = { vm.removeToken(); showToken = false }) { Text("Remove token", color = PanelPalette.accent) }
        }
    }, confirmButton = { TextButton(onClick = { vm.validateToken(token) { token = ""; showToken = false } }, enabled = token.isNotBlank() && !busy) { Text("Validate and save") } },
        dismissButton = { TextButton(onClick = { showToken = false }) { Text("Cancel") } })
    info?.let { (title, detail) -> AlertDialog(onDismissRequest = { info = null }, title = { Text(title) }, text = { Text(detail) }, confirmButton = { TextButton(onClick = { info = null }) { Text("OK") } }) }
}

@Composable private fun SettingsTitle(title: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
}

@Composable private fun SettingsRow(label: String, detail: String? = null, onClick: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            detail?.let { Text(it, color = PanelPalette.muted, style = MaterialTheme.typography.bodySmall) }
        }
        if (detail != "0.3.0") Icon(Icons.Outlined.ChevronRight, null, tint = PanelPalette.muted)
    }
}

@Composable internal fun RatingPicker(rating: Int?, onRate: (Int?) -> Unit) {
    Column {
        Text(if (rating == null) "Not rated" else "$rating / 5 stars")
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onRate(0) }) { Text("0 stars") }
            (1..5).forEach { value ->
                IconButton(onClick = { onRate(value) }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Star, contentDescription = "$value stars", tint = if (rating != null && value <= rating) MaterialTheme.colorScheme.primary else Color.Gray)
                }
            }
        }
        if (rating != null) TextButton(onClick = { onRate(null) }) { Text("Clear rating") }
    }
}

@Composable internal fun RatingsScreen(vm: PanelViewModel, onIssue: (String) -> Unit, onSettings: () -> Unit) {
    val all by vm.issues.collectAsStateWithLifecycle()
    var showRated by remember { mutableStateOf(true) }
    var sort by remember { mutableIntStateOf(0) }
    val shown = if (showRated) all.filter { it.rating != null }.let { list ->
        when (sort) { 0 -> list.sortedByDescending { it.rating }; 1 -> list.sortedBy { it.rating }; else -> list.sortedByDescending { it.ratingUpdatedAt } }
    } else all.filter { it.status == ReadingStatus.COMPLETED && it.rating == null }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PanelHeader("My Ratings", "Your stories, your scores.", onSettings)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(showRated, onClick = { showRated = true }, label = { Text("Rated") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PanelPalette.accent, containerColor = PanelPalette.surface), shape = RoundedCornerShape(15.dp))
            FilterChip(!showRated, onClick = { showRated = false }, label = { Text("Not Rated") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PanelPalette.accent, containerColor = PanelPalette.surface), shape = RoundedCornerShape(15.dp))
        }
        if (showRated) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Highest", "Lowest", "Recent").forEachIndexed { index, label ->
                FilterChip(sort == index, onClick = { sort = index }, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PanelPalette.accent, containerColor = PanelPalette.surface), shape = RoundedCornerShape(15.dp))
            }
        }
        if (shown.isEmpty()) Text(if (showRated) "No rated issues yet." else "No completed issues waiting for a rating.", color = PanelPalette.muted, modifier = Modifier.padding(top = 24.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(shown, key = { it.id }) { issue ->
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PanelPalette.surface), border = BorderStroke(1.dp, PanelPalette.border)) {
                    Column(Modifier.padding(12.dp)) {
                        IssueCard(issue.title, issue.series, issue.number, issue.image, issue.status.label()) { onIssue(issue.id) }
                        RatingPicker(issue.rating) { vm.rate(issue, it) }
                    }
                }
            }
        }
    }
}

@Composable internal fun SagaPickerDialog(vm: PanelViewModel, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val sagas by vm.sagas.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose a Saga") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sagas.forEach { saga -> TextButton(onClick = { onPick(saga.id) }) { Text(saga.name) } }
            OutlinedTextField(newName, { newName = it }, label = { Text("Or create a Saga") })
            TextButton(onClick = { vm.createSaga(newName, "") { onPick(it) } }, enabled = newName.isNotBlank()) { Text("Create and choose") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable internal fun SavedDetailsV2Screen(vm: PanelViewModel, id: String, onDone: () -> Unit) {
    val issues by vm.issues.collectAsStateWithLifecycle()
    val issue = issues.find { it.id == id }
    var choosingSaga by remember { mutableStateOf(false) }
    if (issue == null) { Text("Loading issue…"); return }
    if (choosingSaga) SagaPickerDialog(vm, { vm.addIssueToSaga(it, issue); choosingSaga = false }, { choosingSaga = false })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        issue.image?.let { AsyncImage(it, "Cover", Modifier.height(240.dp).fillMaxWidth()) }
        Text(issue.title, style = MaterialTheme.typography.headlineMedium)
        Text(listOf(issue.series, issue.number.takeIf { it.isNotBlank() }?.let { "#$it" }, issue.year?.toString()).filterNotNull().joinToString(" · "))
        issue.description?.let { Text(it) }; issue.publisher?.let { Text("Publisher: $it") }
        Text("Source: ${issue.source.name.lowercase().replaceFirstChar { it.uppercase() }}")
        StatusPicker(issue.status) { vm.setStatus(issue, it) }
        RatingPicker(issue.rating) { vm.rate(issue, it) }
        Button(onClick = { choosingSaga = true }) { Text("Add to Saga") }
        if (issue.inLibrary) OutlinedButton(onClick = { vm.remove(issue, onDone) }) { Text("Remove from Library") }
        else OutlinedButton(onClick = { vm.saveLocalIssue(issue) }) { Text("Save to Library") }
    }
}

@Composable internal fun RemoteIssueV2Screen(vm: PanelViewModel, id: Int, targetSaga: String, onDone: () -> Unit) {
    LaunchedEffect(id) { vm.openIssue(id) }
    val data by vm.selectedIssue.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val issues by vm.issues.collectAsStateWithLifecycle()
    var choosingSaga by remember { mutableStateOf(false) }
    if (choosingSaga && data != null) SagaPickerDialog(vm, { vm.addRemoteIssueToSaga(it, data!!); choosingSaga = false }, { choosingSaga = false })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        data?.let { issue ->
            issue.image?.let { AsyncImage(it, "Cover", Modifier.height(240.dp).fillMaxWidth()) }
            Text(issue.issue ?: issue.series?.name.orEmpty(), style = MaterialTheme.typography.headlineMedium)
            Text("${issue.series?.name.orEmpty()} · #${issue.number.orEmpty()}")
            issue.coverDate?.let { Text("Cover date: $it") }; issue.desc?.let { Text(it) }
            Text("Metadata: Metron Comic Book Database")
            val canonical = issues.find { it.metronId == issue.id }
            if (canonical?.inLibrary != true) Button(onClick = { vm.saveMetron(issue, onDone) }) { Text("Add to Library") }
            else Text("Saved in Library")
            StatusPicker(canonical?.status ?: ReadingStatus.WANT_TO_READ) { vm.setRemoteStatus(issue, it) }
            RatingPicker(canonical?.rating) { vm.rateRemote(issue, it) }
            Button(onClick = { if (targetSaga.isBlank()) choosingSaga = true else vm.addRemoteIssueToSaga(targetSaga, issue) }) { Text("Add to Saga") }
        }
    }
}

@Composable internal fun SeriesV2Screen(vm: PanelViewModel, id: Int, targetSaga: String, onIssue: (Int) -> Unit, onSavedIssue: (String) -> Unit) {
    val hasToken by vm.hasToken.collectAsStateWithLifecycle()
    LaunchedEffect(id, hasToken) { if (hasToken) vm.openSeries(id) else vm.clearSeriesView(id) }
    val remote by vm.selectedSeries.collectAsStateWithLifecycle()
    val remoteIssues by vm.seriesIssues.collectAsStateWithLifecycle()
    val hasNext by vm.issuesHasNext.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val all by vm.issues.collectAsStateWithLifecycle()
    val savedSeries by vm.savedSeries.collectAsStateWithLifecycle()
    val saved = savedSeries.find { it.metronId == id }
    val dto = remote?.takeIf { it.id == id } ?: saved?.let { SeriesDto(it.metronId, it.title, it.year, it.publisher?.let { name -> PublisherDto(name = name) }, it.issueCount, it.description) }
    val localIssues = all.filter { it.metronSeriesId == id }
    var choosingSaga by remember { mutableStateOf(false) }
    var membershipSaga by remember(id, targetSaga) { mutableStateOf(targetSaga.takeIf { it.isNotBlank() }) }
    if (choosingSaga) SagaPickerDialog(vm, { membershipSaga = it; choosingSaga = false }, { choosingSaga = false })
    val selectableIssues = (remoteIssues + localIssues.mapNotNull { item -> item.metronId?.let { externalId ->
        IssueDto(id = externalId, series = IssueSeriesDto(id, item.series), number = item.number,
            issue = item.title, image = item.image, desc = item.description)
    } }).distinctBy { it.id }
    if (membershipSaga != null && dto != null) SeriesMembershipDialog(vm, membershipSaga!!, dto, selectableIssues,
        onDismiss = { membershipSaga = null })
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(dto?.series ?: "Series", style = MaterialTheme.typography.headlineMedium)
        val cover = saved?.image ?: remoteIssues.firstOrNull()?.image
        cover?.let { AsyncImage(it, "Series cover", Modifier.height(180.dp).fillMaxWidth()) }
        dto?.publisher?.name?.let { Text(it) }
        dto?.yearBegan?.let { Text("Started $it") }
        dto?.desc?.let { Text(it, maxLines = 4) }
        val completed = localIssues.count { it.status == ReadingStatus.COMPLETED }
        val total = dto?.issueCount
        val oldUnmapped = all.any { it.metronId != null && it.metronSeriesId == null }
        Text(if (total != null && total > 0 && !oldUnmapped) "$completed / $total issues completed · ${completed * 100 / total}%"
            else if (total != null) "$completed / $total known completed issues${if (oldUnmapped) " · some older records are not identified yet" else ""}"
            else "$completed issues completed · total unknown")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (dto != null && saved?.inLibrary != true) Button(onClick = { vm.saveSeries(dto) }) { Text("Add Series to Library") }
            if (saved?.inLibrary == true) OutlinedButton(onClick = { vm.removeSeries(saved) {} }) { Text("Remove Series") }
            if (dto != null) Button(onClick = { choosingSaga = true }) { Text("Add to Saga") }
        }
        Text("Issues", style = MaterialTheme.typography.titleLarge)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!hasToken) Text("Online issue browsing is unavailable. Showing locally known issues.")
        val visibleRemote = remoteIssues.filter { remoteIssue -> localIssues.none { it.metronId == remoteIssue.id } }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(localIssues, key = { "local-${it.id}" }) { issue -> IssueCard(issue.title, issue.series, issue.number, issue.image, issue.status.label()) { onSavedIssue(issue.id) } }
            items(visibleRemote, key = { "remote-${it.id}" }) { issue -> IssueCard(issue.issue ?: "Issue #${issue.number.orEmpty()}", issue.series?.name.orEmpty(), issue.number.orEmpty(), issue.image, "Browse issue") { onIssue(issue.id) } }
            if (hasNext) item { TextButton(onClick = vm::loadMoreIssues) { Text("Load more issues") } }
            if (localIssues.isEmpty() && remoteIssues.isEmpty() && !busy) item { Text("No issues available yet.") }
        }
    }
}

@Composable private fun SeriesMembershipDialog(vm: PanelViewModel, sagaId: String, series: SeriesDto, loaded: List<IssueDto>, onDismiss: () -> Unit) {
    var mode by remember(sagaId, series.id) { mutableStateOf(MembershipMode.ALL) }
    val selected = remember(sagaId, series.id) { mutableStateListOf<Int>() }
    var rangeStart by remember(sagaId, series.id) { mutableStateOf("") }
    var rangeEnd by remember(sagaId, series.id) { mutableStateOf("") }
    val progress by vm.importProgress.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = { if (progress == null) onDismiss() }, title = { Text("Add ${series.series ?: "series"} to Saga") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == MembershipMode.ALL, onClick = { mode = MembershipMode.ALL }); Text("All available issues") }
            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == MembershipMode.SELECTED, onClick = { mode = MembershipMode.SELECTED }); Text("Select specific issues") }
            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == MembershipMode.RANGE, onClick = { mode = MembershipMode.RANGE }); Text("Numeric issue range") }
            if (mode == MembershipMode.SELECTED) {
                Text("Load more pages on the series screen to select beyond this page.", style = MaterialTheme.typography.bodySmall)
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    loaded.forEach { issue -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(issue.id in selected, onCheckedChange = { checked -> if (checked) selected.add(issue.id) else selected.remove(issue.id) })
                        Text(issue.issue ?: "Issue #${issue.number.orEmpty()}")
                    } }
                }
            }
            if (mode == MembershipMode.RANGE) {
                Text("Loads all series pages, then includes numeric issue numbers in the range.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(rangeStart, { rangeStart = it }, label = { Text("From #") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(rangeEnd, { rangeEnd = it }, label = { Text("To #") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
            }
            progress?.let { Text(it); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
    }, confirmButton = {
        if (progress == null) TextButton(onClick = { vm.addSeriesToSaga(sagaId, series, mode,
            loaded.filter { it.id in selected }, rangeStart.toIntOrNull(), rangeEnd.toIntOrNull(), onDismiss) },
            enabled = mode == MembershipMode.ALL || (mode == MembershipMode.SELECTED && selected.isNotEmpty()) ||
                (mode == MembershipMode.RANGE && rangeStart.toIntOrNull() != null && rangeEnd.toIntOrNull() != null && rangeStart.toInt() <= rangeEnd.toInt())) { Text("Save membership") }
        else TextButton(onClick = vm::cancelImport) { Text("Cancel import") }
    }, dismissButton = { if (progress == null) TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable internal fun SagasScreen(vm: PanelViewModel, onCreate: () -> Unit, onSaga: (String) -> Unit, onSettings: () -> Unit) {
    val sagas by vm.sagas.collectAsStateWithLifecycle()
    val entries by vm.sagaEntries.collectAsStateWithLifecycle()
    val tracked by vm.trackedIssues.collectAsStateWithLifecycle()
    val issues by vm.issues.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PanelHeader("My Sagas", "Every story has a journey.", onSettings)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Your journeys", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("${sagas.size} sagas", color = PanelPalette.muted) }
            Button(onClick = onCreate, colors = ButtonDefaults.buttonColors(containerColor = PanelPalette.accent), shape = RoundedCornerShape(22.dp)) { Text("+ New Saga") }
        }
        if (sagas.isEmpty()) Text("Create a Saga to build your own reading journey.", color = PanelPalette.muted, modifier = Modifier.padding(top = 24.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 14.dp, bottom = 20.dp)) {
            items(sagas, key = { it.id }) { saga ->
                val progress = sagaProgress(entries.filter { it.sagaId == saga.id }, tracked, issues)
                val sagaEntries = entries.filter { it.sagaId == saga.id }
                Card(onClick = { onSaga(saga.id) }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = PanelPalette.surface), border = BorderStroke(1.dp, PanelPalette.border)) {
                    Box(Modifier.fillMaxWidth().height(if (saga == sagas.firstOrNull()) 280.dp else 235.dp)) {
                        CoverArt(saga.cover, saga.name, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xA9000000), PanelPalette.surface), startY = 50f)))
                        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(saga.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("${sagaEntries.count { it.type == SagaEntryType.SERIES }} series · ${sagaEntries.count { it.type == SagaEntryType.ISSUE }} individual issues", color = PanelPalette.muted)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${progress.percent}% completed", fontWeight = FontWeight.Bold)
                                Text("${progress.completed} of ${progress.total} read", color = PanelPalette.muted)
                            }
                            PanelProgress(progress.percent / 100f, Modifier.fillMaxWidth())
                            if (saga == sagas.firstOrNull() && progress.nextIssueId != null) Button(onClick = { onSaga(saga.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = PanelPalette.accent), shape = RoundedCornerShape(20.dp)) { Text("Continue Reading  ›") }
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun SagaEditScreen(vm: PanelViewModel, sagaId: String?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val sagas by vm.sagas.collectAsStateWithLifecycle()
    val saga = sagas.find { it.id == sagaId }
    var name by remember(sagaId, saga?.name) { mutableStateOf(saga?.name.orEmpty()) }
    var description by remember(sagaId, saga?.description) { mutableStateOf(saga?.description.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete && saga != null) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Delete Saga?") }, text = { Text("Library entries, reading history, and ratings will stay intact.") },
        confirmButton = { TextButton(onClick = { vm.deleteSaga(saga, onBack); confirmDelete = false }) { Text("Delete Saga") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (sagaId == null) "Create Saga" else "Edit Saga", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(name, { name = it }, label = { Text("Saga name *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Button(onClick = { if (saga == null) vm.createSaga(name, description, onSaved) else vm.renameSaga(saga, name, description) { onSaved(saga.id) } }, enabled = name.isNotBlank()) { Text("Save Saga") }
        if (saga != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete Saga") }
    }
}

@Composable internal fun SagaAddScreen(vm: PanelViewModel, sagaId: String, onSearch: () -> Unit, onSeries: (Int) -> Unit, onDone: () -> Unit) {
    val series by vm.savedSeries.collectAsStateWithLifecycle()
    val issues by vm.issues.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Add Content", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onSearch) { Text("Search Metron") }
        Text("Saved content", style = MaterialTheme.typography.titleLarge)
        if (series.none { it.inLibrary } && issues.none { it.inLibrary }) Text("Your library is empty. Search Metron or add a comic manually.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(series.filter { it.inLibrary }, key = { "series-${it.id}" }) { item ->
                IssueCard(item.title, item.publisher.orEmpty(), item.year?.toString().orEmpty(), item.image, "Choose series issues") { onSeries(item.metronId) }
            }
            items(issues.filter { it.inLibrary }, key = { "issue-${it.id}" }) { item ->
                Card(shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.title, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.addIssueToSaga(sagaId, item, onDone) }) { Text("Add") }
                    }
                }
            }
        }
    }
}

@Composable internal fun LegacySagaDetailScreen(
    vm: PanelViewModel, sagaId: String, onAdd: () -> Unit, onEdit: () -> Unit,
    onIssue: (String) -> Unit, onSeries: (Int) -> Unit
) {
    val sagas by vm.sagas.collectAsStateWithLifecycle()
    val allEntries by vm.sagaEntries.collectAsStateWithLifecycle()
    val tracked by vm.trackedIssues.collectAsStateWithLifecycle()
    val issues by vm.issues.collectAsStateWithLifecycle()
    val series by vm.savedSeries.collectAsStateWithLifecycle()
    val saga = sagas.find { it.id == sagaId }
    LaunchedEffect(sagaId) { vm.openSaga(sagaId) }
    val entries = allEntries.filter { it.sagaId == sagaId }.sortedBy { it.position }
    val progress = sagaProgress(entries, tracked, issues)
    val expanded = remember(sagaId) { mutableStateMapOf<String, Boolean>() }
    if (saga == null) { Text("Loading Saga…"); return }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        saga.cover?.let { AsyncImage(it, "Saga cover", Modifier.height(140.dp).fillMaxWidth()) }
        Text(saga.name, style = MaterialTheme.typography.headlineMedium)
        if (saga.description.isNotBlank()) Text(saga.description)
        Text("Reading progress: ${progress.percent}% · ${progress.completed}/${progress.total} issues")
        LinearProgressIndicator(progress = { progress.percent / 100f }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAdd) { Text("Add Content") }
            OutlinedButton(onClick = onEdit) { Text("Edit Saga") }
        }
        if (progress.total == 0) Text("No tracked issues yet. Add a series or issue to begin.")
        progress.nextIssueId?.let { id ->
            val next = issues.find { it.id == id }
            TextButton(onClick = { onIssue(id) }) { Text("Continue: ${next?.title ?: "next issue"}") }
        }
        Text("Reading List", style = MaterialTheme.typography.titleLarge)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(entries, key = { it.id }) { entry ->
                val item = entry.issueId?.let { id -> issues.find { it.id == id } }
                val parent = entry.seriesId?.let { id -> series.find { it.id == id } }
                val members = tracked.filter { it.entryId == entry.id }.sortedBy { it.position }
                val memberIssues = members.mapNotNull { member -> issues.find { it.id == member.issueId } }
                val status = if (entry.type == SagaEntryType.ISSUE) item?.status?.label().orEmpty()
                    else if (members.isEmpty()) "No issues selected"
                    else if (memberIssues.size == members.size && memberIssues.all { it.status == ReadingStatus.COMPLETED }) "Completed"
                    else if (memberIssues.any { it.status != ReadingStatus.WANT_TO_READ }) "In Progress" else "Not Started"
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${entry.position + 1}. ${parent?.title ?: item?.title ?: "Unavailable entry"}", style = MaterialTheme.typography.titleMedium)
                        Text(status, color = MaterialTheme.colorScheme.primary)
                        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.moveSagaEntry(entry, -1) }, enabled = entry.position > 0) { Text("↑ Move") }
                            TextButton(onClick = { vm.moveSagaEntry(entry, 1) }, enabled = entry.position < entries.lastIndex) { Text("↓ Move") }
                            TextButton(onClick = { vm.removeSagaEntry(entry) }) { Text("Remove") }
                        }
                        val cover = parent?.image ?: item?.image
                        if (cover != null) TextButton(onClick = { vm.setSagaCover(saga, cover) }) { Text("Use as Saga cover") }
                        if (entry.type == SagaEntryType.ISSUE && item != null) {
                            TextButton(onClick = { onIssue(item.id) }) { Text("Open issue") }
                            if (item.status != ReadingStatus.COMPLETED) TextButton(onClick = { vm.setStatus(item, ReadingStatus.COMPLETED) }) { Text("Mark Completed") }
                        }
                        if (entry.type == SagaEntryType.SERIES) {
                            parent?.let { TextButton(onClick = { onSeries(it.metronId) }) { Text("Edit selected issues") } }
                            TextButton(onClick = { expanded[entry.id] = expanded[entry.id] != true }) { Text(if (expanded[entry.id] == true) "Collapse issues" else "Expand ${members.size} issues") }
                            if (expanded[entry.id] == true) members.forEachIndexed { index, member ->
                                val issue = issues.find { it.id == member.issueId }
                                if (issue != null) Column(Modifier.padding(start = 12.dp)) {
                                    Text(issue.title)
                                    Text(issue.status.label(), color = MaterialTheme.colorScheme.primary)
                                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                                        TextButton(onClick = { onIssue(issue.id) }) { Text("Open") }
                                        if (issue.status != ReadingStatus.COMPLETED) TextButton(onClick = { vm.setStatus(issue, ReadingStatus.COMPLETED) }) { Text("Complete") }
                                        TextButton(onClick = { vm.moveTracked(member, -1) }, enabled = index > 0) { Text("↑") }
                                        TextButton(onClick = { vm.moveTracked(member, 1) }, enabled = index < members.lastIndex) { Text("↓") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
