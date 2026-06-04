package tech.bananajuice.adzuki.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.bananajuice.adzuki.shared.automerge.*

sealed class Screen {
    object SelectFolder : Screen()
    object JournalList : Screen()
    data class FileList(val folderUri: String) : Screen()
    data class Editor(val fileUri: String) : Screen()
}

data class MainState(
    val currentScreen: Screen = Screen.SelectFolder,
    val openFolderUri: String? = null,
    val rootFolders: List<String> = emptyList()
)

sealed class MainIntent {
    data class OpenFolder(val uri: String) : MainIntent()
    object NavigateBack : MainIntent()
    data class SelectRootFolder(val uri: String) : MainIntent()
    data class OpenEditor(val uri: String) : MainIntent()
}

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    fun processIntent(intent: MainIntent) {
        when (intent) {
            is MainIntent.SelectRootFolder -> {
                _state.update {
                    val folders = it.rootFolders.toMutableList()
                    if (!folders.contains(intent.uri)) {
                        folders.add(intent.uri)
                    }
                    it.copy(rootFolders = folders, currentScreen = Screen.JournalList)
                }
            }
            is MainIntent.OpenFolder -> {
                _state.update {
                    it.copy(currentScreen = Screen.FileList(intent.uri), openFolderUri = intent.uri)
                }
            }
            is MainIntent.OpenEditor -> {
                _state.update {
                    it.copy(currentScreen = Screen.Editor(intent.uri))
                }
            }
            is MainIntent.NavigateBack -> {
                _state.update {
                    when (it.currentScreen) {
                        is Screen.Editor -> Screen.FileList(it.openFolderUri!!)
                        is Screen.FileList -> Screen.JournalList
                        else -> Screen.SelectFolder
                    }.let { newScreen ->
                        it.copy(currentScreen = newScreen)
                    }
                }
            }
        }
    }
}

@Composable
fun SelectFolderScreen(onIntent: (MainIntent) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            onIntent(MainIntent.SelectRootFolder(uri.toString()))
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = { launcher.launch(null) }) {
            Text("Select Journal Folder")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalListScreen(state: MainState, onIntent: (MainIntent) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            onIntent(MainIntent.SelectRootFolder(uri.toString()))
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Journals") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { launcher.launch(null) }) {
                Icon(Icons.Filled.FolderOpen, contentDescription = "Open Folder")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(state.rootFolders) { folderUriStr ->
                val folderUri = Uri.parse(folderUriStr)
                val documentFile = DocumentFile.fromTreeUri(context, folderUri)
                ListItem(
                    headlineContent = { Text(documentFile?.name ?: "Unknown Folder") },
                    modifier = Modifier.clickable { onIntent(MainIntent.OpenFolder(folderUriStr)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(state: MainState, onIntent: (MainIntent) -> Unit) {
    val context = LocalContext.current
    val folderUriStr = (state.currentScreen as Screen.FileList).folderUri
    val folderUri = Uri.parse(folderUriStr)
    val folderFile = DocumentFile.fromTreeUri(context, folderUri)
    val files = folderFile?.listFiles()?.filter { it.isFile } ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folderFile?.name ?: "Files") },
                navigationIcon = {
                    IconButton(onClick = { onIntent(MainIntent.NavigateBack) }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val newFile = folderFile?.createFile("application/octet-stream", "main.am")
                if (newFile != null) {
                    onIntent(MainIntent.OpenEditor(newFile.uri.toString()))
                }
            }) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "New File")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(files) { file ->
                ListItem(
                    headlineContent = { Text(file.name ?: "Unknown") },
                    modifier = Modifier.clickable { onIntent(MainIntent.OpenEditor(file.uri.toString())) }
                )
            }
        }
    }
}

class MainActivity : ComponentActivity() {

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

                            var doc by remember { mutableStateOf<AutomergeDocument?>(null) }
                            var directives by remember { mutableStateOf<List<Directive>>(emptyList()) }
                            val coroutineScope = rememberCoroutineScope()

                            LaunchedEffect(fileUri) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                                        val newDoc = AutomergeDocument(bytes)
                                        doc = newDoc
                                        directives = newDoc.getDirectives()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        launch(Dispatchers.Main) {
                                            Toast.makeText(context, "Error loading document: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                        doc = AutomergeDocument()
                                    }
                                }
                            }

                            val saveDoc = {
                                doc?.let { currentDoc ->
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val bytes = currentDoc.save()
                                            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                                            val updatedDirectives = currentDoc.getDirectives()
                                            launch(Dispatchers.Main) {
                                                directives = updatedDirectives
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            launch(Dispatchers.Main) {
                                                Toast.makeText(context, "Error saving document: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }

                            var showAddMenu by remember { mutableStateOf(false) }
                            var showAddAccountDialog by remember { mutableStateOf(false) }
                            var showAddTransactionDialog by remember { mutableStateOf(false) }

                            Scaffold(
                                topBar = {
                                    @OptIn(ExperimentalMaterial3Api::class)
                                    TopAppBar(
                                        title = { Text(file?.name ?: "Editor") },
                                        navigationIcon = {
                                            IconButton(onClick = { viewModel.processIntent(MainIntent.NavigateBack) }) {
                                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                                            }
                                        }
                                    )
                                },
                                floatingActionButton = {
                                    if (selectedTab == 0) {
                                        Box {
                                            FloatingActionButton(onClick = { showAddMenu = true }) {
                                                Icon(Icons.Filled.Add, contentDescription = "Add")
                                            }
                                            DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                                                DropdownMenuItem(text = { Text("Account") }, onClick = { showAddMenu = false; showAddAccountDialog = true })
                                                DropdownMenuItem(text = { Text("Transaction") }, onClick = { showAddMenu = false; showAddTransactionDialog = true })
                                            }
                                        }
                                    }
                                }
                            ) { padding ->
                                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                                    TabRow(selectedTabIndex = selectedTab) {
                                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Directives") })
                                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Reports") })
                                    }
                                    if (selectedTab == 0) {
                                        if (doc == null) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("Loading...")
                                            }
                                        } else {
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                items(directives) { dir ->
                                                    when (dir) {
                                                        is AccountDirective -> {
                                                            ListItem(
                                                                headlineContent = { Text("Account: ${dir.name}") },
                                                                supportingContent = { Text("Date: ${dir.date} | Currencies: ${dir.constraintCurrencies.joinToString(", ")}") }
                                                            )
                                                        }
                                                        is TransactionDirective -> {
                                                            ListItem(
                                                                headlineContent = { Text("Transaction: ${dir.payee}") },
                                                                supportingContent = {
                                                                    Column {
                                                                        Text("Date: ${dir.date} | Memo: ${dir.memo}")
                                                                        dir.postings.forEach { p ->
                                                                            Text("  ${p.account}: ${p.amount} ${p.currency}", style = MaterialTheme.typography.bodySmall)
                                                                        }
                                                                    }
                                                                }
                                                            )
                                                        }
                                                    }
                                                    Divider()
                                                }
                                            }
                                        }
                                    } else {
                                        ReportsScreen(directives)
                                    }
                                }
                            }

                            if (showAddAccountDialog) {
                                var name by remember { mutableStateOf("") }
                                var date by remember { mutableStateOf("2024-01-01") }
                                var currencies by remember { mutableStateOf("USD") }
                                AlertDialog(
                                    onDismissRequest = { showAddAccountDialog = false },
                                    title = { Text("Add Account") },
                                    text = {
                                        Column {
                                            OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date") })
                                            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                                            OutlinedTextField(value = currencies, onValueChange = { currencies = it }, label = { Text("Currencies (comma separated)") })
                                        }
                                    },
                                    confirmButton = {
                                        Button(onClick = {
                                            doc?.addAccount(AccountDirective(date, name, currencies.split(",").map { it.trim() }))
                                            saveDoc()
                                            showAddAccountDialog = false
                                        }) { Text("Add") }
                                    },
                                    dismissButton = {
                                        Button(onClick = { showAddAccountDialog = false }) { Text("Cancel") }
                                    }
                                )
                            }

                            if (showAddTransactionDialog) {
                                var date by remember { mutableStateOf("2024-01-01") }
                                var payee by remember { mutableStateOf("") }
                                var memo by remember { mutableStateOf("") }
                                AlertDialog(
                                    onDismissRequest = { showAddTransactionDialog = false },
                                    title = { Text("Add Transaction") },
                                    text = {
                                        Column {
                                            OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date") })
                                            OutlinedTextField(value = payee, onValueChange = { payee = it }, label = { Text("Payee") })
                                            OutlinedTextField(value = memo, onValueChange = { memo = it }, label = { Text("Memo") })
                                            // Simplification: hardcode a simple posting entry for now to test functionality
                                            Text("A default posting will be added (Assets:Checking -10 USD, Expenses:Food 10 USD)")
                                        }
                                    },
                                    confirmButton = {
                                        Button(onClick = {
                                            val postings = listOf(
                                                Posting("Assets:Checking", "-10", "USD"),
                                                Posting("Expenses:Food", "10", "USD")
                                            )
                                            doc?.addTransaction(TransactionDirective(date, payee, memo, postings))
                                            saveDoc()
                                            showAddTransactionDialog = false
                                        }) { Text("Add") }
                                    },
                                    dismissButton = {
                                        Button(onClick = { showAddTransactionDialog = false }) { Text("Cancel") }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
