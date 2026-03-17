package tech.bananajuice.adzuki.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.mutableIntStateOf
import uniffi.adzuki.calculateTrialBalances
import uniffi.adzuki.AccountBalanceUi
import androidx.compose.ui.text.font.FontWeight
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import tech.bananajuice.adzuki.shared.mvi.BeancountNode
import tech.bananajuice.adzuki.shared.mvi.DocumentEditor
import tech.bananajuice.adzuki.shared.mvi.CodeBlockNode
import tech.bananajuice.adzuki.shared.mvi.DocumentNode
import tech.bananajuice.adzuki.shared.mvi.DocumentState
import tech.bananajuice.adzuki.shared.mvi.DocumentViewModel
import tech.bananajuice.adzuki.shared.mvi.HeadingNode
import tech.bananajuice.adzuki.shared.mvi.ParagraphNode
import tech.bananajuice.adzuki.shared.mvi.Span
import androidx.compose.runtime.rememberCoroutineScope
import uniffi.adzuki.AstNode
import uniffi.adzuki.ParseTree
import uniffi.adzuki.parseToTree

sealed class Screen {
    object SelectFolder : Screen()
    data class JournalList(val rootUri: String) : Screen()
    data class FileList(val journalUri: String) : Screen()
    data class Editor(val fileUri: String, val journalUri: String) : Screen()
}

data class JournalInfo(val name: String, val uri: String)
data class FileInfo(val name: String, val uri: String)

data class MainState(
    val currentScreen: Screen = Screen.SelectFolder,
    val journals: List<JournalInfo> = emptyList(),
    val files: List<FileInfo> = emptyList()
)

sealed class MainIntent {
    data class SelectRootFolder(val uri: String) : MainIntent()
    data class OpenJournal(val journalUri: String) : MainIntent()
    data class CreateJournal(val rootUri: String, val name: String) : MainIntent()
    data class OpenFile(val fileUri: String, val journalUri: String) : MainIntent()
    object NavigateBack : MainIntent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("adzuki_prefs", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        MainState(
            currentScreen = prefs.getString("root_folder_uri", null)?.let { Screen.JournalList(it) } ?: Screen.SelectFolder
        )
    )
    val state = _state.asStateFlow()

    init {
        val currentScreen = _state.value.currentScreen
        if (currentScreen is Screen.JournalList) {
            loadJournals(currentScreen.rootUri)
        }
    }

    fun processIntent(intent: MainIntent) {
        when (intent) {
            is MainIntent.SelectRootFolder -> {
                prefs.edit().putString("root_folder_uri", intent.uri).apply()
                _state.update { it.copy(currentScreen = Screen.JournalList(intent.uri)) }
                loadJournals(intent.uri)
            }
            is MainIntent.OpenJournal -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val journalUriStr = intent.journalUri
                    val mainFileUriStr = prefs.getString("main_file_$journalUriStr", null)

                    val fileToOpen = if (mainFileUriStr != null && DocumentFile.fromSingleUri(getApplication(), Uri.parse(mainFileUriStr))?.exists() == true) {
                        mainFileUriStr
                    } else {
                        val folder = DocumentFile.fromTreeUri(getApplication(), Uri.parse(journalUriStr))
                        val files = folder?.listFiles() ?: emptyArray()
                        val mainBeancountMd = files.find { it.name == "main.beancount.md" }
                        val mainBeancount = files.find { it.name == "main.beancount" }
                        mainBeancountMd?.uri?.toString() ?: mainBeancount?.uri?.toString()
                    }

                    if (fileToOpen != null) {
                        _state.update { it.copy(currentScreen = Screen.Editor(fileToOpen, journalUriStr)) }
                        loadFiles(journalUriStr)
                    } else {
                        _state.update { it.copy(currentScreen = Screen.FileList(journalUriStr)) }
                        loadFiles(journalUriStr)
                    }
                }
            }
            is MainIntent.CreateJournal -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val rootFolder = DocumentFile.fromTreeUri(getApplication(), Uri.parse(intent.rootUri))
                    val newDir = rootFolder?.createDirectory(intent.name)
                    if (newDir != null) {
                        val newFile = newDir.createFile("text/markdown", "main.beancount.md")
                        if (newFile != null) {
                            getApplication<Application>().contentResolver.openOutputStream(newFile.uri)?.use {
                                it.write("# ${intent.name}\n\n".toByteArray())
                            }
                            // Update state on Main Thread implicitly by flow collection
                            _state.update { it.copy(currentScreen = Screen.Editor(newFile.uri.toString(), newDir.uri.toString())) }
                            loadFiles(newDir.uri.toString())
                            // Reload journals so the new one is listed if they go back
                            loadJournals(intent.rootUri)
                        }
                    }
                }
            }
            is MainIntent.OpenFile -> {
                prefs.edit().putString("main_file_${intent.journalUri}", intent.fileUri).apply()
                _state.update { it.copy(currentScreen = Screen.Editor(intent.fileUri, intent.journalUri)) }
            }
            is MainIntent.NavigateBack -> {
                val currentScreen = _state.value.currentScreen
                when (currentScreen) {
                    is Screen.Editor -> {
                        _state.update { it.copy(currentScreen = Screen.FileList(currentScreen.journalUri)) }
                        loadFiles(currentScreen.journalUri)
                    }
                    is Screen.FileList -> {
                        val rootUri = prefs.getString("root_folder_uri", null)
                        if (rootUri != null) {
                            _state.update { it.copy(currentScreen = Screen.JournalList(rootUri)) }
                            loadJournals(rootUri)
                        } else {
                            _state.update { it.copy(currentScreen = Screen.SelectFolder) }
                        }
                    }
                    is Screen.JournalList -> {
                        // For root JournalList, back usually exits the app (handled by Compose / System),
                        // but we could explicitly do nothing or handle it if needed.
                    }
                    is Screen.SelectFolder -> {
                        // Do nothing
                    }
                }
            }
        }
    }

    private fun loadJournals(rootUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val rootFolder = DocumentFile.fromTreeUri(getApplication(), Uri.parse(rootUri))
            val journals = mutableListOf<JournalInfo>()
            rootFolder?.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    journals.add(JournalInfo(name = file.name ?: "Unknown", uri = file.uri.toString()))
                }
            }
            _state.update { it.copy(journals = journals) }
        }
    }

    private fun loadFiles(journalUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val journalFolder = DocumentFile.fromTreeUri(getApplication(), Uri.parse(journalUri))
            val filesList = mutableListOf<FileInfo>()
            journalFolder?.listFiles()?.forEach { file ->
                if (file.isFile) {
                    filesList.add(FileInfo(name = file.name ?: "Unknown", uri = file.uri.toString()))
                }
            }
            _state.update { it.copy(files = filesList) }
        }
    }
}

@Composable
fun SelectFolderScreen(onIntent: (MainIntent) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            onIntent(MainIntent.SelectRootFolder(uri.toString()))
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = { launcher.launch(null) }) {
            Text("Select Folder")
        }
    }
}

@Composable
fun JournalListScreen(state: MainState, onIntent: (MainIntent) -> Unit) {
    val rootUri = (state.currentScreen as? Screen.JournalList)?.rootUri ?: return
    var showNewJournalDialog by remember { mutableStateOf(false) }
    var newJournalName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Button(onClick = { showNewJournalDialog = true }, modifier = Modifier.padding(16.dp)) {
            Text("New Journal")
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.journals) { journal ->
                Text(
                    text = journal.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onIntent(MainIntent.OpenJournal(journal.uri)) }
                        .padding(16.dp)
                )
            }
        }
    }

    if (showNewJournalDialog) {
        AlertDialog(
            onDismissRequest = { showNewJournalDialog = false },
            title = { Text("New Journal") },
            text = {
                OutlinedTextField(
                    value = newJournalName,
                    onValueChange = { newJournalName = it },
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onIntent(MainIntent.CreateJournal(rootUri, newJournalName))
                    showNewJournalDialog = false
                    newJournalName = ""
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewJournalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(state: MainState, onIntent: (MainIntent) -> Unit) {
    val journalUri = (state.currentScreen as? Screen.FileList)?.journalUri ?: return
    val context = LocalContext.current
    val journalFolder = remember(journalUri) { DocumentFile.fromTreeUri(context, Uri.parse(journalUri)) }

    BackHandler { onIntent(MainIntent.NavigateBack) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(journalFolder?.name ?: "Files") },
                navigationIcon = {
                    IconButton(onClick = { onIntent(MainIntent.NavigateBack) }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.files) { file ->
                    Text(
                        text = file.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onIntent(MainIntent.OpenFile(file.uri, journalUri)) }
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ReportsScreen(state: MainState) {
    val mainFile = state.files.find { it.name == "main.beancount.md" }
        ?: state.files.find { it.name == "main.beancount" }

    if (mainFile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No main.beancount.md found in journal.")
        }
        return
    }

    val context = LocalContext.current
    var balances by remember { mutableStateOf<List<AccountBalanceUi>?>(null) }

    LaunchedEffect(mainFile.uri) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val text = context.contentResolver.openInputStream(Uri.parse(mainFile.uri))?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                } ?: ""
                val computedBalances = calculateTrialBalances(text)
                balances = computedBalances
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error reading balances: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                balances = emptyList()
            }
        }
    }

    val currentBalances = balances
    if (currentBalances == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading balances...")
        }
    } else if (currentBalances.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No balances found or error reading file.")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            items(currentBalances) { balance ->
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(text = balance.account, fontWeight = FontWeight.Bold)
                    balance.balances.forEach { (currency, amount) ->
                        Text(text = "$amount $currency")
                    }
                }
            }
        }
    }
}

fun mapParseTreeToNodes(tree: ParseTree): List<DocumentNode> {
    val levelCounts = mutableMapOf<Int, Int>()
    var currentLevel = 0

    return tree.nodes.map { node ->
        when (node) {
            is AstNode.Heading -> {
                val level = node.level.toInt()
                if (level < currentLevel) {
                    for (i in level + 1..currentLevel) {
                        levelCounts.remove(i)
                    }
                }
                currentLevel = level
                val count = levelCounts.getOrDefault(level, 0)
                levelCounts[level] = count + 1

                val finalTreeIndex = mutableListOf<Int>()
                for (i in 1..level) {
                    finalTreeIndex.add(maxOf(0, levelCounts.getOrDefault(i, 1) - 1))
                }

                HeadingNode(
                    level = level,
                    content = node.content,
                    span = Span(node.span.start.toInt(), node.span.end.toInt()),
                    treeIndex = finalTreeIndex
                )
            }
            is AstNode.Paragraph -> ParagraphNode(content = node.content, span = Span(node.span.start.toInt(), node.span.end.toInt()))
            is AstNode.CodeBlock -> CodeBlockNode(content = node.content, span = Span(node.span.start.toInt(), node.span.end.toInt()))
            is AstNode.Beancount -> BeancountNode(span = Span(node.span.start.toInt(), node.span.end.toInt()))
        }
    }
}


class MainActivity : ComponentActivity() {

    init {
        System.loadLibrary("adzuki")
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MainViewModel = viewModel()
                    val state by viewModel.state.collectAsState()

                    when (val currentScreen = state.currentScreen) {
                        is Screen.SelectFolder -> SelectFolderScreen(
                            onIntent = viewModel::processIntent
                        )
                        is Screen.JournalList -> JournalListScreen(
                            state = state,
                            onIntent = viewModel::processIntent
                        )
                        is Screen.FileList -> FileListScreen(
                            state = state,
                            onIntent = viewModel::processIntent
                        )
                        is Screen.Editor -> {
                            var selectedTab by remember { mutableIntStateOf(0) }
                            val fileUri = currentScreen.fileUri
                            val context = LocalContext.current
                            val uri = Uri.parse(fileUri)
                            val file = DocumentFile.fromSingleUri(context, uri)

                            val initialText = remember(fileUri) {
                                try {
                                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                        inputStream.bufferedReader().use { it.readText() }
                                    } ?: ""
                                } catch (e: Exception) {
                                    ""
                                }
                            }

                            val coroutineScope = rememberCoroutineScope()
                            val docViewModel = remember(fileUri, initialText) {
                                DocumentViewModel(
                                    initialState = DocumentState(text = initialText),
                                    coroutineScope = coroutineScope,
                                    documentId = fileUri,
                                    foldStateRepository = (application as AdzukiApplication).foldStateRepository,
                                    parserProxy = { text ->
                                        val parseTree = parseToTree(text)
                                        mapParseTreeToNodes(parseTree)
                                    },
                                    onSave = { newText ->
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                                                    outputStream.write(newText.toByteArray())
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                coroutineScope.launch(Dispatchers.Main) {
                                                    Toast.makeText(context, "Error saving document: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            val editorState by docViewModel.state.collectAsState()

                            BackHandler {
                                docViewModel.processIntent(tech.bananajuice.adzuki.shared.mvi.DocumentIntent.SaveNow)
                                viewModel.processIntent(MainIntent.NavigateBack)
                            }

                            Scaffold(
                                topBar = {
                                    @OptIn(ExperimentalMaterial3Api::class)
                                    TopAppBar(
                                        title = { Text(file?.name ?: "Editor") },
                                        navigationIcon = {
                                            IconButton(onClick = {
                                                docViewModel.processIntent(tech.bananajuice.adzuki.shared.mvi.DocumentIntent.SaveNow)
                                                viewModel.processIntent(MainIntent.NavigateBack)
                                            }) {
                                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                                            }
                                        }
                                    )
                                }
                            ) { padding ->
                                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                                    TabRow(selectedTabIndex = selectedTab) {
                                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Editor") })
                                        Tab(selected = selectedTab == 1, onClick = {
                                            docViewModel.processIntent(tech.bananajuice.adzuki.shared.mvi.DocumentIntent.SaveNow)
                                            selectedTab = 1
                                        }, text = { Text("Reports") })
                                    }
                                    if (selectedTab == 0) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            DocumentEditor(
                                                state = editorState,
                                                onIntent = docViewModel::processIntent
                                            )
                                        }
                                    } else if (selectedTab == 1) {
                                        ReportsScreen(state = state)
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
