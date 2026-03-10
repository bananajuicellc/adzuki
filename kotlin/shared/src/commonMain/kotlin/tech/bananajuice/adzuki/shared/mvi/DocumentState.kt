package tech.bananajuice.adzuki.shared.mvi

import kotlin.random.Random

sealed interface Block {
    val id: String
}

private fun generateId(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..8)
        .map { chars.random() }
        .joinToString("")
}

data class ParagraphBlock(
    override val id: String = generateId(),
    val text: String
) : Block

data class CodeBlock(
    override val id: String = generateId(),
    val text: String,
    val isRaw: Boolean = false
) : Block

data class DocumentState(
    val blocks: List<Block> = emptyList()
)

sealed interface DocumentIntent {
    data class UpdateBlockText(val blockId: String, val newText: String) : DocumentIntent
    data class ToggleCodeBlockRaw(val blockId: String) : DocumentIntent
}
