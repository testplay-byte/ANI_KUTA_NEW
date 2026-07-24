package app.confused.anikuta.feature.my.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import coil3.compose.AsyncImage

/**
 * Section 1: Profile Header.
 *
 * Shows the user's avatar (user-customized or AniList avatar if linked, else a
 * placeholder), display name (user-customized or AniList username or "Local User"),
 * and connection status.
 *
 * The Link AniList button has been REMOVED from here (per user feedback).
 * Tracker linking is done via the Trackers settings page.
 */
@Composable
fun ProfileHeader(
    displayName: String?,
    avatarUrl: String?,
    isAniListLinked: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar (80dp circle)
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Profile avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
            )
        } else {
            // Default placeholder: Person icon in a surfaceVariant circle
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(80.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Default avatar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(16.dp))

        // Display name + connection status
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName ?: "Local User",
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = if (isAniListLinked) "AniList connected" else "Not connected",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = if (isAniListLinked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
