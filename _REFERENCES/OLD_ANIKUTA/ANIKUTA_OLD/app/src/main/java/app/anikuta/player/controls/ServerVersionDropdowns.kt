package app.anikuta.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.anikuta.player.PlayerViewModel

/**
 * Phase 1.5 — Server + version dropdowns for the minimized player view.
 *
 * Two dropdown menus side by side below the video:
 *  - Server: shows available servers (e.g., Anikoto, Gogo, Zoro)
 *  - Audio: shows available audio versions (e.g., SUB, DUB, HSUB)
 *
 * When tapped, the dropdown expands downward showing all available options.
 * Selecting an option collapses the dropdown and applies the selection.
 */
@Composable
fun ServerVersionDropdowns(
    viewModel: PlayerViewModel,
    onServerSelected: (String) -> Unit,
    onAudioVersionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val availableServers by viewModel.availableServers.collectAsState()
    val currentServer by viewModel.currentServer.collectAsState()

    // FIX (Part 4): Use REAL audio versions from the ViewModel (derived from
    // what the source actually provides for this episode). Previously this
    // was hardcoded to listOf("SUB","DUB","HSUB") which showed all three even
    // when some weren't available, causing failed switches.
    val audioVersions by viewModel.availableAudioVersions.collectAsState()
    val currentAudioVersion by viewModel.currentAudioVersion.collectAsState()

    var serverDropdownOpen by remember { mutableStateOf(false) }
    var audioDropdownOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Server dropdown
        DropdownSelector(
            label = "Server",
            value = currentServer.ifBlank { "Default" },
            options = availableServers.ifEmpty { listOf("Default") },
            isOpen = serverDropdownOpen,
            onToggle = {
                serverDropdownOpen = !serverDropdownOpen
                audioDropdownOpen = false
            },
            onSelect = { server ->
                onServerSelected(server)
                serverDropdownOpen = false
            },
            modifier = Modifier.weight(1f),
        )

        // Audio version dropdown
        DropdownSelector(
            label = "Audio",
            value = currentAudioVersion.ifBlank { "SUB" },
            options = audioVersions.ifEmpty { listOf(currentAudioVersion.ifBlank { "SUB" }) },
            isOpen = audioDropdownOpen,
            onToggle = {
                audioDropdownOpen = !audioDropdownOpen
                serverDropdownOpen = false
            },
            onSelect = { version ->
                onAudioVersionSelected(version)
                audioDropdownOpen = false
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A single dropdown selector that expands downward when tapped.
 * Shows a label + current value + arrow icon. When open, shows the list of
 * options below.
 */
@Composable
private fun DropdownSelector(
    label: String,
    value: String,
    options: List<String>,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Header (always visible)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Dropdown options (expand downward)
        AnimatedVisibility(
            visible = isOpen,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (option == value) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (option == value) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                            )
                            if (option == value) {
                                Text(
                                    text = "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
