package tech.bananajuice.shared.profilepicker

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : BaseProfile> ProfileEditorScreen(
    profileId: String?,
    existingProfile: T?,
    isDefaultInitial: Boolean,
    onNavigateBack: () -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSelectFolder: () -> Unit,
    folderUriStr: String?,
    folderNameToShow: String?,
    onSaveProfile: (id: String?, name: String, folderUri: String, isDefault: Boolean) -> Unit
) {
    var name by remember(existingProfile) { mutableStateOf(existingProfile?.name ?: "") }
    var isDefault by remember(isDefaultInitial) { mutableStateOf(isDefaultInitial) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (profileId == null) "New Profile" else "Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (profileId != null) {
                        IconButton(onClick = { onDeleteProfile(profileId) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Button(onClick = onSelectFolder) {
                Text(if (folderUriStr == null) "Select Folder" else "Change Folder")
            }
            folderUriStr?.let {
                Text("Selected: ${folderNameToShow ?: "Folder"}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile Name") },
                placeholder = { Text(folderNameToShow ?: "Profile Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isDefault, onCheckedChange = { isDefault = it })
                Text("Set as default profile")
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    if (folderUriStr != null) {
                        val resolvedName = name.ifBlank { folderNameToShow ?: "Profile" }
                        onSaveProfile(profileId, resolvedName, folderUriStr, isDefault)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = folderUriStr != null
            ) {
                Text("Save")
            }
        }
    }
}
