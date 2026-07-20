package app.confused.anikuta.feature.extensionssettings

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.component.CollapsingHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.data.extension.repo.ExtensionRepo
import app.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import kotlinx.coroutines.launch

private const val TAG = "AnikutaRepoUI"

/**
 * Extension repository settings screen — manage extension repos.
 *
 * Shows the list of configured repos with delete buttons.
 * FAB (bottom-right) opens an add-repo dialog.
 * The default Aniyomi repo is pre-configured.
 */
@Composable
fun ExtensionRepoSettingsScreen(
    repoRepository: ExtensionRepoRepository,
    onBack: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val repos by repoRepository.repos.collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var repoUrlInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        Log.i(TAG, "Repo settings opened — ${repos.size} repos configured")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(title = "Repositories", scrollState = scrollState)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    bottom = 110.dp,
                    top = 8.dp,
                ),
            ) {
                items(repos) { repo ->
                    RepoRow(
                        repo = repo,
                        onDelete = {
                            Log.i(TAG, "Deleting repo: ${repo.baseUrl}")
                            scope.launch { repoRepository.delete(repo.baseUrl) }
                        },
                    )
                }

                if (repos.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No repositories. Tap + to add one.",
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // FAB — add repo
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "Add Repository")
        }
    }

    // Add repo dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = "Add Repository",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                OutlinedTextField(
                    value = repoUrlInput,
                    onValueChange = { repoUrlInput = it },
                    label = { Text("Repository URL") },
                    placeholder = { Text("https://raw.githubusercontent.com/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val url = repoUrlInput.trim()
                        if (url.isNotEmpty()) {
                            Log.i(TAG, "Adding repo: $url")
                            scope.launch {
                                repoRepository.insert(ExtensionRepo(baseUrl = url))
                            }
                            repoUrlInput = ""
                            showAddDialog = false
                        }
                    },
                ) {
                    Text("Add", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
        )
    }
}

@Composable
private fun RepoRow(
    repo: ExtensionRepo,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.name ?: repo.baseUrl,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = repo.baseUrl,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
