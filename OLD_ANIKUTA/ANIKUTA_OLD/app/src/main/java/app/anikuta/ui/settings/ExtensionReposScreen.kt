package app.anikuta.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.anikuta.domain.mihon.extensionrepo.model.ExtensionRepo

/**
 * Phase 7 — Extension Repositories settings screen.
 *
 * Lists all configured extension repos. Users can add new repos (by entering
 * the `index.min.json` URL), delete repos, and open the repo's website.
 *
 * Once a repo is added, `AnimeExtensionApi.findExtensions()` fetches
 * `/index.min.json` from it and the "Available" section of the Extensions
 * screen populates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionReposScreen(onBack: () -> Unit) {
    val viewModel: ExtensionReposViewModel = viewModel()
    val repos by viewModel.repos.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val createResult by viewModel.createResult.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }

    // Handle create results
    createResult.let { result ->
        when (result) {
            is ExtensionReposViewModel.CreateResult.Success -> {
                viewModel.dismissCreateResult()
            }
            is ExtensionReposViewModel.CreateResult.Error -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissCreateResult() },
                    title = { Text("Error") },
                    text = { Text(result.message) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.dismissCreateResult() }) { Text("OK") }
                    },
                )
            }
            is ExtensionReposViewModel.CreateResult.DuplicateFingerprint -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissCreateResult() },
                    title = { Text("Duplicate signing key") },
                    text = {
                        Text("This repository uses the same signing key as '${result.oldRepo.name}'. Replace it?")
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            // TODO: call ReplaceAnimeExtensionRepo
                            viewModel.dismissCreateResult()
                        }) { Text("Replace") }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissCreateResult() }) { Text("Cancel") }
                    },
                )
            }
            else -> {}
        }
    }

    // Add repo dialog
    if (showAddDialog) {
        AddRepoDialog(
            onDismiss = { showAddDialog = false },
            onCreate = { url ->
                viewModel.createRepo(url)
                showAddDialog = false
            },
        )
    }

    SettingsSubpageScaffold(
        title = "Repositories",
        onBack = onBack,
        actions = {
            IconButton(onClick = { viewModel.refreshRepos() }, enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh repos")
                }
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (repos.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No repositories added yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Tap + to add an extension repository.\nThe URL must end with /index.min.json",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(repos, key = { it.baseUrl }) { repo ->
                        RepoRow(
                            repo = repo,
                            onOpenWebsite = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repo.website))
                                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            },
                            onDelete = { viewModel.deleteRepo(repo.baseUrl) },
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add repository")
            }
        }
    }
}

@Composable
private fun RepoRow(
    repo: ExtensionRepo,
    onOpenWebsite: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = repo.baseUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onOpenWebsite) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open website")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddRepoDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add repository") },
        text = {
            Column {
                Text(
                    "Enter the repository URL. It must end with /index.min.json",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com/index.min.json") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(url.trim()) },
                enabled = url.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
