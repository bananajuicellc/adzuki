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
            is DocumentIntent.DeleteDirective -> deleteDirective(intent.id)
            is DocumentIntent.DismissError -> {
                _state.update { it.copy(errorMessage = null) }
            }
            is DocumentIntent.StartImportSync -> startImportSync(intent.newDocument)
            is DocumentIntent.ToggleSyncChange -> {
                _state.update {
                    val updatedList = it.syncChanges.toMutableList()
                    if (intent.index in updatedList.indices) {
                        updatedList[intent.index] = updatedList[intent.index].toggle()
                    }
                    it.copy(syncChanges = updatedList)
                }
            }
            is DocumentIntent.CancelSync -> {
                _state.update {
                    it.copy(isSyncing = false, syncChanges = emptyList())
                }
            }
            is DocumentIntent.ApplySyncChanges -> applySyncChanges()
        }
    }

    private fun isSimilar(a: tech.bananajuice.adzuki.shared.automerge.Directive, b: tech.bananajuice.adzuki.shared.automerge.Directive): Boolean {
        if (a::class != b::class) return false
        return when (a) {
            is tech.bananajuice.adzuki.shared.automerge.TransactionDirective -> {
                val tb = b as tech.bananajuice.adzuki.shared.automerge.TransactionDirective
                a.date == tb.date && ((a.payee.isNotEmpty() && a.payee == tb.payee) || (a.memo.isNotEmpty() && a.memo == tb.memo))
            }
            is tech.bananajuice.adzuki.shared.automerge.AccountDirective -> {
                val ab = b as tech.bananajuice.adzuki.shared.automerge.AccountDirective
                a.name == ab.name
            }
            is tech.bananajuice.adzuki.shared.automerge.CloseDirective -> {
                val cb = b as tech.bananajuice.adzuki.shared.automerge.CloseDirective
                a.account == cb.account
            }
            is tech.bananajuice.adzuki.shared.automerge.OptionDirective -> {
                val ob = b as tech.bananajuice.adzuki.shared.automerge.OptionDirective
                a.name == ob.name
            }
            else -> false
        }
    }

    private fun areDirectivesEqual(a: tech.bananajuice.adzuki.shared.automerge.Directive, b: tech.bananajuice.adzuki.shared.automerge.Directive): Boolean {
        if (a::class != b::class) return false
        return when (a) {
            is tech.bananajuice.adzuki.shared.automerge.TransactionDirective -> {
                val tb = b as tech.bananajuice.adzuki.shared.automerge.TransactionDirective
                a.date == tb.date && a.payee == tb.payee && a.memo == tb.memo && a.postings == tb.postings
            }
            is tech.bananajuice.adzuki.shared.automerge.AccountDirective -> {
                val ab = b as tech.bananajuice.adzuki.shared.automerge.AccountDirective
                a.date == ab.date && a.name == ab.name && a.constraintCurrencies == ab.constraintCurrencies
            }
            is tech.bananajuice.adzuki.shared.automerge.CloseDirective -> {
                val cb = b as tech.bananajuice.adzuki.shared.automerge.CloseDirective
                a.date == cb.date && a.account == cb.account
            }
            is tech.bananajuice.adzuki.shared.automerge.OptionDirective -> {
                val ob = b as tech.bananajuice.adzuki.shared.automerge.OptionDirective
                a.name == ob.name && a.value == ob.value
            }
            else -> false
        }
    }

    private fun startImportSync(newDocument: AutomergeDocument) {
        val currentDirectives = document?.getDirectives() ?: emptyList()
        val newDirectives = newDocument.getDirectives()

        val changes = mutableListOf<DiffChange>()
        val unmatchedCurrent = currentDirectives.toMutableList()
        val unmatchedNew = newDirectives.toMutableList()

        // 1. Exact matches
        val currentIter = unmatchedCurrent.iterator()
        while (currentIter.hasNext()) {
            val curr = currentIter.next()
            val matchIndex = unmatchedNew.indexOfFirst { areDirectivesEqual(curr, it) }
            if (matchIndex != -1) {
                currentIter.remove()
                unmatchedNew.removeAt(matchIndex)
            }
        }

        // 2. Modified (similar but not exact)
        val modifiedCurrentIter = unmatchedCurrent.iterator()
        while (modifiedCurrentIter.hasNext()) {
            val curr = modifiedCurrentIter.next()
            val matchIndex = unmatchedNew.indexOfFirst { isSimilar(curr, it) }
            if (matchIndex != -1) {
                val matchedNew = unmatchedNew[matchIndex]
                changes.add(DiffChange.Modified(curr, matchedNew))
                modifiedCurrentIter.remove()
                unmatchedNew.removeAt(matchIndex)
            }
        }

        // 3. Removed
        unmatchedCurrent.forEach { changes.add(DiffChange.Removed(it)) }

        // 4. Added
        unmatchedNew.forEach { changes.add(DiffChange.Added(it)) }

        _state.update {
            it.copy(
                isSyncing = true,
                syncChanges = changes
            )
        }
    }

    private fun applySyncChanges() {
        coroutineScope.launch {
            try {
                val doc = document ?: return@launch

                val changesToApply = _state.value.syncChanges.filter { it.selected }

                val modifications = changesToApply.filterIsInstance<DiffChange.Modified>()
                val additions = changesToApply.filterIsInstance<DiffChange.Added>()
                val removals = changesToApply.filterIsInstance<DiffChange.Removed>()
                    .sortedByDescending { it.directive.id }

                modifications.forEach { change ->
                    when (val newDir = change.newDirective) {
                        is tech.bananajuice.adzuki.shared.automerge.TransactionDirective -> doc.updateTransaction(newDir.copy(id = change.oldDirective.id))
                        is tech.bananajuice.adzuki.shared.automerge.AccountDirective -> doc.updateAccount(newDir.copy(id = change.oldDirective.id))
                        is tech.bananajuice.adzuki.shared.automerge.CloseDirective -> doc.updateCloseDirective(newDir.copy(id = change.oldDirective.id))
                        is tech.bananajuice.adzuki.shared.automerge.OptionDirective -> doc.updateOption(newDir.copy(id = change.oldDirective.id))
                    }
                }

                additions.forEach { change ->
                    when (val dir = change.directive) {
                        is tech.bananajuice.adzuki.shared.automerge.TransactionDirective -> doc.addTransaction(dir.copy(id = -1))
                        is tech.bananajuice.adzuki.shared.automerge.AccountDirective -> doc.addAccount(dir.copy(id = -1))
                        is tech.bananajuice.adzuki.shared.automerge.CloseDirective -> doc.addCloseDirective(dir.copy(id = -1))
                        is tech.bananajuice.adzuki.shared.automerge.OptionDirective -> doc.addOption(dir.copy(id = -1))
                    }
                }

                removals.forEach { change ->
                    doc.deleteDirective(change.directive.id)
                }

                val bytes = doc.save()
                saveDocumentBytes(bytes)
                val newDirectives = doc.getDirectives()

                _state.update {
                    it.copy(
                        directives = newDirectives,
                        isSyncing = false,
                        syncChanges = emptyList()
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = "Failed to apply sync changes: ${e.message}") }
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
