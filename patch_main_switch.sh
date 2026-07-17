#!/bin/bash
cat << 'INNER_EOF' > /tmp/patch_main_switch.txt
<<<<<<< SEARCH
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
=======
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
>>>>>>> REPLACE
INNER_EOF
