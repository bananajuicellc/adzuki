package tech.bananajuice.adzuki.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
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
import tech.bananajuice.adzuki.shared.mvi.*

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

    BackHandler {
        onIntent(MainIntent.NavigateBack)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folderFile?.name ?: "Files") },
                navigationIcon = {
                    IconButton(onClick = { onIntent(MainIntent.NavigateBack) }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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

@Composable
fun TransactionEditDialog(
    transaction: TransactionDirective?,
    onSave: (TransactionDirective) -> Unit,
    onDismiss: () -> Unit
) {
    var date by remember { mutableStateOf(transaction?.date ?: "2024-01-01") }
    var payee by remember { mutableStateOf(transaction?.payee ?: "") }
    var memo by remember { mutableStateOf(transaction?.memo ?: "") }

    // Convert current postings to mutable state list for UI editing
    val postings = remember { mutableStateListOf(*((transaction?.postings ?: emptyList()).toTypedArray())) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (transaction != null) "Edit Transaction" else "Add Transaction") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date") })
                OutlinedTextField(value = payee, onValueChange = { payee = it }, label = { Text("Payee") })
                OutlinedTextField(value = memo, onValueChange = { memo = it }, label = { Text("Memo") })

                Spacer(modifier = Modifier.height(8.dp))
                Text("Postings", fontWeight = FontWeight.Bold)

                LazyColumn(modifier = Modifier.height(200.dp)) {
                    items(postings.size) { i ->
                        val p = postings[i]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = p.account,
                                onValueChange = { postings[i] = p.copy(account = it) },
                                label = { Text("Acct") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = p.amount,
                                onValueChange = { postings[i] = p.copy(amount = it) },
                                label = { Text("Amt") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = p.currency,
                                onValueChange = { postings[i] = p.copy(currency = it) },
                                label = { Text("Cur") },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { postings.removeAt(i) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete Posting")
                            }
                        }
                    }
                    item {
                        Button(onClick = { postings.add(Posting("", "", "")) }) {
                            Text("Add Posting")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val newTxn = TransactionDirective(
                    id = transaction?.id ?: -1L,
                    date = date,
                    payee = payee,
                    memo = memo,
                    postings = postings.toList()
                )
                onSave(newTxn)
            }) { Text("Save") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
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

                            val coroutineScope = rememberCoroutineScope()

                            val docViewModel = remember(fileUri) {
                                DocumentViewModel(
                                    coroutineScope = coroutineScope,
                                    loadDocumentBytes = {
                                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                                    },
                                    saveDocumentBytes = { bytes ->
                                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                                    }
                                )
                            }
                            val docState by docViewModel.state.collectAsState()

                            BackHandler {
                                viewModel.processIntent(MainIntent.NavigateBack)
                            }

                            docState.errorMessage?.let { msg ->
                                LaunchedEffect(msg) {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    docViewModel.processIntent(DocumentIntent.DismissError)
                                }
                            }

                            Scaffold(
                                topBar = {
                                    @OptIn(ExperimentalMaterial3Api::class)
                                    TopAppBar(
                                        title = { Text(file?.name ?: "Editor") },
                                        navigationIcon = {
                                            IconButton(onClick = { viewModel.processIntent(MainIntent.NavigateBack) }) {
                                                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                                            }
                                        }
                                    )
                                },
                                floatingActionButton = {
                                    if (selectedTab == 0) {
                                        FloatingActionButton(onClick = {
                                            docViewModel.processIntent(DocumentIntent.StartEditingTransaction(null))
                                        }) {
                                            Icon(Icons.Filled.Add, contentDescription = "Add Transaction")
                                        }
                                    }
                                }
                            ) { padding ->
                                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Directives") })
                                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Reports") })
                                    }
                                    if (selectedTab == 0) {
                                        if (docState.isLoading) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("Loading...")
                                            }
                                        } else {
                                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                                items(docState.directives) { dir ->
                                                    when (dir) {
                                                        is AccountDirective -> {
                                                            ListItem(
                                                                headlineContent = { Text("Account: ${dir.name}") },
                                                                supportingContent = { Text("Date: ${dir.date} | Currencies: ${dir.constraintCurrencies.joinToString(", ")}") },
                                                                trailingContent = {
                                                                    IconButton(onClick = { docViewModel.processIntent(DocumentIntent.DeleteDirective(dir.id)) }) {
                                                                        Icon(Icons.Filled.Delete, contentDescription = "Delete Account")
                                                                    }
                                                                }
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
                                                                },
                                                                trailingContent = {
                                                                    Row {
                                                                        IconButton(onClick = { docViewModel.processIntent(DocumentIntent.StartEditingTransaction(dir)) }) {
                                                                            Icon(Icons.Filled.Edit, contentDescription = "Edit Transaction")
                                                                        }
                                                                        IconButton(onClick = { docViewModel.processIntent(DocumentIntent.DeleteDirective(dir.id)) }) {
                                                                            Icon(Icons.Filled.Delete, contentDescription = "Delete Transaction")
                                                                        }
                                                                    }
                                                                }
                                                            )
                                                        }
                                                    }
                                                    HorizontalDivider()
                                                }
                                            }
                                        }
                                    } else {
                                        ReportsScreen(docState.directives)
                                    }
                                }
                            }

                            if (docState.isEditingTransaction) {
                                TransactionEditDialog(
                                    transaction = docState.transactionBeingEdited,
                                    onSave = { docViewModel.processIntent(DocumentIntent.SaveTransaction(it)) },
                                    onDismiss = { docViewModel.processIntent(DocumentIntent.CancelEditingTransaction) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
