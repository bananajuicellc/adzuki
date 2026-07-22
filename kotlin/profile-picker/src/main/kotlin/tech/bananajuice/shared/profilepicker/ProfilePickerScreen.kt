package tech.bananajuice.shared.profilepicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : BaseProfile> ProfilePickerScreen(
    profiles: List<T>,
    defaultProfileId: String?,
    onAddProfile: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onEditProfile: (String) -> Unit,
    title: String = "Select Profile"
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProfile) {
                Icon(Icons.Filled.Add, contentDescription = "Add Profile")
            }
        }
    ) { padding ->
        val colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.error
        )
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(profiles, key = { it.id }) { profile ->
                val firstLetter = profile.name.firstOrNull()?.uppercase() ?: "?"
                val colorIndex = kotlin.math.abs(profile.name.hashCode()) % colors.size
                val circleColor = colors[colorIndex]

                ListItem(
                    headlineContent = { Text(profile.name) },
                    supportingContent = { Text(if (profile.id == defaultProfileId) "Default" else "") },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(color = circleColor, shape = androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = firstLetter,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    modifier = Modifier.clickable { onSelectProfile(profile.id) },
                    trailingContent = {
                        IconButton(onClick = { onEditProfile(profile.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit Profile")
                        }
                    }
                )
            }
        }
    }
}
