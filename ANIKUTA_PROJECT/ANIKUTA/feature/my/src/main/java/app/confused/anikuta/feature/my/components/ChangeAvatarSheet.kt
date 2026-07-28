package app.confused.anikuta.feature.my.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage

/**
 * Bottom sheet for changing the display picture (avatar).
 *
 * Shows the current avatar + a text field for entering a new avatar URL.
 * The user can also reset to the AniList avatar (clears the custom URL).
 *
 * Per design language: dragHandle = null (principle #2), max height 70%
 * (principle #3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeAvatarSheet(
    currentAvatarUrl: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(currentAvatarUrl ?: "") }
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxHeight = screenHeight * 0.7f // principle #3: max 70% of viewport

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .padding(top = 16.dp, bottom = 32.dp),
        ) {
            // Header
            Text(
                text = "Change Profile Picture",
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
            )

            // Preview avatar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Current/preview avatar
                if (url.isNotBlank()) {
                    AsyncImage(
                        model = url,
                        contentDescription = "Avatar preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp),
                    ) {}
                }

                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = "Preview",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (url.isBlank()) "Will use AniList avatar" else "Custom avatar",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // URL input
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Avatar Image URL", fontFamily = RobotoFamily) },
                placeholder = { Text("https://...", fontFamily = RobotoFamily, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Reset button
                OutlinedButton(
                    onClick = {
                        url = ""
                        onConfirm("")
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Reset", fontFamily = RobotoFamily, fontWeight = FontWeight.Bold)
                }
                // Save button
                Button(
                    onClick = { onConfirm(url) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text("Save", fontFamily = RobotoFamily, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
