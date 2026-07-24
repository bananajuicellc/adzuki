package tech.bananajuice.adzuki.shared

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import tech.bananajuice.adzuki.shared.automerge.*
import tech.bananajuice.adzuki.shared.mvi.*
import tech.bananajuice.shared.profilepicker.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    profiles: List<Profile>,
    activeProfileId: String?,
    openFolderUri: String?,
    onOpenProfilePicker: () -> Unit,
    onOpenEditor: (String) -> Unit
) {
    val context = LocalContext.current
    val activeProfile = profiles.find { it.id == activeProfileId }
    val folderUriStr = activeProfile?.folderUri ?: openFolderUri

    val folderUri = remember(folderUriStr) { folderUriStr?.let { Uri.parse(it) } }
    var files by remember(folderUri) { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var folderName by remember(folderUri) { mutableStateOf<String?>("Loading...") }
    var folderFileState by remember(folderUri) { mutableStateOf<DocumentFile?>(null) }

    LaunchedEffect(folderUri) {
        if (folderUri != null) {
            try {
                val result = withContext(Dispatchers.IO) {
                    val folderFile = DocumentFile.fromTreeUri(context, folderUri)
                    if (folderFile != null) {
                        val name = folderFile.name ?: "Unknown Folder"
                        val listedFiles = folderFile.listFiles()
                            .filter { it.isFile && it.name?.endsWith(".adzuki") == true }
                            .toList()
                        Triple(folderFile, name, listedFiles)
                    } else {
                        null
                    }
                }
                if (result != null) {
                    folderFileState = result.first
                    folderName = result.second
                    files = result.third
                } else {
                    onOpenProfilePicker()
                }
            } catch (e: SecurityException) {
                onOpenProfilePicker()
            }
        } else {
            onOpenProfilePicker()
        }
    }

    BackHandler {
        onOpenProfilePicker()
    }

    val exportZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && folderFileState != null) {
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    exportFolderToZip(context, folderFileState!!, out)
                }
                Toast.makeText(context, "Exported folder successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to export: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val createDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                // Ignore
            }
            onOpenEditor(uri.toString())
        }
    }

    var beancountContentToImport by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }

    val importSaveDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val content = beancountContentToImport
        if (uri != null && content != null) {
            try {
                val doc = importFromBeancount(content)
                val bytes = doc.save()
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }

                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {
                    // Ignore
                }
                onOpenEditor(uri.toString())
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
        topBar = {
            TopAppBar(
                title = { Text(folderName ?: "Files") },
                navigationIcon = {
                    val profileToUse = activeProfile ?: profiles.firstOrNull()
                    val firstLetter = profileToUse?.name?.firstOrNull()?.uppercase() ?: "?"
                    val colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.tertiary,
                        MaterialTheme.colorScheme.error
                    )
                    val colorIndex = kotlin.math.abs(profileToUse?.name?.hashCode() ?: 0) % colors.size
                    val circleColor = colors[colorIndex]

                    IconButton(onClick = onOpenProfilePicker) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color = circleColor, shape = androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = firstLetter,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val newFile = folderFileState?.createFile("application/octet-stream", "main.adzuki")
                if (newFile != null) {
                    onOpenEditor(newFile.uri.toString())
                }
            }) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "New File")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Button(onClick = { createDocLauncher.launch("new_journal.adzuki") }) {
                    Text("New Journal")
                }
                Button(onClick = { importPickerLauncher.launch(arrayOf("*/*")) }) {
                    Text("Import Beancount File")
                }
                Button(onClick = { exportZipLauncher?.launch("export.zip") }) {
                    Text("Export All to Zip")
                }
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(files) { file ->
                    ListItem(
                        headlineContent = { Text(file.name ?: "Unknown") },
                        modifier = Modifier.clickable { onOpenEditor(file.uri.toString()) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    fileUri: String,
    onNavigateBack: () -> Unit,
    onNavigateToUri: ((String) -> Unit)? = null,
    folderUri: String? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val uri = Uri.parse(fileUri)
    var fileName by remember { mutableStateOf<String?>("Loading...") }

    val parentUri = android.net.Uri.parse(fileUri)
    LaunchedEffect(parentUri) {
        try {
            val name = withContext(Dispatchers.IO) {
                val file = DocumentFile.fromSingleUri(context, parentUri)
                file?.name ?: "Editor"
            }
            fileName = name
        } catch (e: Exception) {
            onNavigateBack()
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
        onNavigateBack()
    }

    docState.errorMessage?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            docViewModel.processIntent(DocumentIntent.DismissError)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName ?: "Editor") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                            text = { Text("Include") },
                            onClick = {
                                showFabMenu = false
                                docViewModel.processIntent(DocumentIntent.StartEditingInclude(null))
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

                                is IncludeDirective -> {
                                    ListItem(
                                        headlineContent = { Text("Include: ${dir.file}") },
                                        modifier = Modifier.clickable {
                                            // Handle relative link clicking
                                            try {
                                                val parent = folderUri?.let { androidx.documentfile.provider.DocumentFile.fromTreeUri(context, android.net.Uri.parse(it)) }
                                                val target = parent?.findFile(dir.file.replace(".beancount", ".adzuki"))
                                                if (target != null) {
                                                    onNavigateToUri?.invoke(target.uri.toString())
                                                } else {
                                                    android.widget.Toast.makeText(context, "Could not find file ${dir.file}", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } catch(e: Exception) {
                                                android.widget.Toast.makeText(context, "Error opening include: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        trailingContent = {
                                            IconButton(onClick = { docViewModel.processIntent(DocumentIntent.DeleteDirective(dir.id)) }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete Include")
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
                                    val imbalances = remember(dir) { calculateImbalances(dir.postings) }
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
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (imbalances.isNotEmpty()) {
                                                    Icon(
                                                        Icons.Filled.Warning,
                                                        contentDescription = "Unbalanced Transaction",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.padding(end = 8.dp)
                                                    )
                                                }
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

                var resolvedDirectives by remember { mutableStateOf<List<Directive>?>(null) }
                var isResolving by remember { mutableStateOf(true) }

                val parentUri = android.net.Uri.parse(fileUri)
                LaunchedEffect(docState.directives, fileUri) {

                    isResolving = true
                    val allDirectives = mutableListOf<Directive>()
                    val resolvedUris = mutableSetOf<String>()

                    suspend fun resolve(currentUri: android.net.Uri, currentDirectives: List<Directive>) {
                        val uriStr = currentUri.toString()
                        if (resolvedUris.contains(uriStr)) return
                        resolvedUris.add(uriStr)

                        allDirectives.addAll(currentDirectives)

                        for (dir in currentDirectives) {
                            if (dir is IncludeDirective) {
                                try {
                                    val parent = folderUri?.let { androidx.documentfile.provider.DocumentFile.fromTreeUri(context, android.net.Uri.parse(it)) }
                                    val target = parent?.findFile(dir.file.replace(".beancount", ".adzuki"))
                                    if (target != null && !resolvedUris.contains(target.uri.toString())) {
                                        val bytes = context.contentResolver.openInputStream(target.uri)?.use { it.readBytes() } ?: ByteArray(0)
                                        val targetDoc = tech.bananajuice.adzuki.shared.automerge.AutomergeDocument(bytes)
                                        resolve(target.uri, targetDoc.getDirectives())
                                    }
                                } catch (e: Exception) {
                                    // Skip missing files
                                }
                            }
                        }
                    }

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        resolve(parentUri, docState.directives)
                    }
                    resolvedDirectives = allDirectives
                    isResolving = false
                }

                if (isResolving) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Resolving included files...")
                    }
                } else {
                    ReportsScreen(resolvedDirectives ?: emptyList())
                }

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
    if (docState.isEditingInclude) {
        IncludeEditDialog(
            includeDirective = docState.includeBeingEdited,
            onSave = { docViewModel.processIntent(DocumentIntent.SaveInclude(it)) },
            onDismiss = { docViewModel.processIntent(DocumentIntent.CancelEditingInclude) }
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
                AutocompleteTextField(
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
                AutocompleteTextField(
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
fun IncludeEditDialog(
    includeDirective: IncludeDirective?,
    onSave: (IncludeDirective) -> Unit,
    onDismiss: () -> Unit
) {
    var file by remember { mutableStateOf(includeDirective?.file ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (includeDirective == null) "Add Include" else "Edit Include") },
        text = {
            Column {
                OutlinedTextField(
                    value = file,
                    onValueChange = { file = it },
                    label = { Text("File Name (e.g. other.beancount)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(IncludeDirective(includeDirective?.id ?: -1L, file))
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
    val accountSuggestions = remember(directives) { getAccountSuggestions(directives) }
    val currencySuggestions = remember(directives) { getCurrencySuggestions(directives) }

    // Convert current postings to mutable state list for UI editing
    val postings = remember { mutableStateListOf(*((transaction?.postings ?: emptyList()).map { java.util.UUID.randomUUID() to it }.toTypedArray())) }

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
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
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

                Column(modifier = Modifier.fillMaxWidth()) {
                    postings.forEachIndexed { i, (id, p) ->
                        key(id) {
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    AutocompleteTextField(
                                        value = p.account,
                                        onValueChange = { if (i in postings.indices) postings[i] = id to p.copy(account = it) },
                                        label = "Account",
                                        suggestions = accountSuggestions,
                                        modifier = Modifier.width(200.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = p.amount,
                                        onValueChange = { if (i in postings.indices) postings[i] = id to p.copy(amount = it) },
                                        label = { Text("Amount") },
                                        modifier = Modifier.width(100.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    AutocompleteTextField(
                                        value = p.currency,
                                        onValueChange = { if (i in postings.indices) postings[i] = id to p.copy(currency = it) },
                                        label = "Currency",
                                        suggestions = currencySuggestions,
                                        modifier = Modifier.width(200.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(onClick = { if (i in postings.indices) postings.removeAt(i) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete Posting")
                                    }
                                }
                            }
                        }
                    }
                    Button(onClick = { postings.add(java.util.UUID.randomUUID() to Posting("", "", "")) }) {
                        Text("Add Posting")
                    }

                    val imbalances by remember { derivedStateOf { calculateImbalances(postings.map { it.second }) } }
                    if (imbalances.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Transaction is unbalanced. Need:",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                            imbalances.forEach { (currency, imbalance) ->
                                val needed = imbalance.negate()
                                Text(
                                    text = "$needed $currency",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
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
                    postings = postings.map { it.second }.toList()
                )
                onSave(newTxn)
            }) { Text("Save") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


fun calculateImbalances(postings: List<Posting>): Map<String, java.math.BigDecimal> {
    val balances = mutableMapOf<String, java.math.BigDecimal>()
    for (posting in postings) {
        val amountStr = posting.amount.trim()
        if (amountStr.isEmpty()) continue
        val amount = try {
            java.math.BigDecimal(amountStr)
        } catch (e: Exception) {
            continue
        }
        val currentBalance = balances[posting.currency] ?: java.math.BigDecimal.ZERO
        balances[posting.currency] = currentBalance + amount
    }
    return balances.filter { it.value.compareTo(java.math.BigDecimal.ZERO) != 0 }
}

fun getCurrencySuggestions(directives: List<Directive>): List<String> {
    val suggestions = mutableSetOf<String>()
    directives.forEach { dir ->
        when (dir) {
            is OptionDirective -> {
                if (dir.name == "operating_currency" && dir.value.isNotEmpty()) {
                    suggestions.add(dir.value)
                }
            }
            is AccountDirective -> {
                suggestions.addAll(dir.constraintCurrencies.filter { it.isNotEmpty() })
            }
            is TransactionDirective -> {
                dir.postings.forEach { p ->
                    if (p.currency.isNotEmpty()) {
                        suggestions.add(p.currency)
                    }
                }
            }
            else -> {}
        }
    }
    return suggestions.toList().sorted()
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
fun AutocompleteTextField(
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
