package tech.bananajuice.adzuki.shared.mvi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextLayoutResult
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
    val visualTransformation = remember(state.nodes, state.foldedHeadingIds) {
        DocumentVisualTransformation(state.nodes, state.foldedHeadingIds)
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val scrollState = rememberScrollState()

    // Evaluate transformed text exactly once per state change, instead of in the composition loop
    val transformedText = remember(state.text, visualTransformation) {
        visualTransformation.filter(AnnotatedString(state.text))
    }

    Row(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        Box(modifier = Modifier.width(24.dp)) {
            val layoutResult = textLayoutResult
            if (layoutResult != null) {
                for (node in state.nodes) {
                    if (node is HeadingNode) {
                        val startOffset = node.span.start.coerceIn(0, state.text.length)
                        val transformedOffset = transformedText.offsetMapping.originalToTransformed(startOffset)

                        // Check if the heading is hidden by seeing if its start and end map to the same offset
                        val endOffset = node.span.end.coerceIn(0, state.text.length)
                        val transformedEndOffset = transformedText.offsetMapping.originalToTransformed(endOffset)

                        // If it's a heading inside a folded region, its entire span maps to the "..." offset.
                        // Skip rendering the arrow for nested hidden headings.
                        if (transformedOffset == transformedEndOffset && transformedOffset != 0 && transformedEndOffset != transformedText.text.length) {
                             // Additionally check if the transformed text is literally '...' at this spot to be certain it's hidden
                             val originalStartMappedBack = transformedText.offsetMapping.transformedToOriginal(transformedOffset)
                             if (originalStartMappedBack != startOffset) {
                                 continue // It's hidden
                             }
                        }

                        val isFolded = state.foldedHeadingIds.contains(node.treeIndex)
                        val line = layoutResult.getLineForOffset(transformedOffset)
                        val y = layoutResult.getLineTop(line)

                        Text(
                            text = if (isFolded) ">" else "v",
                            modifier = Modifier
                                .padding(top = (y / layoutResult.layoutInput.density.density).dp)
                                .clickable { onIntent(DocumentIntent.ToggleFold(node.treeIndex)) },
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        BasicTextField(
            value = state.text,
            onValueChange = { newText -> onIntent(DocumentIntent.UpdateText(newText)) },
            // Do not use fillMaxSize() on the TextField so it grows to content height, allowing Row to handle scrolling
            modifier = Modifier.weight(1f),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            visualTransformation = visualTransformation,
            onTextLayout = { result ->
                textLayoutResult = result
            }
        )
    }
}

class DocumentVisualTransformation(
    private val nodes: List<DocumentNode>,
    private val foldedHeadingIds: Set<List<Int>>
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val hiddenRanges = mutableListOf<IntRange>()
        var currentFoldLevel = Int.MAX_VALUE
        var currentFoldStart = -1

        for (node in nodes) {
            if (node is HeadingNode) {
                if (node.level <= currentFoldLevel && currentFoldLevel != Int.MAX_VALUE) {
                    // We've reached a heading of the same or higher level, end the fold
                    hiddenRanges.add(currentFoldStart until node.span.start)
                    currentFoldLevel = Int.MAX_VALUE
                    currentFoldStart = -1
                }

                if (foldedHeadingIds.contains(node.treeIndex) && currentFoldLevel == Int.MAX_VALUE) {
                    currentFoldLevel = node.level
                    // The fold starts *after* the heading's content, so we keep the heading visible
                    currentFoldStart = node.span.end
                }
            }
        }

        if (currentFoldLevel != Int.MAX_VALUE) {
            hiddenRanges.add(currentFoldStart until text.length)
        }

        val builder = AnnotatedString.Builder()
        val originalToTransformed = mutableListOf<Int>()
        val transformedToOriginal = mutableListOf<Int>()

        var textIndex = 0
        var transformedIndex = 0

        for (range in hiddenRanges) {
            if (textIndex < range.first) {
                val chunk = text.text.substring(textIndex, range.first)
                builder.append(chunk)
                for (i in 0 until chunk.length) {
                    originalToTransformed.add(transformedIndex)
                    transformedToOriginal.add(textIndex + i)
                    transformedIndex++
                }
                textIndex = range.first
            }

            if (textIndex == range.first) {
                builder.append("...\n")
                for (i in 0..3) {
                    transformedToOriginal.add(textIndex)
                }
                for (i in range) {
                    originalToTransformed.add(transformedIndex) // Map hidden text to the '...\n'
                }
                transformedIndex += 4
                textIndex = range.last + 1
            }
        }

        if (textIndex < text.length) {
            val chunk = text.text.substring(textIndex, text.length)
            builder.append(chunk)
            for (i in 0 until chunk.length) {
                originalToTransformed.add(transformedIndex)
                transformedToOriginal.add(textIndex + i)
                transformedIndex++
            }
        }

        // Add mapping for the end of the string
        originalToTransformed.add(transformedIndex)
        transformedToOriginal.add(text.length)

        for (node in nodes) {
            val style = when (node) {
                is HeadingNode -> SpanStyle(color = Color.Blue)
                is CodeBlockNode -> SpanStyle(color = Color(0xFF006400)) // Dark Green
                is BeancountNode -> SpanStyle(color = Color(0xFF8B0000)) // Dark Red
                is ParagraphNode -> SpanStyle(color = Color.Unspecified)
            }

            // Map the original indices to the transformed indices
            val startOriginal = node.span.start.coerceIn(0, text.length)
            val endOriginal = node.span.end.coerceIn(0, text.length)

            if (startOriginal < endOriginal) {
                val startTransformed = originalToTransformed[startOriginal]
                val endTransformed = originalToTransformed[endOriginal]

                if (startTransformed < endTransformed) {
                    builder.addStyle(style, startTransformed, endTransformed)
                }
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return originalToTransformed[offset.coerceIn(0, originalToTransformed.lastIndex)]
            }

            override fun transformedToOriginal(offset: Int): Int {
                return transformedToOriginal[offset.coerceIn(0, transformedToOriginal.lastIndex)]
            }
        }

        return TransformedText(builder.toAnnotatedString(), offsetMapping)
    }
}
