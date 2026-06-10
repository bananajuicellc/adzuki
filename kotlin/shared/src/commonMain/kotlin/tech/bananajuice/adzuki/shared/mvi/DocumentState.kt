package tech.bananajuice.adzuki.shared.mvi

import tech.bananajuice.adzuki.shared.automerge.Directive
import tech.bananajuice.adzuki.shared.automerge.TransactionDirective

data class DocumentState(
    val directives: List<Directive> = emptyList(),
    val isEditingTransaction: Boolean = false,
    val transactionBeingEdited: TransactionDirective? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
