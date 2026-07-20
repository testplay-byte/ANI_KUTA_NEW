package app.confused.anikuta.feature.extensionssettings

import android.util.Log
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import app.confused.anikuta.data.extension.repo.ExtensionRepoApi
import app.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import app.confused.anikuta.data.extension.repo.RepoVerificationResult
import kotlinx.coroutines.launch

private const val TAG = "AnikutaRepoUI"

@Composable
fun ExtensionRepoSettingsScreen(
    repoRepository: ExtensionRepoRepository,
    repoApi: ExtensionRepoApi? = null,
    onBack: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val repos by repoRepository.repos.collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var repoUrlInput by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var verificationError by remember { mutableStateOf<String?>(null) }

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

        FloatingActionButton(
            onClick = { showAddDialog = true; verificationError = null; repoUrlInput = "" },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "Add Repository")
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; isVerifying = false; verificationError = null },
            title = {
                Text(
                    text = "Add Repository",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = repoUrlInput,
                        onValueChange = { repoUrlInput = it; verificationError = null },
                        label = { Text("Repository URL") },
                        placeholder = { Text("https://raw.githubusercontent.com/...") },
                        singleLine = true,
                        enabled = !isVerifying,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Show verification status
                    when {
                        isVerifying -> {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = "Verifying repository...",
                                    fontSize = 13.sp,
                                    fontFamily = RobotoFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        verificationError != null -> {
                            Text(
                                text = verificationError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontFamily = RobotoFamily,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        repoUrlInput.isNotEmpty() && !repoUrlInput.trim().startsWith("http") -> {
                            Text(
                                text = "URL must start with http:// or https://",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontFamily = RobotoFamily,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val url = repoUrlInput.trim()
                        if (url.isNotEmpty() && url.startsWith("http")) {
                            if (repoApi != null) {
                                // Verify the repo by fetching its index
                                isVerifying = true
                                verificationError = null
                                scope.launch {
                                    val result = repoApi.verifyRepo(url)
                                    isVerifying = false
                                    when (result) {
                                        is RepoVerificationResult.Success -> {
                                            Log.i(TAG, "Repo verified: ${result.cleanUrl} (${result.extensionCount} extensions)")
                                            repoRepository.insert(ExtensionRepo(
                                                baseUrl = result.cleanUrl,
                                                name = result.cleanUrl.substringAfterLast("/").ifEmpty { result.cleanUrl },
                                            ))
                                            repoUrlInput = ""
                                            showAddDialog = false
                                        }
                                        is RepoVerificationResult.Error -> {
                                            Log.w(TAG, "Repo verification failed: ${result.message}")
                                            verificationError = result.message
                                        }
                                    }
                                }
                            } else {
                                // No API available — just add without verification (fallback)
                                val cleanUrl = url
                                    .removeSuffix("/index.json")
                                    .removeSuffix("/index.min.json")
                                Log.i(TAG, "Adding repo (no verification): $cleanUrl")
                                scope.launch {
                                    repoRepository.insert(ExtensionRepo(
                                        baseUrl = cleanUrl,
                                        name = cleanUrl.substringAfterLast("/").ifEmpty { cleanUrl },
                                    ))
                                }
                                repoUrlInput = ""
                                showAddDialog = false
                            }
                        }
                    },
                    enabled = !isVerifying && repoUrlInput.trim().isNotEmpty() && repoUrlInput.trim().startsWith("http"),
                ) {
                    Text("Add", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddDialog = false; isVerifying = false; verificationError = null },
                    enabled = !isVerifying,
                ) {
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
                    text = repo.name.ifEmpty { repo.baseUrl },
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
            IconButton(onClick = onDelete) {
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
