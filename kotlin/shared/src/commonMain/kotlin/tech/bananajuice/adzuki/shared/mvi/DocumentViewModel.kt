package tech.bananajuice.adzuki.shared.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DocumentViewModel(
    initialState: DocumentState = DocumentState(),
    private val parseDebounceMs: Long = 300L,
    private val saveDebounceMs: Long = 3000L,
    private val coroutineScope: CoroutineScope,
    private val documentId: String? = null,
    private val foldStateRepository: FoldStateRepository? = null,
    private val parserProxy: (String) -> List<DocumentNode>,
    private val onSave: ((String) -> Unit)? = null
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<DocumentState> = _state.asStateFlow()

    private var parseJob: Job? = null
    private var saveJob: Job? = null

    init {
        // Initial parse
        if (initialState.text.isNotEmpty()) {
            parseText(initialState.text)
        }

        if (documentId != null && foldStateRepository != null) {
            coroutineScope.launch(Dispatchers.Default) {
                val foldedIds = foldStateRepository.getFoldedHeadings(documentId)
                _state.update { currentState ->
                    currentState.copy(foldedHeadingIds = foldedIds)
                }
            }
        }
    }

    fun processIntent(intent: DocumentIntent) {
        when (intent) {
            is DocumentIntent.UpdateText -> {
                _state.update { currentState ->
                    currentState.copy(text = intent.newText)
                }

                parseJob?.cancel()
                parseJob = coroutineScope.launch(Dispatchers.Default) {
                    delay(parseDebounceMs)
                    parseText(intent.newText)
                }

                if (onSave != null) {
                    saveJob?.cancel()
                    saveJob = coroutineScope.launch(Dispatchers.Default) {
                        delay(saveDebounceMs)
                        onSave.invoke(intent.newText)
                    }
                }
            }
            is DocumentIntent.SaveNow -> {
                saveJob?.cancel()
                onSave?.invoke(_state.value.text)
            }
            is DocumentIntent.ToggleFold -> {
                _state.update { currentState ->
                    val newFoldedIds = currentState.foldedHeadingIds.toMutableSet()
                    if (newFoldedIds.contains(intent.headingIndex)) {
                        newFoldedIds.remove(intent.headingIndex)
                        if (documentId != null && foldStateRepository != null) {
                            coroutineScope.launch(Dispatchers.Default) { foldStateRepository.removeFoldedHeading(documentId, intent.headingIndex) }
                        }
                    } else {
                        newFoldedIds.add(intent.headingIndex)
                        if (documentId != null && foldStateRepository != null) {
                            coroutineScope.launch(Dispatchers.Default) { foldStateRepository.addFoldedHeading(documentId, intent.headingIndex) }
                        }
                    }
                    currentState.copy(foldedHeadingIds = newFoldedIds)
                }
            }
        }
    }

    private fun parseText(text: String) {
        val parsedNodes = parserProxy(text)
        _state.update { currentState ->
            currentState.copy(nodes = parsedNodes)
        }
    }
}
