package tech.bananajuice.adzuki.shared.mvi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BlockEditor(
    state: DocumentState,
    onIntent: (DocumentIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.blocks, key = { it.id }) { block ->
            when (block) {
                is ParagraphBlock -> {
                    ParagraphBlockEditor(
                        block = block,
                        onTextChange = { newText ->
                            onIntent(DocumentIntent.UpdateBlockText(block.id, newText))
                        }
                    )
                }
                is CodeBlock -> {
                    CodeBlockEditor(
                        block = block,
                        onTextChange = { newText ->
                            onIntent(DocumentIntent.UpdateBlockText(block.id, newText))
                        },
                        onToggleRaw = {
                            onIntent(DocumentIntent.ToggleCodeBlockRaw(block.id))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ParagraphBlockEditor(
    block: ParagraphBlock,
    onTextChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    BasicTextField(
        value = block.text,
        onValueChange = onTextChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .padding(4.dp),
        textStyle = TextStyle(
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (block.text.isEmpty()) {
                    Text(
                        text = "Type here...",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
fun CodeBlockEditor(
    block: CodeBlock,
    onTextChange: (String) -> Unit,
    onToggleRaw: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (block.isRaw) "Code (Raw)" else "Code (Rich View)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Toggle",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onToggleRaw() }
                    .padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (block.isRaw) {
            BasicTextField(
                value = block.text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(8.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                ),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (block.text.isEmpty()) {
                            Text(
                                text = "Enter code here...",
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
        } else {
            // Placeholder for Rich UI (like a transaction table)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color.LightGray.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Rich Editor Placeholder",
                    color = Color.DarkGray
                )
            }
        }
    }
}
