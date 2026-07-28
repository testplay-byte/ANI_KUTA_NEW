package app.confused.anikuta.feature.library.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * Floating action bar shown at the bottom of the library while in selection mode.
 *
 * Three equally-weighted slots: Cancel (always enabled), Category (disabled when
 * nothing selected), Delete (disabled when nothing selected, rendered in
 * `colorScheme.error`).
 *
 * Per the prototype's `SelectionActionBar`: a pill-shaped surface with shadow,
 * 58dp tall, three icon+label buttons.
 */
@Composable
fun SelectionActionBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onCategory: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            SelectionBarButton(
                icon = Icons.Filled.Close,
                label = "Cancel",
                enabled = true,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            SelectionBarButton(
                icon = Icons.Filled.CreateNewFolder,
                label = "Category",
                enabled = selectedCount > 0,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onCategory,
                modifier = Modifier.weight(1f),
            )
            SelectionBarButton(
                icon = Icons.Filled.Delete,
                label = "Delete",
                enabled = selectedCount > 0,
                color = MaterialTheme.colorScheme.error,
                onClick = onDelete,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectionBarButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.35f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = RobotoFamily,
        )
    }
}
