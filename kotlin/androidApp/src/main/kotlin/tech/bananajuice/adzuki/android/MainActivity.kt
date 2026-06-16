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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.bananajuice.adzuki.shared.automerge.*
import tech.bananajuice.adzuki.shared.automerge.OptionDirective
import tech.bananajuice.adzuki.shared.automerge.CloseDirective
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
    data class RemoveRootFolder(val uri: String) : MainIntent()
    data class OpenEditor(val uri: String) : MainIntent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    private val prefs = application.getSharedPreferences("adzuki_prefs", Context.MODE_PRIVATE)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val savedFolders = prefs.getStringSet("root_folders", emptySet())?.toList() ?: emptyList()
            _state.update {
                it.copy(
                    rootFolders = savedFolders,
                    currentScreen = if (savedFolders.isNotEmpty()) Screen.JournalList else Screen.SelectFolder
                )
            }
        }
    }

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
                viewModelScope.launch(Dispatchers.IO) {
                    prefs.edit().putStringSet("root_folders", _state.value.rootFolders.toSet()).apply()
                }
            }
            is MainIntent.RemoveRootFolder -> {
                _state.update {
                    val folders = it.rootFolders.toMutableList()
                    folders.remove(intent.uri)
                    it.copy(
                        rootFolders = folders,
                        currentScreen = if (folders.isEmpty()) Screen.SelectFolder else it.currentScreen
                    )
                }
                viewModelScope.launch(Dispatchers.IO) {
                    prefs.edit().putStringSet("root_folders", _state.value.rootFolders.toSet()).apply()
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
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
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
        Button(onClick = { folderLauncher.launch(null) }) {
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

    val createDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
            }
            onIntent(MainIntent.OpenEditor(uri.toString()))
        }
    }

    var beancountContentToImport by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }

    val importSaveDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && beancountContentToImport != null) {
            try {
                val doc = importFromBeancount(beancountContentToImport!!)
                val bytes = doc.save()
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }

                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {
                    // Ignore if persistable permission cannot be taken for a newly created document
                }
                onIntent(MainIntent.OpenEditor(uri.toString()))
            } catch (e: Exception) {
                Toast.makeText(context, "Error importing: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        beancountContentToImport = null
    }

    val importPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (text != null) {
                    beancountContentToImport = text
                    importSaveDocLauncher.launch("imported_journal.adzuki")
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read Beancount file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Button(onClick = { createDocLauncher.launch("new_journal.adzuki") }) {
                    Text("New Journal")
                }
                Button(onClick = { importPickerLauncher.launch(arrayOf("*/*")) }) {
                    Text("Import Beancount File")
                }
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.rootFolders) { folderUriStr ->
                    val folderUri = Uri.parse(folderUriStr)
                    var folderName by remember { mutableStateOf<String?>("Loading...") }

                    LaunchedEffect(folderUri) {
                        try {
                            val name = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val documentFile = DocumentFile.fromTreeUri(context, folderUri)
                                documentFile?.name ?: "Unknown Folder"
                            }
                            folderName = name
                        } catch (e: SecurityException) {
                            onIntent(MainIntent.RemoveRootFolder(folderUriStr))
                        }
                    }

                    ListItem(
                        headlineContent = { Text(folderName ?: "Unknown Folder") },
                        modifier = Modifier.clickable { onIntent(MainIntent.OpenFolder(folderUriStr)) }
                    )
                }
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
    var folderName by remember { mutableStateOf<String?>("Loading...") }
    var files by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var folderFile by remember { mutableStateOf<DocumentFile?>(null) }

    LaunchedEffect(folderUri) {
        try {
            val (file, name, filteredFiles) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val file = DocumentFile.fromTreeUri(context, folderUri)
                val name = file?.name ?: "Files"
                val filteredFiles = file?.listFiles()?.filter { it.isFile && it.name?.endsWith(".adzuki") == true } ?: emptyList()
                Triple(file, name, filteredFiles)
            }
            folderFile = file
            folderName = name
            files = filteredFiles
        } catch (e: SecurityException) {
            onIntent(MainIntent.RemoveRootFolder(folderUriStr))
            onIntent(MainIntent.NavigateBack)
        }
    }

    BackHandler {
        onIntent(MainIntent.NavigateBack)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folderName ?: "Files") },
                navigationIcon = {
                    IconButton(onClick = { onIntent(MainIntent.NavigateBack) }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val newFile = folderFile?.createFile("application/octet-stream", "main.adzuki")
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditDialog(
    account: AccountDirective?,
    onSave: (AccountDirective) -> Unit,
    onDismiss: () -> Unit,
    directives: List<Directive>
) {
    var date by remember { mutableStateOf(account?.date ?: java.time.LocalDate.now().toString()) }
    var name by remember { mutableStateOf(account?.name ?: "") }
    var currencies by remember { mutableStateOf(account?.constraintCurrencies?.joinToString(",") ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    val accountSuggestions = remember(directives) { getAccountSuggestions(directives) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = remember(date) {
                try {
                    java.time.LocalDate.parse(date)
                        .atStartOfDay(java.time.ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli()
                } catch (e: java.time.format.DateTimeParseException) {
                    java.time.LocalDate.now()
                        .atStartOfDay(java.time.ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli()
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        date = java.time.Instant.ofEpochMilli(it)
                            .atZone(java.time.ZoneId.of("UTC"))
                            .toLocalDate()
                            .toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "Add Account" else "Edit Account") },
        text = {
            Column {
                OutlinedTextField(
                    value = date,
                    onValueChange = {},
                    label = { Text("Date") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Filled.DateRange, contentDescription = "Select Date")
                        }
                    }
                )
                AutocompleteAccountField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Account Name",
                    suggestions = accountSuggestions
                )
                OutlinedTextField(
                    value = currencies,
                    onValueChange = { currencies = it },
                    label = { Text("Currencies (comma separated)") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(AccountDirective(account?.id ?: -1L, date, name, currencies.split(",").map { it.trim() }.filter { it.isNotEmpty() }))
            }) { Text("Save") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloseEditDialog(
    closeDirective: CloseDirective?,
    onSave: (CloseDirective) -> Unit,
    onDismiss: () -> Unit,
    directives: List<Directive>
) {
    var date by remember { mutableStateOf(closeDirective?.date ?: java.time.LocalDate.now().toString()) }
    var accountName by remember { mutableStateOf(closeDirective?.account ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    val accountSuggestions = remember(directives) { getAccountSuggestions(directives) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = java.time.LocalDate.parse(date)
                .atStartOfDay(java.time.ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        date = java.time.Instant.ofEpochMilli(it)
                            .atZone(java.time.ZoneId.of("UTC"))
                            .toLocalDate()
                            .toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (closeDirective == null) "Close Account" else "Edit Close Account") },
        text = {
            Column {
                OutlinedTextField(
                    value = date,
                    onValueChange = {},
                    label = { Text("Date") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Filled.DateRange, contentDescription = "Select Date")
                        }
                    }
                )
                AutocompleteAccountField(
                    value = accountName,
                    onValueChange = { accountName = it },
                    label = "Account Name",
                    suggestions = accountSuggestions
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(CloseDirective(closeDirective?.id ?: -1L, date, accountName))
            }) { Text("Save") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun OptionEditDialog(
    option: OptionDirective?,
    onSave: (OptionDirective) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(option?.name ?: "") }
    var value by remember { mutableStateOf(option?.value ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (option == null) "Add Option" else "Edit Option") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") }
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Value") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(OptionDirective(option?.id ?: -1L, name, value))
            }) { Text("Save") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditDialog(
    transaction: TransactionDirective?,
    onSave: (TransactionDirective) -> Unit,
    onDismiss: () -> Unit,
    directives: List<Directive>
) {
    var date by remember { mutableStateOf(transaction?.date ?: java.time.LocalDate.now().toString()) }
    var payee by remember { mutableStateOf(transaction?.payee ?: "") }
    var memo by remember { mutableStateOf(transaction?.memo ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Convert current postings to mutable state list for UI editing
    val postings = remember { mutableStateListOf(*((transaction?.postings ?: emptyList()).toTypedArray())) }

    if (showDatePicker) {
        val parsedDateMillis = remember(date) {
            try {
                java.time.LocalDate.parse(date).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            } catch (e: java.time.format.DateTimeParseException) {
                null
            }
        }

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = parsedDateMillis
        )

        LaunchedEffect(showDatePicker) {
            if (parsedDateMillis == null) {
                 Toast.makeText(context, "Error parsing date: Invalid format", Toast.LENGTH_SHORT).show()
            }
        }
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val localDate = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                        date = localDate.toString()
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (transaction != null) "Edit Transaction" else "Add Transaction") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Date") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Filled.DateRange, contentDescription = "Select Date")
                        }
                    }
                )
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


fun getAccountSuggestions(directives: List<Directive>): List<String> {
    val roots = mutableMapOf(
        "name_assets" to "Assets",
        "name_liabilities" to "Liabilities",
        "name_equity" to "Equity",
        "name_income" to "Income",
        "name_expenses" to "Expenses"
    )
    directives.filterIsInstance<OptionDirective>().forEach { opt ->
        if (roots.containsKey(opt.name)) {
            roots[opt.name] = opt.value
        }
    }

    val suggestions = mutableSetOf<String>()
    suggestions.addAll(roots.values)

    directives.forEach { dir ->
        when (dir) {
            is AccountDirective -> suggestions.add(dir.name)
            is CloseDirective -> suggestions.add(dir.account)
            is TransactionDirective -> dir.postings.forEach { p -> suggestions.add(p.account) }
            else -> {}
        }
    }
    return suggestions.toList().sorted()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutocompleteAccountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            modifier = Modifier.menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        val filtered = suggestions.filter { it.contains(value, ignoreCase = true) }
        if (filtered.isNotEmpty() && expanded) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                filtered.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            onValueChange(selectionOption)
                            expanded = false
                        }
                    )
                }
            }
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
                            var fileName by remember { mutableStateOf<String?>("Loading...") }

                            LaunchedEffect(uri) {
                                try {
                                    val name = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        val file = DocumentFile.fromSingleUri(context, uri)
                                        file?.name ?: "Editor"
                                    }
                                    fileName = name
                                } catch (e: SecurityException) {
                                    viewModel.processIntent(MainIntent.NavigateBack)
                                }
                            }

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

                            val exportDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { exportUri ->
                                if (exportUri != null) {
                                    try {
                                        val exportText = exportToBeancount(docState.directives)
                                        context.contentResolver.openOutputStream(exportUri, "wt")?.use { it.write(exportText.toByteArray()) }
                                        Toast.makeText(context, "Exported successfully", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

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
                                        title = { Text(fileName ?: "Editor") },
                                        navigationIcon = {
                                            IconButton(onClick = { viewModel.processIntent(MainIntent.NavigateBack) }) {
                                                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                                            }
                                        },
                                        actions = {
                                            IconButton(onClick = { exportDocLauncher.launch("exported_journal.beancount") }) {
                                                Icon(Icons.Filled.Share, contentDescription = "Export to Beancount")
                                            }
                                        }
                                    )
                                },
                                floatingActionButton = {
                                    if (selectedTab == 0) {
                                        var showFabMenu by remember { mutableStateOf(false) }
                                        Box {
                                            FloatingActionButton(onClick = { showFabMenu = !showFabMenu }) {
                                                Icon(Icons.Filled.Add, contentDescription = "Add Directive")
                                            }
                                            DropdownMenu(
                                                expanded = showFabMenu,
                                                onDismissRequest = { showFabMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Transaction") },
                                                    onClick = {
                                                        showFabMenu = false
                                                        docViewModel.processIntent(DocumentIntent.StartEditingTransaction(null))
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Open Account") },
                                                    onClick = {
                                                        showFabMenu = false
                                                        docViewModel.processIntent(DocumentIntent.StartEditingAccount(null))
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Close Account") },
                                                    onClick = {
                                                        showFabMenu = false
                                                        docViewModel.processIntent(DocumentIntent.StartEditingClose(null))
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Option") },
                                                    onClick = {
                                                        showFabMenu = false
                                                        docViewModel.processIntent(DocumentIntent.StartEditingOption(null))
                                                    }
                                                )
                                            }
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
                                                        is OptionDirective -> {
                                                            ListItem(
                                                                headlineContent = { Text("Option: ${dir.name}") },
                                                                supportingContent = { Text("Value: ${dir.value}") },
                                                                trailingContent = {
                                                                    IconButton(onClick = { docViewModel.processIntent(DocumentIntent.DeleteDirective(dir.id)) }) {
                                                                        Icon(Icons.Filled.Delete, contentDescription = "Delete Option")
                                                                    }
                                                                }
                                                            )
                                                        }
                                                        is CloseDirective -> {
                                                            ListItem(
                                                                headlineContent = { Text("Close: ${dir.account}") },
                                                                supportingContent = { Text("Date: ${dir.date}") },
                                                                trailingContent = {
                                                                    IconButton(onClick = { docViewModel.processIntent(DocumentIntent.DeleteDirective(dir.id)) }) {
                                                                        Icon(Icons.Filled.Delete, contentDescription = "Delete Close Directive")
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
                                    onDismiss = { docViewModel.processIntent(DocumentIntent.CancelEditingTransaction) },
                                    directives = docState.directives
                                )
                            }
                            if (docState.isEditingAccount) {
                                AccountEditDialog(
                                    account = docState.accountBeingEdited,
                                    onSave = { docViewModel.processIntent(DocumentIntent.SaveAccount(it)) },
                                    onDismiss = { docViewModel.processIntent(DocumentIntent.CancelEditingAccount) },
                                    directives = docState.directives
                                )
                            }
                            if (docState.isEditingClose) {
                                CloseEditDialog(
                                    closeDirective = docState.closeBeingEdited,
                                    onSave = { docViewModel.processIntent(DocumentIntent.SaveClose(it)) },
                                    onDismiss = { docViewModel.processIntent(DocumentIntent.CancelEditingClose) },
                                    directives = docState.directives
                                )
                            }
                            if (docState.isEditingOption) {
                                OptionEditDialog(
                                    option = docState.optionBeingEdited,
                                    onSave = { docViewModel.processIntent(DocumentIntent.SaveOption(it)) },
                                    onDismiss = { docViewModel.processIntent(DocumentIntent.CancelEditingOption) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
