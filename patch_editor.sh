#!/bin/bash
cat << 'INNER_EOF' > /tmp/patch_editor.txt
<<<<<<< SEARCH
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
=======
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(state: MainState, profileId: String?, onIntent: (MainIntent) -> Unit) {
    val existingProfile = state.profiles.find { it.id == profileId }
    var name by remember { mutableStateOf(existingProfile?.name ?: "") }
    var folderUri by remember { mutableStateOf(existingProfile?.folderUri) }
    var isDefault by remember { mutableStateOf(existingProfile?.id != null && existingProfile.id == state.defaultProfileId) }
    val context = LocalContext.current

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                folderUri = uri.toString()
                if (name.isBlank()) {
                    val documentFile = DocumentFile.fromTreeUri(context, uri)
                    name = documentFile?.name ?: "New Profile"
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to take permission", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (profileId == null) "New Profile" else "Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = { onIntent(MainIntent.NavigateBack) }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (profileId != null) {
                        IconButton(onClick = { onIntent(MainIntent.DeleteProfile(profileId)) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { folderLauncher.launch(null) }) {
                Text(if (folderUri == null) "Select Folder" else "Change Folder")
            }
            if (folderUri != null) {
                Text("Selected: ${Uri.parse(folderUri).lastPathSegment ?: "Folder"}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isDefault, onCheckedChange = { isDefault = it })
                Text("Set as default profile")
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    if (name.isNotBlank() && folderUri != null) {
                        val profile = Profile(id = profileId ?: java.util.UUID.randomUUID().toString(), name = name, folderUri = folderUri!!)
                        onIntent(MainIntent.SaveProfile(profile, isDefault))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && folderUri != null
            ) {
                Text("Save")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalListScreen(state: MainState, onIntent: (MainIntent) -> Unit) {
    val context = LocalContext.current
>>>>>>> REPLACE
INNER_EOF
