#!/bin/bash
cat << 'INNER_EOF' > /tmp/patch_viewmodel.txt
<<<<<<< SEARCH
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
=======
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
>>>>>>> REPLACE
INNER_EOF
