#!/bin/bash
cat << 'INNER_EOF' > /tmp/patch_flow.txt
<<<<<<< SEARCH
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalListScreen(state: MainState, onIntent: (MainIntent) -> Unit) {
    val context = LocalContext.current

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
=======
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(state: MainState, onIntent: (MainIntent) -> Unit) {
    val context = LocalContext.current
    val activeProfile = state.profiles.find { it.id == state.activeProfileId }
    val folderUriStr = activeProfile?.folderUri ?: state.openFolderUri

    val folderUri = remember(folderUriStr) { folderUriStr?.let { Uri.parse(it) } }
    var files by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var folderName by remember { mutableStateOf<String?>("Loading...") }
    val folderFile = remember(folderUri) {
        folderUri?.let { DocumentFile.fromTreeUri(context, it) }
    }

    LaunchedEffect(folderFile) {
        if (folderFile != null) {
            try {
                val name = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    folderFile.name ?: "Unknown Folder"
                }
                folderName = name
                val listedFiles = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    folderFile.listFiles().filter { it.isFile && it.name?.endsWith(".adzuki") == true }.toList()
                }
                files = listedFiles
            } catch (e: SecurityException) {
                // If permission is lost, navigate back to profile picker
                onIntent(MainIntent.OpenProfilePicker)
            }
        }
    }

    BackHandler {
        onIntent(MainIntent.OpenProfilePicker)
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
                    // Ignore
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
        topBar = {
            TopAppBar(
                title = { Text(folderName ?: "Files") },
                navigationIcon = {
                    val profileToUse = activeProfile ?: state.profiles.firstOrNull()
                    val firstLetter = profileToUse?.name?.firstOrNull()?.uppercase() ?: "?"
                    val colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.tertiary,
                        MaterialTheme.colorScheme.error
                    )
                    val colorIndex = kotlin.math.abs(profileToUse?.name?.hashCode() ?: 0) % colors.size
                    val circleColor = colors[colorIndex]

                    IconButton(onClick = { onIntent(MainIntent.OpenProfilePicker) }) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(32.dp)
                                .androidx.compose.foundation.background(color = circleColor, shape = androidx.compose.foundation.shape.CircleShape),
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
                val newFile = folderFile?.createFile("application/octet-stream", "main.adzuki")
                if (newFile != null) {
                    onIntent(MainIntent.OpenEditor(newFile.uri.toString()))
                }
            }) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "New File")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = { importPickerLauncher.launch(arrayOf("*/*")) }) {
                    Text("Import Beancount File")
                }
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(files) { file ->
                    ListItem(
                        headlineContent = { Text(file.name ?: "Unknown") },
                        modifier = Modifier.clickable { onIntent(MainIntent.OpenEditor(file.uri.toString())) }
                    )
                }
            }
        }
    }
}
>>>>>>> REPLACE
INNER_EOF
