package tech.bananajuice.adzuki.shared.mvi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DocumentEditor(
    state: DocumentState,
    onIntent: (DocumentIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val visualTransformation = remember(state.nodes) {
        DocumentVisualTransformation(state.nodes)
    }

    Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
        BasicTextField(
            value = state.text,
            onValueChange = { newText -> onIntent(DocumentIntent.UpdateText(newText)) },
            modifier = Modifier.fillMaxSize(),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            visualTransformation = visualTransformation
        )
    }
}

class DocumentVisualTransformation(private val nodes: List<DocumentNode>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text)

        for (node in nodes) {
            val style = when (node) {
                is HeadingNode -> SpanStyle(color = Color.Blue)
                is CodeBlockNode -> SpanStyle(color = Color(0xFF006400)) // Dark Green
                is BeancountNode -> SpanStyle(color = Color(0xFF8B0000)) // Dark Red
                is ParagraphNode -> SpanStyle(color = Color.Unspecified)
            }
            // Ensure span is within text bounds to prevent crashes if typing goes ahead of parser
            val start = node.span.start.coerceIn(0, text.length)
            val end = node.span.end.coerceIn(0, text.length)
            if (start < end) {
                builder.addStyle(style, start, end)
            }
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
