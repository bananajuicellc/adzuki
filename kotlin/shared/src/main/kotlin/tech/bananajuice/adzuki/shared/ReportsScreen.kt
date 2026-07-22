package tech.bananajuice.adzuki.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tech.bananajuice.adzuki.shared.automerge.Directive

@Composable
fun ReportsScreen(directives: List<Directive>) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Reports coming soon...")
    }
}
