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
import tech.bananajuice.adzuki.shared.mvi.BlockEditor
import tech.bananajuice.adzuki.shared.mvi.CodeBlock
import tech.bananajuice.adzuki.shared.mvi.DocumentState
import tech.bananajuice.adzuki.shared.mvi.DocumentViewModel
import tech.bananajuice.adzuki.shared.mvi.ParagraphBlock

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel = remember {
                        DocumentViewModel(
                            initialState = DocumentState(
                                blocks = listOf(
                                    ParagraphBlock(text = "Welcome to Adzuki!"),
                                    ParagraphBlock(text = "This is a basic paragraph block."),
                                    CodeBlock(
                                        text = "2023-10-25 * \"Grocery Store\"\n  Expenses:Food  25.00 USD\n  Assets:Checking",
                                        isRaw = false
                                    ),
                                    ParagraphBlock(text = "More text down here.")
                                )
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
