package tech.bananajuice.adzuki.shared.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.bananajuice.adzuki.shared.automerge.AutomergeDocument

import tech.bananajuice.adzuki.shared.importFromBeancount

class DocumentViewModel(
    private val coroutineScope: CoroutineScope,
    private val loadDocumentBytes: suspend () -> ByteArray,
    private val saveDocumentBytes: suspend (ByteArray) -> Unit,
    private val isBeancount: Boolean = false
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

            is DocumentIntent.StartEditingAccount -> {
                _state.update {
                    it.copy(
                        isEditingAccount = true,
                        accountBeingEdited = intent.account
                    )
                }
            }
            is DocumentIntent.CancelEditingAccount -> {
                _state.update {
                    it.copy(
                        isEditingAccount = false,
                        accountBeingEdited = null
                    )
                }
            }
            is DocumentIntent.SaveAccount -> saveAccount(intent.account)

            is DocumentIntent.StartEditingClose -> {
                _state.update {
                    it.copy(
                        isEditingClose = true,
                        closeBeingEdited = intent.closeDirective
                    )
                }
            }
            is DocumentIntent.CancelEditingClose -> {
                _state.update {
                    it.copy(
                        isEditingClose = false,
                        closeBeingEdited = null
                    )
                }
            }
            is DocumentIntent.SaveClose -> saveClose(intent.closeDirective)

            is DocumentIntent.StartEditingOption -> {
                _state.update {
                    it.copy(
                        isEditingOption = true,
                        optionBeingEdited = intent.option
                    )
                }
            }
            is DocumentIntent.CancelEditingOption -> {
                _state.update {
                    it.copy(
                        isEditingOption = false,
                        optionBeingEdited = null
                    )
                }
            }
            is DocumentIntent.SaveOption -> saveOption(intent.option)

            is DocumentIntent.StartEditingInclude -> {
                _state.update {
                    it.copy(
                        isEditingInclude = true,
                        includeBeingEdited = intent.includeDirective
                    )
                }
            }
            is DocumentIntent.CancelEditingInclude -> {
                _state.update {
                    it.copy(
                        isEditingInclude = false,
                        includeBeingEdited = null
                    )
                }
            }
            is DocumentIntent.SaveInclude -> saveInclude(intent.includeDirective)
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
                if (isBeancount) {
                    val text = String(bytes, Charsets.UTF_8)
                    document = importFromBeancount(text)
                    val directives = document?.getDirectives() ?: emptyList()
                    _state.update { it.copy(directives = directives, isLoading = false, isReadOnly = true) }
                } else {
                    document = AutomergeDocument(bytes)
                    val directives = document?.getDirectives() ?: emptyList()
                    _state.update { it.copy(directives = directives, isLoading = false, isReadOnly = false) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = "Failed to load document: ${e.message}") }
            }
        }
    }

    private fun saveTransaction(transaction: tech.bananajuice.adzuki.shared.automerge.TransactionDirective) {
        coroutineScope.launch {
            try {
                val doc = document ?: return@launch

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

    private fun saveAccount(account: tech.bananajuice.adzuki.shared.automerge.AccountDirective) {
        coroutineScope.launch {
            try {
                val doc = document ?: return@launch

                if (account.id < 0L) {
                    doc.addAccount(account)
                } else {
                    doc.updateAccount(account)
                }

                val bytes = doc.save()
                saveDocumentBytes(bytes)

                val directives = doc.getDirectives()
                _state.update {
                    it.copy(
                        directives = directives,
                        isEditingAccount = false,
                        accountBeingEdited = null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Failed to save account: ${e.message}") }
            }
        }
    }

    private fun saveClose(closeDirective: tech.bananajuice.adzuki.shared.automerge.CloseDirective) {
        coroutineScope.launch {
            try {
                val doc = document ?: return@launch

                if (closeDirective.id < 0L) {
                    doc.addCloseDirective(closeDirective)
                } else {
                    doc.updateCloseDirective(closeDirective)
                }

                val bytes = doc.save()
                saveDocumentBytes(bytes)

                val directives = doc.getDirectives()
                _state.update {
                    it.copy(
                        directives = directives,
                        isEditingClose = false,
                        closeBeingEdited = null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Failed to save close directive: ${e.message}") }
            }
        }
    }

    private fun saveOption(option: tech.bananajuice.adzuki.shared.automerge.OptionDirective) {
        coroutineScope.launch {
            try {
                val doc = document ?: return@launch

                if (option.id < 0L) {
                    doc.addOption(option)
                } else {
                    doc.updateOption(option)
                }

                val bytes = doc.save()
                saveDocumentBytes(bytes)

                val directives = doc.getDirectives()
                _state.update {
                    it.copy(
                        directives = directives,
                        isEditingOption = false,
                        optionBeingEdited = null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Failed to save option: ${e.message}") }
            }
        }
    }


    private fun saveInclude(includeDirective: tech.bananajuice.adzuki.shared.automerge.IncludeDirective) {
        coroutineScope.launch {
            try {
                val doc = document ?: return@launch

                if (includeDirective.id < 0L) {
                    doc.addIncludeDirective(includeDirective)
                } else {
                    doc.updateIncludeDirective(includeDirective)
                }

                val bytes = doc.save()
                saveDocumentBytes(bytes)

                val directives = doc.getDirectives()
                _state.update {
                    it.copy(
                        directives = directives,
                        isEditingInclude = false,
                        includeBeingEdited = null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Failed to save include: ${e.message}") }
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
