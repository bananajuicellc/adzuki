package tech.bananajuice.adzuki.shared.mvi

import tech.bananajuice.adzuki.shared.automerge.TransactionDirective

sealed interface DocumentIntent {
    object LoadDocument : DocumentIntent
    data class StartEditingTransaction(val transaction: TransactionDirective?) : DocumentIntent
    object CancelEditingTransaction : DocumentIntent
    data class SaveTransaction(val transaction: TransactionDirective) : DocumentIntent
    data class DeleteDirective(val id: Long) : DocumentIntent
    object DismissError : DocumentIntent
}
