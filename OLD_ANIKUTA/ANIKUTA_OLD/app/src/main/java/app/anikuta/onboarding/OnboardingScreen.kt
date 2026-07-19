package app.anikuta.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.anikuta.ui.theme.AnikutaSprings
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
) {
    var state by remember { mutableStateOf(OnboardingState()) }

    // M3 Expressive: full-screen gradient background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Expressive progress indicator — rounded, spring-animated
            ExpressiveProgressBar(
                current = state.currentStep,
                total = state.totalSteps,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Step ${state.currentStep + 1} of ${state.totalSteps}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Step content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                when (state.currentStep) {
                    0 -> ExpressiveWelcomeStep()
                    1 -> ExpressivePermissionsStep()
                    2 -> ExpressiveStorageStep(
                        onFolderSelected = { uri -> state = state.copy(storageFolderUri = uri) },
                        selectedUri = state.storageFolderUri,
                    )
                    3 -> ExpressiveExtensionStep(
                        onPrimarySelected = { pkg -> state = state.copy(primaryExtensionPkg = pkg) },
                        primaryPkg = state.primaryExtensionPkg,
                    )
                    4 -> ExpressiveBackupStep(
                        onBackupSelected = { uri -> state = state.copy(backupFileUri = uri) },
                        onSkip = { state = state.copy(backupFileUri = null) },
                        selectedUri = state.backupFileUri,
                    )
                    5 -> ExpressiveDesignStep(
                        onDesignSelected = { design -> state = state.copy(selectedDesign = design) },
                        selectedDesign = state.selectedDesign,
                    )
                    6 -> ExpressiveAllSetStep()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Expressive navigation buttons — spring press feedback
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (state.currentStep > 0) {
                    ExpressiveTextButton(
                        text = "Back",
                        onClick = { state = state.previous() },
                    )
                } else {
                    Spacer(modifier = Modifier.width(64.dp))
                }

                ExpressiveButton(
                    text = if (state.currentStep == state.totalSteps - 1) "Start Watching" else "Next",
                    onClick = {
                        // Step 3 (Extension): trust the selected extension on Next.
                        val selectedPkg = state.primaryExtensionPkg
                        if (state.currentStep == 3 && selectedPkg != null) {
                            try {
                                val manager = Injekt.get<app.anikuta.extension.anime.AnimeExtensionManager>()
                                manager.trust(selectedPkg)
                                Log.i("Onboarding", "Trusted $selectedPkg on Next")
                            } catch (e: Exception) {
                                Log.e("Onboarding", "Trust on Next failed", e)
                            }
                        }
                        if (state.currentStep == state.totalSteps - 1) onComplete()
                        else state = state.next()
                    },
                    enabled = state.canProceed(),
                )
            }
        }
    }
}

@Composable
private fun ExpressiveProgressBar(current: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(total) { index ->
            val isActive = index <= current
            val animatedWeight by animateFloatAsState(
                targetValue = if (index == current) 2f else 1f,
                animationSpec = AnikutaSprings.effects,
                label = "progress_$index",
            )
            Surface(
                modifier = Modifier.weight(animatedWeight).height(6.dp),
                shape = RoundedCornerShape(3.dp),
                color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {}
        }
    }
}

@Composable
private fun ExpressiveButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = AnikutaSprings.press,
        label = "btn_scale",
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier.scale(scale),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ExpressiveTextButton(text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = AnikutaSprings.press,
        label = "text_btn_scale",
    )
    TextButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier.scale(scale),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ExpressiveWelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Expressive icon in a large tonal container
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "ANI-KUTA",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Your anime, your way.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Let's get you set up. This takes about a minute.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ExpressivePermissionsStep() {
    val context = LocalContext.current

    // ---- Notification permission (Android 13+ / API 33+) ----
    val isApi33Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val initialNotifGranted = if (isApi33Plus) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    var notifGranted by remember { mutableStateOf(initialNotifGranted) }
    var notifAsked by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifGranted = granted
        notifAsked = true
    }

    // ---- Install from unknown sources (API 26+) ----
    // CanInstallApps checks if the user has granted "install unknown apps"
    // permission for this app. Without it, extension APKs can't be installed.
    val initialInstallGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.packageManager.canRequestPackageInstalls()
    } else {
        true  // Pre-O doesn't need this permission
    }
    var installGranted by remember { mutableStateOf(initialInstallGranted) }

    // ---- Storage permission (API 33+ uses READ_MEDIA_*, older uses READ/WRITE_EXTERNAL_STORAGE) ----
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    }
    val initialStorageGranted = ContextCompat.checkSelfPermission(
        context,
        storagePermission,
    ) == PackageManager.PERMISSION_GRANTED
    var storageGranted by remember { mutableStateOf(initialStorageGranted) }
    var storageAsked by remember { mutableStateOf(false) }

    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        storageGranted = granted
        storageAsked = true
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Permissions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "ANI-KUTA needs a few permissions to work properly. " +
                "You can skip these and grant them later in Settings.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))

        // ---- Notifications card ----
        PermissionCard(
            name = "Notifications",
            desc = when {
                notifGranted -> "Granted — download + schedule updates"
                notifAsked -> "Denied — notifications won't be shown"
                else -> "For download + schedule updates"
            },
            icon = Icons.Default.Notifications,
            trailing = {
                if (isApi33Plus) {
                    Switch(
                        checked = notifGranted,
                        onCheckedChange = { wantsEnabled ->
                            if (wantsEnabled && !notifGranted) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else if (!wantsEnabled && notifGranted) {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching { context.startActivity(intent) }
                            }
                        },
                    )
                } else {
                    StatusBadge("Granted", granted = true)
                }
            },
        )

        if (notifAsked && !notifGranted && isApi33Plus) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Denied — notifications won't be shown. You can still continue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ---- Storage permission card ----
        PermissionCard(
            name = "Storage",
            desc = when {
                storageGranted -> "Granted — save downloads + cache"
                storageAsked -> "Denied — downloads won't work"
                else -> "For saving downloads and cache"
            },
            icon = Icons.Default.Storage,
            trailing = {
                Switch(
                    checked = storageGranted,
                    onCheckedChange = { wantsEnabled ->
                        if (wantsEnabled && !storageGranted) {
                            storageLauncher.launch(storagePermission)
                        } else if (!wantsEnabled && storageGranted) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(intent) }
                        }
                    },
                )
            },
        )

        if (storageAsked && !storageGranted) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Denied — downloads won't work. You can still continue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ---- Install from unknown sources card (API 26+) ----
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PermissionCard(
                name = "Install unknown apps",
                desc = when {
                    installGranted -> "Granted — install extensions directly"
                    else -> "Required to install extension APKs"
                },
                icon = Icons.Default.InstallMobile,
                trailing = {
                    Switch(
                        checked = installGranted,
                        onCheckedChange = { wantsEnabled ->
                            if (wantsEnabled && !installGranted) {
                                // Navigate to the system settings page where the user
                                // can grant "install unknown apps" for this app.
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}"),
                                ).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching { context.startActivity(intent) }
                            } else if (!wantsEnabled && installGranted) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}"),
                                ).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching { context.startActivity(intent) }
                            }
                        },
                    )
                },
            )
            // Re-check the permission status when the user returns from settings
            LaunchedEffect(installGranted) {
                // This LaunchedEffect re-runs when installGranted changes, but we
                // also want to re-check when the composable recomposes. The user
                // will see the updated state when they return from the settings page.
            }
            // Manual refresh button
            TextButton(
                onClick = {
                    installGranted = context.packageManager.canRequestPackageInstalls()
                    storageGranted = ContextCompat.checkSelfPermission(
                        context,
                        storagePermission,
                    ) == PackageManager.PERMISSION_GRANTED
                    notifGranted = if (isApi33Plus) {
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) == PackageManager.PERMISSION_GRANTED
                    } else true
                },
            ) {
                Text("Refresh status", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "You can grant these later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Small tonal status chip used as the trailing element of a permission card. */
@Composable
private fun StatusBadge(text: String, granted: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (granted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (granted) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PermissionCard(
    name: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

@Composable
private fun ExpressiveStorageStep(onFolderSelected: (String) -> Unit, selectedUri: String?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val storagePrefs = remember { uy.kohesive.injekt.Injekt.get<app.anikuta.storage.StoragePreferences>() }

    // Real SAF folder picker — launches ACTION_OPEN_DOCUMENT_TREE.
    val pickFolder = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            // Take persistable permission so the URI survives process death.
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: SecurityException) {
                android.util.Log.w("Onboarding", "Persistable URI grant failed (non-fatal)", e)
            }
            // Persist the URI immediately (survives onboarding cancel/relaunch).
            storagePrefs.baseStorageDirectory().set(uri.toString())
            onFolderSelected(uri.toString())
        }
    }

    // Show a readable path for the selected URI.
    val displayPath = remember(selectedUri) {
        if (selectedUri == null) null
        else if (selectedUri.startsWith("content://")) {
            try {
                com.hippo.unifile.UniFile.fromUri(context, android.net.Uri.parse(selectedUri))?.filePath
                    ?: selectedUri.takeLast(40)
            } catch (e: Exception) { selectedUri.takeLast(40) }
        } else {
            selectedUri
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Storage Folder", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Pick where ANI-KUTA stores downloads, data, backups, and cache.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        // Primary: launch SAF picker
        Surface(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = if (selectedUri != null) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 1.dp,
            onClick = {
                try { pickFolder.launch(null) }
                catch (e: android.content.ActivityNotFoundException) {
                    android.util.Log.e("Onboarding", "No SAF picker available", e)
                }
            },
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (selectedUri != null) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (selectedUri != null) "Folder Selected" else "Select Folder",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        displayPath ?: "Tap to choose a folder on your device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                if (selectedUri != null) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun ExpressiveExtensionStep(
    onPrimarySelected: (String) -> Unit,
    primaryPkg: String?,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var installedExtensions by remember { mutableStateOf<List<app.anikuta.extension.anime.model.AnimeExtension.Untrusted>>(emptyList()) }
    var trustedExtensions by remember { mutableStateOf<List<app.anikuta.extension.anime.model.AnimeExtension.Installed>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var repoUrl by remember { mutableStateOf("") }
    var repoStatus by remember { mutableStateOf<String?>(null) }

    // Load extensions on first composition
    LaunchedEffect(Unit) {
        try {
            val manager = Injekt.get<app.anikuta.extension.anime.AnimeExtensionManager>()
            manager.reload()
            kotlinx.coroutines.delay(500)
            installedExtensions = manager.untrustedExtensions.value
            trustedExtensions = manager.installedExtensions.value
            Log.d("OnboardingExtension", "Loaded ${installedExtensions.size} untrusted, ${trustedExtensions.size} trusted")
        } catch (e: Exception) {
            Log.e("OnboardingExtension", "Failed to load extensions", e)
        } finally {
            isLoading = false
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Select Extension", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            // Refresh button — more prominent with a surface background
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = {
                    isLoading = true
                    scope.launch {
                        try {
                            val manager = Injekt.get<app.anikuta.extension.anime.AnimeExtensionManager>()
                            manager.reload()
                            kotlinx.coroutines.delay(500)
                            installedExtensions = manager.untrustedExtensions.value
                            trustedExtensions = manager.installedExtensions.value
                            Log.d("OnboardingExtension", "Refreshed: ${installedExtensions.size} untrusted")
                        } catch (e: Exception) {
                            Log.e("OnboardingExtension", "Refresh failed", e)
                        } finally {
                            isLoading = false
                        }
                    }
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Refresh",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        when {
            isLoading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Scanning for installed extensions…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            installedExtensions.isNotEmpty() -> {
                // Show the list of installed (untrusted) extensions.
                // Tapping only SELECTS (visual) — trust happens on Next.
                // Selecting one extension deselects any previously selected one
                // (radio-button behavior — only one primary source).
                installedExtensions.forEach { ext ->
                    val isSelected = primaryPkg == ext.pkgName
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 1.dp,
                        onClick = {
                            // Only select — don't trust yet. Trust happens on Next.
                            onPrimarySelected(ext.pkgName)
                            Log.d("OnboardingExtension", "Selected ${ext.name} (trust deferred to Next)")
                        },
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            ext.icon?.let { drawable ->
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.graphics.painter.BitmapPainter(
                                        drawable.toBitmap().asImageBitmap(),
                                    ),
                                    contentDescription = ext.name,
                                    modifier = Modifier.size(40.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            } ?: run {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ext.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("v${ext.versionName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            else -> {
                // No extensions installed — prompt for repo URL
                Icon(
                    Icons.Default.Extension,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No extensions installed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Add an extension repository to install extensions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    label = { Text("Repository URL") },
                    placeholder = { Text("https://example.com/index.min.json") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            repoStatus = "Adding repository…"
                            try {
                                val createRepo = Injekt.get<app.anikuta.domain.mihon.extensionrepo.anime.interactor.CreateAnimeExtensionRepo>()
                                val result = createRepo.await(repoUrl.trim())
                                repoStatus = when (result) {
                                    is app.anikuta.domain.mihon.extensionrepo.anime.interactor.CreateAnimeExtensionRepo.Result.Success -> "Repository added! Install extensions from Settings → Extensions."
                                    is app.anikuta.domain.mihon.extensionrepo.anime.interactor.CreateAnimeExtensionRepo.Result.InvalidUrl -> "Invalid URL"
                                    is app.anikuta.domain.mihon.extensionrepo.anime.interactor.CreateAnimeExtensionRepo.Result.RepoAlreadyExists -> "Repository already exists"
                                    else -> "Failed to add repository"
                                }
                            } catch (e: Exception) {
                                repoStatus = "Error: ${e.message}"
                            }
                        }
                    },
                    enabled = repoUrl.isNotBlank(),
                ) {
                    Text("Add Repository")
                }
                repoStatus?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "You can add more extensions later in Settings → Extensions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExpressiveBackupStep(
    onBackupSelected: (String) -> Unit,
    onSkip: () -> Unit,
    selectedUri: String?,
) {
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) onBackupSelected(uri.toString())
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Backup Restore", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Have a backup file from a previous install?", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                shape = RoundedCornerShape(16.dp),
            ) { Text("Select Backup") }
            TextButton(onClick = { onSkip() }) { Text("Skip — fresh start") }
        }
        if (selectedUri != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Backup selected: $selectedUri", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text("No backup selected — fresh start", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpressiveDesignStep(onDesignSelected: (String) -> Unit, selectedDesign: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Pick a Design", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Choose how ANI-KUTA looks. You can change this later.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(24.dp))
        // Expressive design cards — large, contained, with icons
        ExpressiveDesignCard("Material 3", "Clean, standard, Material You", "material3", selectedDesign, onDesignSelected, Icons.Default.Palette)
        ExpressiveDesignCard("Dark Neon", "Coming soon", "neon", selectedDesign, onDesignSelected, Icons.Default.Bolt, enabled = false)
        ExpressiveDesignCard("Neobrutalism", "Coming soon", "neobrutalism", selectedDesign, onDesignSelected, Icons.Default.Brush, enabled = false)
        ExpressiveDesignCard("Coffee Notebook", "Coming soon", "coffee", selectedDesign, onDesignSelected, Icons.Default.Coffee, enabled = false)
    }
}

@Composable
private fun ExpressiveDesignCard(
    name: String,
    desc: String,
    id: String,
    selected: String,
    onSelect: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
) {
    val isSelected = selected == id
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (isSelected) 2.dp else 1.dp,
        onClick = if (enabled) ({ onSelect(id) }) else ({}),
        enabled = enabled,
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSelected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ExpressiveAllSetStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Expressive success icon — large, tonal container
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("You're all set!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        // Expressive tip cards
        TipCard("Browse anime on the home page", Icons.Default.Home)
        Spacer(modifier = Modifier.height(8.dp))
        TipCard("Search by name or genre", Icons.Default.Search)
        Spacer(modifier = Modifier.height(8.dp))
        TipCard("Settings in the More tab", Icons.Default.Settings)
    }
}

@Composable
private fun TipCard(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
