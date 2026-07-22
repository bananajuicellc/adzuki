package tech.bananajuice.adzuki.android

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.bananajuice.shared.profilepicker.Profile
import tech.bananajuice.shared.profilepicker.ProfilePickerScreen
import tech.bananajuice.shared.profilepicker.ProfileEditorScreen
import tech.bananajuice.shared.profilepicker.AndroidProfileRepository
import tech.bananajuice.adzuki.shared.FileListScreen
import tech.bananajuice.adzuki.shared.EditorScreen

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

    private val profileRepository = AndroidProfileRepository(application)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val profiles = profileRepository.getProfiles()
            val defaultProfileId = profileRepository.getDefaultProfileId()

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

    private fun loadProfiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val profiles = profileRepository.getProfiles()
            val defaultId = profileRepository.getDefaultProfileId()
            _state.update { it.copy(profiles = profiles, defaultProfileId = defaultId) }
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
                viewModelScope.launch(Dispatchers.IO) {
                    profileRepository.saveProfile(intent.profile, intent.isDefault)
                    val newProfiles = profileRepository.getProfiles()
                    val newDefaultId = profileRepository.getDefaultProfileId()
                    _state.update { state ->
                        state.copy(
                            profiles = newProfiles,
                            defaultProfileId = newDefaultId,
                            activeProfileId = intent.profile.id,
                            currentScreen = Screen.FileList(intent.profile.folderUri),
                            openFolderUri = intent.profile.folderUri
                        )
                    }
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
                viewModelScope.launch(Dispatchers.IO) {
                    profileRepository.deleteProfile(intent.profileId)
                    val newProfiles = profileRepository.getProfiles()
                    val newDefaultId = profileRepository.getDefaultProfileId()

                    val nextScreen = if (newProfiles.isEmpty()) {
                        Screen.ProfileEditor(null)
                    } else {
                        Screen.ProfilePicker
                    }

                    _state.update { state ->
                        state.copy(
                            profiles = newProfiles,
                            defaultProfileId = newDefaultId,
                            activeProfileId = if (nextScreen is Screen.FileList) newProfiles.firstOrNull()?.id else null,
                            currentScreen = nextScreen,
                            openFolderUri = if (nextScreen is Screen.FileList) newProfiles.firstOrNull()?.folderUri else null
                        )
                    }
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
                            profiles = state.profiles,
                            defaultProfileId = state.defaultProfileId,
                            onAddProfile = { viewModel.processIntent(MainIntent.StartProfileEditor(null)) },
                            onSelectProfile = { viewModel.processIntent(MainIntent.SelectProfile(it)) },
                            onEditProfile = { viewModel.processIntent(MainIntent.StartProfileEditor(it)) }
                        )
                        is Screen.ProfileEditor -> {
                            val context = LocalContext.current
                            val existingProfile = state.profiles.find { it.id == currentScreen.profileId }
                            var folderUriStr by remember(existingProfile) { mutableStateOf(existingProfile?.folderUri) }
                            var folderNameToShow by remember(existingProfile) { mutableStateOf<String?>(null) }
                            var defaultNameFromFolder by remember { mutableStateOf<String?>(null) }

                            LaunchedEffect(folderUriStr) {
                                if (folderUriStr != null) {
                                    val uri = Uri.parse(folderUriStr)
                                    val documentFile = DocumentFile.fromTreeUri(context, uri)
                                    folderNameToShow = documentFile?.name ?: Uri.parse(folderUriStr).lastPathSegment
                                    defaultNameFromFolder = documentFile?.name
                                }
                            }

                            val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                                if (uri != null) {
                                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    try {
                                        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                                        folderUriStr = uri.toString()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to take permission", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            ProfileEditorScreen(
                                profileId = currentScreen.profileId,
                                existingProfile = existingProfile,
                                isDefaultInitial = existingProfile?.id != null && existingProfile.id == state.defaultProfileId,
                                onNavigateBack = { viewModel.processIntent(MainIntent.NavigateBack) },
                                onDeleteProfile = { viewModel.processIntent(MainIntent.DeleteProfile(it)) },
                                onSelectFolder = { folderLauncher.launch(null) },
                                folderUriStr = folderUriStr,
                                folderNameToShow = folderNameToShow,
                                onSaveProfile = { id, name, uriStr, isDefault ->
                                    val actualName = if (name.isBlank() && defaultNameFromFolder != null) defaultNameFromFolder!! else name
                                    val profile = Profile(id = id, name = actualName, folderUri = uriStr)
                                    viewModel.processIntent(MainIntent.SaveProfile(profile, isDefault))
                                }
                            )
                        }
                        is Screen.FileList -> FileListScreen(
                            profiles = state.profiles,
                            activeProfileId = state.activeProfileId,
                            openFolderUri = state.openFolderUri,
                            onOpenProfilePicker = { viewModel.processIntent(MainIntent.OpenProfilePicker) },
                            onOpenEditor = { viewModel.processIntent(MainIntent.OpenEditor(it)) }
                        )
                        is Screen.Editor -> EditorScreen(
                            fileUri = currentScreen.fileUri,
                            onNavigateBack = { viewModel.processIntent(MainIntent.NavigateBack) }
                        )
                    }
                }
            }
        }
    }
}
