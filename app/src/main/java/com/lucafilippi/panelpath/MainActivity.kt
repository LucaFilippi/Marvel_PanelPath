package com.lucafilippi.panelpath

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.room.withTransaction
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private val red = Color(0xFFE85454)
private val dark = Color(0xFF141416)
private val panel = Color(0xFF242428)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent {
        MaterialTheme(colorScheme = darkColorScheme(primary = PanelPalette.accent, background = PanelPalette.background,
            surface = PanelPalette.surface, onBackground = Color.White, onSurface = Color.White)) {
            var opening by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) { delay(900); opening = false }
            if (opening) Image(painterResource(R.drawable.panelpath_cover), "PanelPath — Every Story Has a Path",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else PanelPathApp()
        }
    } }
}

enum class MembershipMode { ALL, SELECTED, RANGE }

class PanelViewModel(app: Application) : AndroidViewModel(app) {
    private val token = TokenStore(app)
    private val profileRepo = ProfileRepository(app)
    private val library = LibraryRepository(PanelDatabase.get(app).issues())
    private val sagaRepo = SagaRepository(PanelDatabase.get(app))
    private val metron = MetronRepository(token)
    val profile = profileRepo.profile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val issues = library.issues.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val savedSeries = sagaRepo.series.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sagas = sagaRepo.sagas.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sagaEntries = sagaRepo.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trackedIssues = sagaRepo.tracked.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val hasToken = MutableStateFlow(token.hasToken())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val searchResults = MutableStateFlow<List<SeriesDto>>(emptyList())
    val searchHasNext = MutableStateFlow(false)
    val issueSearchResults = MutableStateFlow<List<IssueDto>>(emptyList())
    val issueSearchHasNext = MutableStateFlow(false)
    val seriesIssues = MutableStateFlow<List<IssueDto>>(emptyList())
    val issuesHasNext = MutableStateFlow(false)
    val selectedIssue = MutableStateFlow<IssueDto?>(null)
    val selectedSeries = MutableStateFlow<SeriesDto?>(null)
    val importProgress = MutableStateFlow<String?>(null)
    private var importJob: Job? = null
    private var searchJob: Job? = null
    private var query = ""
    private var searchPage = 0
    private var issueSearchPage = 0
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
    var undoAction: (() -> Unit)? = null
    fun bulkAction(selected: List<SavedIssue>, action: String) = viewModelScope.launch {
        val db = PanelDatabase.get(getApplication())
        val before = selected.mapNotNull { library.get(it.id) }
        db.withTransaction { before.forEach { issue ->
            when (action) {
                "Read" -> library.setStatus(issue, ReadingStatus.COMPLETED)
                "Unread" -> library.setStatus(issue, ReadingStatus.WANT_TO_READ)
                "Reading" -> library.setStatus(issue, ReadingStatus.READING)
                "Queue" -> library.queue(issue)
                "Remove" -> library.unqueue(issue)
            }
            sagaRepo.recordIssueActivity(issue.id)
        } }
        val after = before.mapNotNull { library.get(it.id) }.associateBy { it.id }
        undoAction = { viewModelScope.launch { db.withTransaction {
            before.forEach { old ->
                val current = library.get(old.id)
                if (current != null && current.modifiedAt == after[old.id]?.modifiedAt && current.status == after[old.id]?.status && current.completedAt == after[old.id]?.completedAt && current.queued == after[old.id]?.queued) {
                    db.issues().update(current.copy(status = old.status, completedAt = old.completedAt, queued = old.queued, queuePosition = old.queuePosition))
                }
            }
        } } }
        message.value = "${before.size} issues updated"
    }
    fun setStatus(issue: SavedIssue, status: ReadingStatus) { bulkAction(listOf(issue), if (status == ReadingStatus.COMPLETED) "Read" else if (status == ReadingStatus.WANT_TO_READ) "Unread" else "Reading") }

    fun rate(issue: SavedIssue, rating: Int?) = viewModelScope.launch { library.setRating(issue, rating) }
    fun rateRemote(issue: IssueDto, rating: Int?) = viewModelScope.launch { library.setRating(library.ensureRemote(issue), rating) }
    fun setRemoteStatus(issue: IssueDto, status: ReadingStatus) = viewModelScope.launch {
        val canonical = library.ensureRemote(issue); library.setStatus(canonical, status); sagaRepo.recordIssueActivity(canonical.id)
    }
    fun remove(issue: SavedIssue, onDone: () -> Unit) = viewModelScope.launch { library.remove(issue); onDone() }
    fun saveLocalIssue(issue: SavedIssue) = viewModelScope.launch { library.save(issue) }
    fun queueRemote(issue: IssueDto) = viewModelScope.launch { library.queue(library.ensureRemote(issue)) }
    fun queueIssue(issue: SavedIssue) = viewModelScope.launch { library.queue(issue) }
    fun unqueueIssue(issue: SavedIssue) { bulkAction(listOf(issue), "Remove") }
    fun reorderQueue(issues: List<SavedIssue>) = viewModelScope.launch { library.reorderQueue(issues.map { it.id }) }
    fun favoriteSeries(metronId: Int, favorite: Boolean) = viewModelScope.launch { PanelDatabase.get(getApplication()).sagas().setFavorite(metronId, favorite) }
    fun search(text: String) {
        query = text; searchJob?.cancel(); searchResults.value = emptyList(); searchHasNext.value = false; searchPage = 0
        issueSearchResults.value = emptyList(); issueSearchHasNext.value = false; issueSearchPage = 0
        if (text.isBlank()) return
        searchJob = viewModelScope.launch { delay(400); fetchSeriesPage(); fetchSearchIssuePage() }
    }
    fun loadMoreSeries() = viewModelScope.launch { fetchSeriesPage() }
    fun loadMoreSearchIssues() = viewModelScope.launch { fetchSearchIssuePage() }
    private suspend fun fetchSeriesPage() {
        if (query.isBlank() || busy.value || (searchPage > 0 && !searchHasNext.value)) return
        val currentQuery = query
        busy.value = true
        try { when (val result = metron.series(currentQuery, searchPage + 1)) {
            is ApiResult.Success -> if (query == currentQuery) { searchPage++; searchResults.value += result.value.results; searchHasNext.value = result.value.next != null }
            is ApiResult.Failure -> if (query == currentQuery) message.value = result.message
        } } finally { busy.value = false }
    }
    private suspend fun fetchSearchIssuePage() {
        if (query.isBlank() || busy.value || (issueSearchPage > 0 && !issueSearchHasNext.value)) return
        val currentQuery = query
        busy.value = true
        try { when (val result = metron.searchIssues(currentQuery, issueSearchPage + 1)) {
            is ApiResult.Success -> if (query == currentQuery) { issueSearchPage++; issueSearchResults.value += result.value.results; issueSearchHasNext.value = result.value.next != null }
            is ApiResult.Failure -> if (query == currentQuery) message.value = result.message
        } } finally { busy.value = false }
    }
    fun openSeries(id: Int) {
        seriesId = id; issuePage = 0; seriesIssues.value = emptyList(); issuesHasNext.value = false
        selectedSeries.value = null
        viewModelScope.launch { when (val result = metron.seriesDetail(id)) {
            is ApiResult.Success -> { selectedSeries.value = result.value; sagaRepo.fillSeriesMetadata(result.value) }
            is ApiResult.Failure -> message.value = result.message
        } }
        loadMoreIssues()
    }
    fun clearSeriesView(id: Int) { seriesId = id; issuePage = 0; seriesIssues.value = emptyList(); issuesHasNext.value = false; selectedSeries.value = null }
    fun loadMoreIssues() = viewModelScope.launch {
        if (busy.value || (issuePage > 0 && !issuesHasNext.value)) return@launch
        busy.value = true
        when (val result = metron.issues(seriesId, issuePage + 1)) {
            is ApiResult.Success -> {
                issuePage++; seriesIssues.value += result.value.results; issuesHasNext.value = result.value.next != null
                sagaRepo.fillSeriesCover(seriesId, result.value.results.firstOrNull()?.image)
                result.value.results.forEach { if (library.findMetron(it.id) != null) library.ensureRemote(it) }
            }
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
        library.ensureRemote(issue, true)
        message.value = "Issue saved to your library."
        onDone()
    }
    fun saveSeries(dto: SeriesDto, cover: String? = null) = viewModelScope.launch {
        sagaRepo.saveSeries(dto, cover ?: seriesIssues.value.firstOrNull()?.image)
        message.value = "Series saved to your library."
    }
    fun removeSeries(series: SavedSeries, onDone: () -> Unit) = viewModelScope.launch { sagaRepo.removeSeries(series); onDone() }
    fun createSaga(name: String, description: String, onDone: (String) -> Unit) = viewModelScope.launch {
        if (name.isBlank()) { message.value = "Saga name is required."; return@launch }
        onDone(sagaRepo.createSaga(name, description).id)
    }
    fun renameSaga(saga: Saga, name: String, description: String, onDone: () -> Unit) = viewModelScope.launch {
        if (name.isBlank()) { message.value = "Saga name is required."; return@launch }
        sagaRepo.renameSaga(saga, name, description); onDone()
    }
    fun deleteSaga(saga: Saga, onDone: () -> Unit) = viewModelScope.launch { sagaRepo.deleteSaga(saga); onDone() }
    fun setSagaCover(saga: Saga, cover: String?) = viewModelScope.launch { sagaRepo.setCover(saga, cover) }
    fun addIssueToSaga(sagaId: String, issue: SavedIssue, onDone: () -> Unit = {}) = viewModelScope.launch {
        if (sagaRepo.addIssue(sagaId, issue)) { message.value = "Issue added to Saga."; onDone() }
        else message.value = "This issue is already in that Saga."
    }
    fun addRemoteIssueToSaga(sagaId: String, issue: IssueDto, onDone: () -> Unit = {}) = viewModelScope.launch {
        if (sagaRepo.addRemoteIssue(sagaId, issue)) { message.value = "Issue added to Saga."; onDone() }
        else message.value = "This issue is already in that Saga."
    }
    fun addSeriesToSaga(sagaId: String, dto: SeriesDto, mode: MembershipMode,
                        selected: List<IssueDto>, rangeStart: Int?, rangeEnd: Int?, onDone: () -> Unit) {
        importJob?.cancel()
        importJob = viewModelScope.launch {
            importProgress.value = "Preparing issues…"
            try {
                val chosen = if (mode != MembershipMode.SELECTED) {
                    val gathered = mutableListOf<IssueDto>()
                    var page = 1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        when (val result = metron.issues(dto.id, page)) {
                            is ApiResult.Failure -> { message.value = "Saga import stopped: ${result.message}"; return@launch }
                            is ApiResult.Success -> {
                                gathered += result.value.results
                                importProgress.value = "Loaded ${gathered.size}${dto.issueCount?.let { " / $it" }.orEmpty()} issues…"
                                if (result.value.next == null) break
                                page++
                                if (page > 1000) { message.value = "Series is too large to import safely."; return@launch }
                            }
                        }
                    }
                    if (mode == MembershipMode.RANGE) {
                        val start = rangeStart ?: return@launch
                        val end = rangeEnd ?: return@launch
                        gathered.filter { it.number?.toIntOrNull()?.let { number -> number in start..end } == true }
                    } else gathered
                } else selected
                if (chosen.isEmpty()) { message.value = "Select at least one issue."; return@launch }
                sagaRepo.addSeries(sagaId, dto, chosen)
                message.value = "Series added to Saga with ${chosen.size} issues."
                onDone()
            } catch (_: CancellationException) { message.value = "Saga import cancelled; no membership was changed." }
            finally { importProgress.value = null }
        }
    }
    fun cancelImport() { importJob?.cancel() }
    fun moveSagaEntry(entry: SagaEntry, direction: Int) = viewModelScope.launch { sagaRepo.moveEntry(entry, direction) }
    fun moveTracked(item: SagaTrackedIssue, direction: Int) = viewModelScope.launch { sagaRepo.moveTracked(item, direction) }
    fun removeSagaEntry(entry: SagaEntry) = viewModelScope.launch { sagaRepo.removeEntry(entry) }
    fun openSaga(sagaId: String) = viewModelScope.launch { sagaRepo.openSaga(sagaId) }
}

@Composable private fun PanelPathApp(vm: PanelViewModel = viewModel()) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { val undo = vm.undoAction; vm.undoAction = null; if (snackbar.showSnackbar(it, actionLabel = if (undo != null) "Undo" else null) == SnackbarResult.ActionPerformed) undo?.invoke(); vm.clearMessage() } }
    Scaffold(containerColor = PanelPalette.background, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (profile != null && (route == "home" || route == "library" || route == "queue" || route == "sagas" || route == "ratings" || route == "dashboard" || route.startsWith("search"))) {
            val destinations = listOf(
                Triple("library", "Library", Icons.Outlined.Book),
                Triple("sagas", "Sagas", Icons.Outlined.Layers),
                Triple("ratings", "Ratings", Icons.Outlined.StarBorder),
                Triple("dashboard", "Dashboard", Icons.Outlined.BarChart),
                Triple("search", "Search", Icons.Outlined.Search)
            )
            Row(Modifier.fillMaxWidth().background(PanelPalette.background).navigationBarsPadding().padding(top = 10.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                destinations.forEach { (destination, label, icon) ->
                    val selected = route == destination || (destination == "library" && route == "home") || (destination == "search" && route.startsWith("search"))
                    Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable { nav.navigate(destination) { popUpTo("home"); launchSingleTop = true; restoreState = true } }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, contentDescription = label, tint = if (selected) PanelPalette.accent else PanelPalette.muted, modifier = Modifier.size(24.dp))
                        Text(label, color = if (selected) PanelPalette.accent else PanelPalette.muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { if (profile == null) WelcomeScreen(vm) { nav.navigate("library") } else LibraryV2Screen(vm, { nav.navigate("manual") }, { nav.navigate("saved/$it") }, { nav.navigate("series/$it") }, { nav.navigate("saga/$it") }, { nav.navigate("settings") }, { nav.navigate("queue") }, { nav.navigate("history") }) }
            composable("library") { LibraryV2Screen(vm, { nav.navigate("manual") }, { nav.navigate("saved/$it") }, { nav.navigate("series/$it") }, { nav.navigate("saga/$it") }, { nav.navigate("settings") }, { nav.navigate("queue") }, { nav.navigate("history") }) }
            composable("queue") { ReadingQueueScreen(vm, { nav.navigate("saved/$it") }, { nav.navigate("settings") }) }
            composable("history") { ReadingHistoryScreen(vm, { nav.navigate("saved/$it") }) }
            composable("manual") { ManualScreen(vm) { nav.popBackStack() } }
            composable("saved/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { SavedDetailsV2Screen(vm, it.arguments?.getString("id").orEmpty()) { nav.popBackStack() } }
            composable("search?target={target}", arguments = listOf(navArgument("target") { type = NavType.StringType; defaultValue = "" })) { entry ->
                val target = entry.arguments?.getString("target").orEmpty()
                SearchV2Screen(vm, { nav.navigate("series/$it?target=$target") }, { nav.navigate("issue/$it?target=$target") }, { nav.navigate("settings") })
            }
            composable("series/{id}?target={target}", arguments = listOf(navArgument("id") { type = NavType.IntType }, navArgument("target") { type = NavType.StringType; defaultValue = "" })) { entry ->
                val target = entry.arguments?.getString("target").orEmpty()
                SeriesV2Screen(vm, entry.arguments!!.getInt("id"), target, { id -> nav.navigate("issue/$id?target=$target") }, { nav.navigate("saved/$it") })
            }
            composable("issue/{id}?target={target}", arguments = listOf(navArgument("id") { type = NavType.IntType }, navArgument("target") { type = NavType.StringType; defaultValue = "" })) { entry ->
                RemoteIssueV2Screen(vm, entry.arguments!!.getInt("id"), entry.arguments?.getString("target").orEmpty()) { nav.navigate("library") }
            }
            composable("sagas") { SagasScreen(vm, { nav.navigate("saga-create") }, { nav.navigate("saga/$it") }, { nav.navigate("settings") }) }
            composable("saga-create") { SagaEditScreen(vm, null, { nav.popBackStack() }, { id -> nav.navigate("saga/$id") { popUpTo("sagas") } }) }
            composable("saga-edit/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { SagaEditScreen(vm, it.arguments?.getString("id"), { nav.popBackStack() }, { nav.popBackStack() }) }
            composable("saga/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                SagaDetailScreen(vm, id, { nav.navigate("saga-add/$id") }, { nav.navigate("saga-edit/$id") }, { nav.navigate("saved/$it") }, { nav.navigate("series/$it?target=$id") }, { nav.popBackStack() })
            }
            composable("saga-add/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                SagaAddScreen(vm, id, { nav.navigate("search?target=$id") }, { nav.navigate("series/$it?target=$id") }, { nav.popBackStack() })
            }
            composable("ratings") { SeriesRatingsScreen(vm, { nav.navigate("saved/$it") }, { nav.navigate("settings") }) }
            composable("dashboard") { DashboardScreen(vm, { nav.navigate("series/$it") }, { nav.navigate("saved/$it") }, { nav.navigate("settings") }) }
            composable("settings") { SettingsV2Screen(vm) { nav.popBackStack() } }
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.panelpath_cover), "PanelPath cover", modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)), contentScale = ContentScale.Crop)
        Text("Start your journey", style = MaterialTheme.typography.headlineMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text("Every story has a path.", color = PanelPalette.muted)
        OutlinedTextField(name, { name = it }, label = { Text("Profile name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("PanelPath uses the Metron Comic Book Database to retrieve comic information. To enable online comic search, create a free Metron account and generate your personal API token.")
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://metron.cloud/"))) }) { Text("Visit Metron") }
        TokenInput(token, { token = it })
        Button(onClick = { vm.validateToken(token) { vm.createProfile(name, onDone) } }, enabled = !busy && name.isNotBlank() && token.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Validating…" else "Validate token and continue") }
        TextButton(onClick = { vm.createProfile(name, onDone) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Continue offline") }
        Spacer(Modifier.height(18.dp))
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

internal fun ReadingStatus.label() = when (this) { ReadingStatus.WANT_TO_READ -> "Want to Read"; ReadingStatus.READING -> "Reading"; ReadingStatus.COMPLETED -> "Completed" }

@Composable internal fun IssueCard(title: String, series: String, number: String, image: String?, subtitle: String, onClick: () -> Unit) {
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

@Composable internal fun StatusPicker(current: ReadingStatus, onPick: (ReadingStatus) -> Unit) {
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
        Text("PanelPath 0.4.4")
        Text("Comic metadata provided by the Metron Comic Book Database.")
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://metron.cloud/docs/"))) }) { Text("Metron API documentation") }
    }
}
