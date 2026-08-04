package tech.bananajuice.adzuki.shared.mvi

import tech.bananajuice.adzuki.shared.automerge.Directive
import tech.bananajuice.adzuki.shared.automerge.*

data class DocumentState(
    val directives: List<Directive> = emptyList(),
    val isEditingTransaction: Boolean = false,
    val transactionBeingEdited: TransactionDirective? = null,
    val isEditingAccount: Boolean = false,
    val accountBeingEdited: AccountDirective? = null,
    val isEditingClose: Boolean = false,
    val closeBeingEdited: CloseDirective? = null,
    val isEditingOption: Boolean = false,
    val optionBeingEdited: OptionDirective? = null,
    val isEditingInclude: Boolean = false,
    val includeBeingEdited: IncludeDirective? = null,
    val isLoading: Boolean = true,
    val isReadOnly: Boolean = false,
    val errorMessage: String? = null
)
