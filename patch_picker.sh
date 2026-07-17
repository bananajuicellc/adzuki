#!/bin/bash
cat << 'INNER_EOF' > /tmp/patch_picker.txt
<<<<<<< SEARCH
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
=======
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePickerScreen(state: MainState, onIntent: (MainIntent) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Select Profile") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onIntent(MainIntent.StartProfileEditor(null)) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Profile")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(state.profiles) { profile ->
                val firstLetter = profile.name.firstOrNull()?.uppercase() ?: "?"
                val colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.error
                )
                val colorIndex = kotlin.math.abs(profile.name.hashCode()) % colors.size
                val circleColor = colors[colorIndex]

                ListItem(
                    headlineContent = { Text(profile.name) },
                    supportingContent = { Text(if (profile.id == state.defaultProfileId) "Default" else "") },
                    leadingContent = {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(40.dp)
                                .androidx.compose.foundation.background(color = circleColor, shape = androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = firstLetter,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    modifier = Modifier.clickable { onIntent(MainIntent.SelectProfile(profile.id)) },
                    trailingContent = {
                        IconButton(onClick = { onIntent(MainIntent.StartProfileEditor(profile.id)) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit Profile")
                        }
                    }
                )
            }
        }
    }
}
>>>>>>> REPLACE
INNER_EOF
