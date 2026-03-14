package tech.bananajuice.adzuki.shared.mvi

// We keep a generic AstNode representation in the shared module since uniffi.adzuki.AstNode
// is currently generated directly in the AndroidApp module.
// In a full multiplatform setup, we'd generate Uniffi bindings for KMP natively.

data class Span(val start: Int, val end: Int)

sealed interface DocumentNode {
    val span: Span
}

data class HeadingNode(val level: Int, val content: String, override val span: Span) : DocumentNode
data class ParagraphNode(val content: String, override val span: Span) : DocumentNode
data class CodeBlockNode(val content: String, override val span: Span) : DocumentNode
data class BeancountNode(override val span: Span) : DocumentNode

data class DocumentState(
    val text: String = "",
    val nodes: List<DocumentNode> = emptyList()
)

sealed interface DocumentIntent {
    data class UpdateText(val newText: String) : DocumentIntent
    object SaveNow : DocumentIntent
}
