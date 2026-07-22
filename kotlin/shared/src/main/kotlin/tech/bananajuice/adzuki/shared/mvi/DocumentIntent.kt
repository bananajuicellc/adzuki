package tech.bananajuice.adzuki.shared.mvi

import tech.bananajuice.adzuki.shared.automerge.*

sealed interface DocumentIntent {
    object LoadDocument : DocumentIntent
    data class StartEditingTransaction(val transaction: TransactionDirective?) : DocumentIntent
    object CancelEditingTransaction : DocumentIntent
    data class SaveTransaction(val transaction: TransactionDirective) : DocumentIntent

    data class StartEditingAccount(val account: AccountDirective?) : DocumentIntent
    object CancelEditingAccount : DocumentIntent
    data class SaveAccount(val account: AccountDirective) : DocumentIntent

    data class StartEditingClose(val closeDirective: CloseDirective?) : DocumentIntent
    object CancelEditingClose : DocumentIntent
    data class SaveClose(val closeDirective: CloseDirective) : DocumentIntent

    data class StartEditingOption(val option: OptionDirective?) : DocumentIntent
    object CancelEditingOption : DocumentIntent
    data class SaveOption(val option: OptionDirective) : DocumentIntent
    data class DeleteDirective(val id: Long) : DocumentIntent
    object DismissError : DocumentIntent
}
