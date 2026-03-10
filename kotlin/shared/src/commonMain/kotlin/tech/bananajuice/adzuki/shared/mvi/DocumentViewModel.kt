package tech.bananajuice.adzuki.shared.mvi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DocumentViewModel(initialState: DocumentState = DocumentState()) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<DocumentState> = _state.asStateFlow()

    fun processIntent(intent: DocumentIntent) {
        when (intent) {
            is DocumentIntent.UpdateBlockText -> {
                _state.update { currentState ->
                    val newBlocks = currentState.blocks.map { block ->
                        if (block.id == intent.blockId) {
                            when (block) {
                                is ParagraphBlock -> block.copy(text = intent.newText)
                                is CodeBlock -> block.copy(text = intent.newText)
                                else -> block
                            }
                        } else {
                            block
                        }
                    }
                    currentState.copy(blocks = newBlocks)
                }
            }
            is DocumentIntent.ToggleCodeBlockRaw -> {
                _state.update { currentState ->
                    val newBlocks = currentState.blocks.map { block ->
                        if (block.id == intent.blockId && block is CodeBlock) {
                            block.copy(isRaw = !block.isRaw)
                        } else {
                            block
                        }
                    }
                    currentState.copy(blocks = newBlocks)
                }
            }
        }
    }
}
