package com.lucafilippi.panelpath

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.lucafilippi.panelpath.data.*
import com.lucafilippi.panelpath.security.TokenStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

private val red = Color(0xFFE85454)
private val dark = Color(0xFF141416)
private val panel = Color(0xFF242428)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent {
        MaterialTheme(colorScheme = darkColorScheme(primary = red, background = dark, surface = panel)) { PanelPathApp() }
    } }
}

class PanelViewModel(app: Application) : AndroidViewModel(app) {
    private val token = TokenStore(app)
    private val profileRepo = ProfileRepository(app)
    private val library = LibraryRepository(PanelDatabase.get(app).issues())
    private val metron = MetronRepository(token)
    val profile = profileRepo.profile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val issues = library.issues.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val hasToken = MutableStateFlow(token.hasToken())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val searchResults = MutableStateFlow<List<SeriesDto>>(emptyList())
    val searchHasNext = MutableStateFlow(false)
    val seriesIssues = MutableStateFlow<List<IssueDto>>(emptyList())
    val issuesHasNext = MutableStateFlow(false)
    val selectedIssue = MutableStateFlow<IssueDto?>(null)
    private var searchJob: Job? = null
    private var query = ""
    private var searchPage = 0
    private var seriesId = 0
    private var issuePage = 0

    fun clearMessage() { message.value = null }
    fun createProfile(name: String, onDone: () -> Unit) = viewModelScope.launch { if (name.isBlank()) { message.value = "Enter a profile name."; return@launch }; profileRepo.create(name); onDone() }
    fun rename(name: String) = viewModelScope.launch { if (name.isNotBlank()) profileRepo.rename(name) }
    fun validateToken(candidate: String, onSuccess: () -> Unit = {}) = viewModelScope.launch {
        if (candidate.isBlank()) { message.value = "Paste a Metron token first."; return@launch }
        busy.value = true
        when (val result = metron.validate(candidate.trim())) {
            is ApiResult.Success -> { token.save(candidate.trim()); hasToken.value = true; message.value = "Metron token validated and saved."; onSuccess() }
            is ApiResult.Failure -> message.value = result.message
        }
        busy.value = false
    }
    fun removeToken() { token.delete(); hasToken.value = false; message.value = "Token removed. Your library is still available." }
    fun testToken() = viewModelScope.launch {
        val value = token.read() ?: run { message.value = "No Metron token configured."; hasToken.value = false; return@launch }
        busy.value = true
        message.value = when (val result = metron.validate(value)) { is ApiResult.Success -> "Metron connection works."; is ApiResult.Failure -> result.message }
        busy.value = false
    }
    fun addManual(title: String, series: String, number: String, year: String, status: ReadingStatus, onDone: () -> Unit) = viewModelScope.launch {
        if (title.isBlank()) { message.value = "Comic title is required."; return@launch }
        library.add(SavedIssue(title = title.trim(), series = series.trim(), number = number.trim(), year = year.toIntOrNull(), status = status, completedAt = if (status == ReadingStatus.COMPLETED) System.currentTimeMillis() else null))
        onDone()
    }
    fun setStatus(issue: SavedIssue, status: ReadingStatus) = viewModelScope.launch { library.setStatus(issue, status) }
    fun remove(issue: SavedIssue, onDone: () -> Unit) = viewModelScope.launch { library.remove(issue); onDone() }
    fun search(text: String) {
        query = text; searchJob?.cancel(); searchResults.value = emptyList(); searchHasNext.value = false; searchPage = 0
        if (text.isBlank()) return
        searchJob = viewModelScope.launch { delay(400); loadMoreSeries() }
    }
    fun loadMoreSeries() = viewModelScope.launch {
        if (query.isBlank() || busy.value || (searchPage > 0 && !searchHasNext.value)) return@launch
        busy.value = true
        when (val result = metron.series(query, searchPage + 1)) {
            is ApiResult.Success -> { searchPage++; searchResults.value += result.value.results; searchHasNext.value = result.value.next != null }
            is ApiResult.Failure -> message.value = result.message
        }
        busy.value = false
    }
    fun openSeries(id: Int) { seriesId = id; issuePage = 0; seriesIssues.value = emptyList(); issuesHasNext.value = false; loadMoreIssues() }
    fun loadMoreIssues() = viewModelScope.launch {
        if (busy.value || (issuePage > 0 && !issuesHasNext.value)) return@launch
        busy.value = true
        when (val result = metron.issues(seriesId, issuePage + 1)) {
            is ApiResult.Success -> { issuePage++; seriesIssues.value += result.value.results; issuesHasNext.value = result.value.next != null }
            is ApiResult.Failure -> message.value = result.message
        }
        busy.value = false
    }
    fun openIssue(id: Int) = viewModelScope.launch {
        busy.value = true; selectedIssue.value = null
        when (val result = metron.issue(id)) { is ApiResult.Success -> selectedIssue.value = result.value; is ApiResult.Failure -> message.value = result.message }
        busy.value = false
    }
    fun saveMetron(issue: IssueDto, onDone: () -> Unit) = viewModelScope.launch {
        val existing = library.findMetron(issue.id)
        if (existing == null) library.add(SavedIssue(source = SourceType.METRON, metronId = issue.id, title = issue.issue ?: issue.series?.name ?: "Issue #${issue.number.orEmpty()}", series = issue.series?.name.orEmpty(), number = issue.number.orEmpty(), year = issue.coverDate?.take(4)?.toIntOrNull(), image = issue.image, description = issue.desc, publisher = issue.series?.publisher?.name))
        else message.value = "Already in your library."
        onDone()
    }
}

@Composable private fun PanelPathApp(vm: PanelViewModel = viewModel()) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        if (profile != null) Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("PanelPath", style = MaterialTheme.typography.titleLarge, color = red)
            TextButton(onClick = { nav.navigate("settings") }) { Text("Settings") }
        }
    }, bottomBar = {
        if (profile != null) NavigationBar(containerColor = panel) {
            NavigationBarItem(selected = false, onClick = { nav.navigate("library") { popUpTo("library") { inclusive = true } } }, icon = { Text("▤") }, label = { Text("My Library") })
            NavigationBarItem(selected = false, onClick = { nav.navigate("search") }, icon = { Text("⌕") }, label = { Text("Search") })
        }
    }) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { if (profile == null) WelcomeScreen(vm) { nav.navigate("library") } else LibraryScreen(vm, { nav.navigate("manual") }, { nav.navigate("saved/$it") }) }
            composable("library") { LibraryScreen(vm, { nav.navigate("manual") }, { nav.navigate("saved/$it") }) }
            composable("manual") { ManualScreen(vm) { nav.popBackStack() } }
            composable("saved/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { SavedDetails(vm, it.arguments?.getString("id").orEmpty()) { nav.popBackStack() } }
            composable("search") { SearchScreen(vm, { nav.navigate("series/$it") }, { nav.navigate("settings") }) }
            composable("series/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { SeriesScreen(vm, it.arguments!!.getInt("id")) { id -> nav.navigate("issue/$id") } }
            composable("issue/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { RemoteIssueScreen(vm, it.arguments!!.getInt("id")) { nav.navigate("library") } }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}

@Composable private fun TokenInput(value: String, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, onChange, label = { Text("Personal Metron API token") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") } })
}

@Composable private fun WelcomeScreen(vm: PanelViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }; var token by remember { mutableStateOf("") }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("PanelPath", style = MaterialTheme.typography.headlineLarge, color = red)
        Text("Your Comic Reading Companion")
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Profile name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(20.dp))
        Text("PanelPath uses the Metron Comic Book Database to retrieve comic information. To enable online comic search, create a free Metron account and generate your personal API token.")
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://metron.cloud/"))) }) { Text("Visit Metron") }
        TokenInput(token, { token = it })
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.validateToken(token) { vm.createProfile(name, onDone) } }, enabled = !busy && name.isNotBlank() && token.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Validating…" else "Validate token and continue") }
        TextButton(onClick = { vm.createProfile(name, onDone) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Continue offline") }
    }
}

@Composable private fun LibraryScreen(vm: PanelViewModel, onManual: () -> Unit, onIssue: (String) -> Unit) {
    val all by vm.issues.collectAsStateWithLifecycle(); var filter by remember { mutableStateOf<ReadingStatus?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("My Library", style = MaterialTheme.typography.headlineMedium)
        Text("${all.size} saved  ·  ${all.count { it.status == ReadingStatus.COMPLETED }} completed", color = Color.LightGray)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filter == null, onClick = { filter = null }, label = { Text("All") })
            ReadingStatus.entries.forEach { status -> FilterChip(filter == status, onClick = { filter = status }, label = { Text(status.label()) }) }
        }
        Button(onClick = onManual) { Text("Add comic manually") }
        val shown = all.filter { filter == null || it.status == filter }
        if (all.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Your library is empty. Add your first comic.") }
        else if (shown.isEmpty()) Text("No comics with this status.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(shown, key = { it.id }) { issue -> IssueCard(issue.title, issue.series, issue.number, issue.image, issue.status.label()) { onIssue(issue.id) } } }
    }
}

private fun ReadingStatus.label() = when (this) { ReadingStatus.WANT_TO_READ -> "Want to Read"; ReadingStatus.READING -> "Reading"; ReadingStatus.COMPLETED -> "Completed" }

@Composable private fun IssueCard(title: String, series: String, number: String, image: String?, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (image != null) AsyncImage(model = image, contentDescription = "Cover", modifier = Modifier.size(64.dp, 86.dp))
            else Box(Modifier.size(64.dp, 86.dp).background(dark), contentAlignment = Alignment.Center) { Text("▤", color = red) }
            Spacer(Modifier.width(12.dp))
            Column { Text(title, style = MaterialTheme.typography.titleMedium); if (series.isNotBlank()) Text(series, color = Color.LightGray); if (number.isNotBlank()) Text("#$number"); Text(subtitle, color = red) }
        }
    }
}

@Composable private fun ManualScreen(vm: PanelViewModel, onDone: () -> Unit) {
    var title by remember { mutableStateOf("") }; var series by remember { mutableStateOf("") }; var number by remember { mutableStateOf("") }; var year by remember { mutableStateOf("") }; var status by remember { mutableStateOf(ReadingStatus.WANT_TO_READ) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add comic", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(title, { title = it }, label = { Text("Comic title *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(series, { series = it }, label = { Text("Series") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(number, { number = it }, label = { Text("Issue number") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(year, { year = it }, label = { Text("Publication year") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        StatusPicker(status) { status = it }
        Button(onClick = { vm.addManual(title, series, number, year, status, onDone) }, enabled = title.isNotBlank()) { Text("Save to library") }
    }
}

@Composable private fun StatusPicker(current: ReadingStatus, onPick: (ReadingStatus) -> Unit) {
    Column { Text("Reading status"); ReadingStatus.entries.forEach { status -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(current == status, onClick = { onPick(status) }); Text(status.label()) } } }
}

@Composable private fun SavedDetails(vm: PanelViewModel, id: String, onDone: () -> Unit) {
    val all by vm.issues.collectAsStateWithLifecycle(); val issue = all.find { it.id == id }
    if (issue == null) { Text("Issue not found."); return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        issue.image?.let { AsyncImage(it, "Cover", Modifier.height(240.dp).fillMaxWidth()) }
        Text(issue.title, style = MaterialTheme.typography.headlineMedium)
        Text(listOf(issue.series, issue.number.takeIf { it.isNotBlank() }?.let { "#$it" }, issue.year?.toString()).filterNotNull().joinToString(" · "))
        issue.description?.let { Text(it) }; issue.publisher?.let { Text("Publisher: $it") }
        Text("Source: ${issue.source.name.lowercase().replaceFirstChar { it.uppercase() }}")
        StatusPicker(issue.status) { vm.setStatus(issue, it) }
        OutlinedButton(onClick = { vm.remove(issue, onDone) }) { Text("Remove from library") }
    }
}

@Composable private fun SearchScreen(vm: PanelViewModel, onSeries: (Int) -> Unit, onSettings: () -> Unit) {
    val hasToken by vm.hasToken.collectAsStateWithLifecycle(); val results by vm.searchResults.collectAsStateWithLifecycle(); val next by vm.searchHasNext.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Search Metron", style = MaterialTheme.typography.headlineMedium)
        if (!hasToken) { Text("Online search needs your personal Metron token. Your local library still works offline."); Button(onClick = onSettings) { Text("Configure token") }; return@Column }
        OutlinedTextField(query, { query = it; vm.search(it) }, label = { Text("Search Marvel series") }, modifier = Modifier.fillMaxWidth())
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (query.isNotBlank() && !busy && results.isEmpty()) Text("No matching series yet.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it.id }) { series -> IssueCard(series.series.orEmpty(), series.publisher?.name.orEmpty(), series.yearBegan?.toString().orEmpty(), null, "${series.issueCount ?: 0} issues") { onSeries(series.id) } }
            if (next) item { TextButton(onClick = vm::loadMoreSeries) { Text("Load more") } }
        }
    }
}

@Composable private fun SeriesScreen(vm: PanelViewModel, id: Int, onIssue: (Int) -> Unit) {
    LaunchedEffect(id) { vm.openSeries(id) }
    val issues by vm.seriesIssues.collectAsStateWithLifecycle(); val next by vm.issuesHasNext.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Series issues", style = MaterialTheme.typography.headlineMedium)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!busy && issues.isEmpty()) Text("No issues found.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(issues, key = { it.id }) { issue -> IssueCard(issue.issue ?: issue.series?.name.orEmpty(), issue.series?.name.orEmpty(), issue.number.orEmpty(), issue.image, issue.coverDate.orEmpty()) { onIssue(issue.id) } }
            if (next) item { TextButton(onClick = vm::loadMoreIssues) { Text("Load more") } }
        }
    }
}

@Composable private fun RemoteIssueScreen(vm: PanelViewModel, id: Int, onDone: () -> Unit) {
    LaunchedEffect(id) { vm.openIssue(id) }
    val issue by vm.selectedIssue.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle(); val all by vm.issues.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        issue?.let { data ->
            data.image?.let { AsyncImage(it, "Cover", Modifier.height(240.dp).fillMaxWidth()) }
            Text(data.issue ?: data.series?.name.orEmpty(), style = MaterialTheme.typography.headlineMedium)
            Text("${data.series?.name.orEmpty()} · #${data.number.orEmpty()}")
            data.coverDate?.let { Text("Cover date: $it") }; data.desc?.let { Text(it) }
            Text("Metadata: Metron Comic Book Database")
            val saved = all.find { it.metronId == data.id }
            if (saved == null) Button(onClick = { vm.saveMetron(data, onDone) }) { Text("Add to library") }
            else { Text("Already saved · ${saved.status.label()}"); StatusPicker(saved.status) { vm.setStatus(saved, it) } }
        }
    }
}

@Composable private fun SettingsScreen(vm: PanelViewModel) {
    val profile by vm.profile.collectAsStateWithLifecycle(); val hasToken by vm.hasToken.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    var name by remember(profile?.name) { mutableStateOf(profile?.name.orEmpty()) }; var token by remember { mutableStateOf("") }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Local profile", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(name, { name = it }, label = { Text("Profile name") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.rename(name) }, enabled = name.isNotBlank()) { Text("Save name") }
        HorizontalDivider()
        Text("Metron API", style = MaterialTheme.typography.titleLarge)
        Text(if (hasToken) "Token configured" else "No token configured")
        TokenInput(token, { token = it })
        Button(onClick = { vm.validateToken(token) { token = "" } }, enabled = token.isNotBlank() && !busy) { Text(if (hasToken) "Validate and replace" else "Validate and save") }
        if (hasToken) { OutlinedButton(onClick = vm::testToken, enabled = !busy) { Text("Test connection") }; TextButton(onClick = vm::removeToken) { Text("Remove token") } }
        HorizontalDivider()
        Text("PanelPath 0.1.0")
        Text("Comic metadata provided by the Metron Comic Book Database.")
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://metron.cloud/docs/"))) }) { Text("Metron API documentation") }
    }
}
