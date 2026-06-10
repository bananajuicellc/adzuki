package tech.bananajuice.adzuki.shared.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.bananajuice.adzuki.shared.automerge.AutomergeDocument

class DocumentViewModel(
    private val coroutineScope: CoroutineScope,
    private val loadDocumentBytes: suspend () -> ByteArray,
    private val saveDocumentBytes: suspend (ByteArray) -> Unit
) {
    private val _state = MutableStateFlow(DocumentState())
    val state: StateFlow<DocumentState> = _state.asStateFlow()

    private var document: AutomergeDocument? = null

    init {
        processIntent(DocumentIntent.LoadDocument)
    }

    fun processIntent(intent: DocumentIntent) {
        when (intent) {
            is DocumentIntent.LoadDocument -> loadDocument()
            is DocumentIntent.StartEditingTransaction -> {
                _state.update {
                    it.copy(
                        isEditingTransaction = true,
                        transactionBeingEdited = intent.transaction
                    )
                }
            }
            is DocumentIntent.CancelEditingTransaction -> {
                _state.update {
                    it.copy(
                        isEditingTransaction = false,
                        transactionBeingEdited = null
                    )
                }
            }
            is DocumentIntent.SaveTransaction -> saveTransaction(intent.transaction)
            is DocumentIntent.DeleteDirective -> deleteDirective(intent.id)
            is DocumentIntent.DismissError -> {
                _state.update { it.copy(errorMessage = null) }
            }
        }
    }

    private fun loadDocument() {
        coroutineScope.launch {
            try {
                val bytes = loadDocumentBytes()
                document = AutomergeDocument(bytes)
                val directives = document?.getDirectives() ?: emptyList()
                _state.update { it.copy(directives = directives, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = "Failed to load document: ${e.message}") }
            }
        }
    }

    private fun saveTransaction(transaction: tech.bananajuice.adzuki.shared.automerge.TransactionDirective) {
        coroutineScope.launch {
            try {
                val doc = document ?: return@launch

                // If ID is < 0, we assume it's a new transaction being added
                if (transaction.id < 0L) {
                    doc.addTransaction(transaction)
                } else {
                    doc.updateTransaction(transaction)
                }

                val bytes = doc.save()
                saveDocumentBytes(bytes)

                val directives = doc.getDirectives()
                _state.update {
                    it.copy(
                        directives = directives,
                        isEditingTransaction = false,
                        transactionBeingEdited = null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Failed to save transaction: ${e.message}") }
            }
        }
    }

    private fun deleteDirective(id: Long) {
        coroutineScope.launch {
            try {
                val doc = document ?: return@launch
                doc.deleteDirective(id)
                val bytes = doc.save()
                saveDocumentBytes(bytes)
                val directives = doc.getDirectives()
                _state.update { it.copy(directives = directives) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Failed to delete item: ${e.message}") }
            }
        }
    }
}
