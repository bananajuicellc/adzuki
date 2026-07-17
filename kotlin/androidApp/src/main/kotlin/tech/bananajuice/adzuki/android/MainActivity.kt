package tech.bananajuice.adzuki.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.key
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
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

import org.json.JSONObject

data class Profile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val folderUri: String
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("folderUri", folderUri)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): Profile {
            val obj = JSONObject(json)
            return Profile(
                id = obj.getString("id"),
                name = obj.getString("name"),
                folderUri = obj.getString("folderUri")
            )
        }
    }
}

sealed class Screen {
    object ProfilePicker : Screen()
    data class ProfileEditor(val profileId: String?) : Screen()
    data class FileList(val folderUri: String) : Screen()
    data class Editor(val fileUri: String) : Screen()
}

data class MainState(
    val currentScreen: Screen = Screen.ProfilePicker,
    val profiles: List<Profile> = emptyList(),
    val defaultProfileId: String? = null,
    val activeProfileId: String? = null,
    val openFolderUri: String? = null
)

sealed class MainIntent {
    object OpenProfilePicker : MainIntent()
    data class StartProfileEditor(val profileId: String?) : MainIntent()
    data class SaveProfile(val profile: Profile, val isDefault: Boolean) : MainIntent()
    data class SelectProfile(val profileId: String) : MainIntent()
    data class DeleteProfile(val profileId: String) : MainIntent()
    data class OpenFolder(val uri: String) : MainIntent()
    object NavigateBack : MainIntent()
    data class OpenEditor(val uri: String) : MainIntent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()

    private val prefs = application.getSharedPreferences("adzuki_prefs", Context.MODE_PRIVATE)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val savedProfilesJson = prefs.getStringSet("profiles", emptySet()) ?: emptySet()
            val profiles = savedProfilesJson.mapNotNull {
                try { Profile.fromJson(it) } catch (e: Exception) { null }
            }
            val defaultProfileId = prefs.getString("default_profile_id", null)

            val initialScreen = if (profiles.isEmpty()) {
                Screen.ProfileEditor(null)
            } else if (profiles.size == 1) {
                Screen.FileList(profiles.first().folderUri)
            } else if (defaultProfileId != null && profiles.any { it.id == defaultProfileId }) {
                val profile = profiles.first { it.id == defaultProfileId }
                Screen.FileList(profile.folderUri)
            } else {
                Screen.ProfilePicker
            }

            val activeId = if (initialScreen is Screen.FileList) {
                if (profiles.size == 1) profiles.first().id else defaultProfileId
            } else null

            _state.update {
                it.copy(
                    profiles = profiles,
                    defaultProfileId = defaultProfileId,
                    activeProfileId = activeId,
                    currentScreen = initialScreen,
                    openFolderUri = if (initialScreen is Screen.FileList) initialScreen.folderUri else null
                )
            }
        }
    }

    private fun persistState(state: MainState) {
        viewModelScope.launch(Dispatchers.IO) {
            val editor = prefs.edit()
            editor.putStringSet("profiles", state.profiles.map { it.toJson() }.toSet())
            if (state.defaultProfileId == null) {
                editor.remove("default_profile_id")
            } else {
                editor.putString("default_profile_id", state.defaultProfileId)
            }
            editor.apply()
        }
    }

    fun processIntent(intent: MainIntent) {
        when (intent) {
            is MainIntent.OpenProfilePicker -> {
                _state.update { it.copy(currentScreen = Screen.ProfilePicker) }
            }
            is MainIntent.StartProfileEditor -> {
                _state.update { it.copy(currentScreen = Screen.ProfileEditor(intent.profileId)) }
            }
            is MainIntent.SaveProfile -> {
                _state.update { state ->
                    val newProfiles = state.profiles.filter { it.id != intent.profile.id } + intent.profile
                    val newDefaultId = if (intent.isDefault) intent.profile.id else if (state.defaultProfileId == intent.profile.id) null else state.defaultProfileId

                    val newState = state.copy(
                        profiles = newProfiles,
                        defaultProfileId = newDefaultId,
                        activeProfileId = intent.profile.id,
                        currentScreen = Screen.FileList(intent.profile.folderUri),
                        openFolderUri = intent.profile.folderUri
                    )
                    persistState(newState)
                    newState
                }
            }
            is MainIntent.SelectProfile -> {
                _state.update { state ->
                    val profile = state.profiles.find { it.id == intent.profileId }
                    if (profile != null) {
                        state.copy(
                            activeProfileId = profile.id,
                            currentScreen = Screen.FileList(profile.folderUri),
                            openFolderUri = profile.folderUri
                        )
                    } else state
                }
            }
            is MainIntent.DeleteProfile -> {
                _state.update { state ->
                    val newProfiles = state.profiles.filter { it.id != intent.profileId }
                    val newDefaultId = if (state.defaultProfileId == intent.profileId) null else state.defaultProfileId

                    val nextScreen = if (newProfiles.isEmpty()) {
                        Screen.ProfileEditor(null)
                    } else {
                        Screen.ProfilePicker
                    }

                    val newState = state.copy(
                        profiles = newProfiles,
                        defaultProfileId = newDefaultId,
                        activeProfileId = if (nextScreen is Screen.FileList) newProfiles.firstOrNull()?.id else null,
                        currentScreen = nextScreen,
                        openFolderUri = if (nextScreen is Screen.FileList) newProfiles.firstOrNull()?.folderUri else null
                    )
                    persistState(newState)
                    newState
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
                        is Screen.FileList -> Screen.ProfilePicker
                        is Screen.ProfileEditor -> if (it.profiles.isEmpty()) it.currentScreen else Screen.ProfilePicker
                        else -> Screen.ProfilePicker
                    }.let { newScreen ->
                        it.copy(currentScreen = newScreen)
                    }
                }
            }
        }
    }
}

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
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(color = circleColor, shape = androidx.compose.foundation.shape.CircleShape),
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
                        is Screen.ProfilePicker -> ProfilePickerScreen(
                            state = state,
                            onIntent = viewModel::processIntent
                        )
                        is Screen.ProfileEditor -> ProfileEditorScreen(
                            state = state,
                            profileId = currentScreen.profileId,
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
