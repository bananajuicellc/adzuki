package tech.bananajuice.adzuki.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import tech.bananajuice.adzuki.shared.mvi.Block
import tech.bananajuice.adzuki.shared.mvi.BlockEditor
import tech.bananajuice.adzuki.shared.mvi.CodeBlock
import tech.bananajuice.adzuki.shared.mvi.DocumentState
import tech.bananajuice.adzuki.shared.mvi.DocumentViewModel
import tech.bananajuice.adzuki.shared.mvi.ParagraphBlock
import uniffi.adzuki.AstNode
import uniffi.adzuki.ParseTree
import uniffi.adzuki.parseToTree

class MainActivity : ComponentActivity() {

    init {
        System.loadLibrary("adzuki")
    }

    private fun mapParseTreeToBlocks(tree: ParseTree): List<Block> {
        return tree.nodes.map { node ->
            when (node) {
                is AstNode.Heading -> ParagraphBlock(text = "#".repeat(node.level.toInt()) + " " + node.content)
                is AstNode.Paragraph -> ParagraphBlock(text = node.content)
                is AstNode.CodeBlock -> CodeBlock(text = node.content.trim('`', '\n'), isRaw = false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialText = """
            # Welcome to Adzuki!

            This is a basic paragraph block.

            ```
            2023-10-25 * "Grocery Store"
              Expenses:Food  25.00 USD
              Assets:Checking
            ```

            More text down here.
        """.trimIndent()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel = remember {
                        val parseTree = parseToTree(initialText)
                        val mappedBlocks = mapParseTreeToBlocks(parseTree)
                        DocumentViewModel(
                            initialState = DocumentState(
                                blocks = mappedBlocks
                            )
                        )
                    }
                    val state by viewModel.state.collectAsState()

                    BlockEditor(
                        state = state,
                        onIntent = viewModel::processIntent
                    )
                }
            }
        }
    }
}
