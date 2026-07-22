package app.confused.anikuta.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.model.LibrarySortType
import app.confused.anikuta.core.designsystem.component.AnikutaBottomSheet
import app.confused.anikuta.core.designsystem.component.SectionHeader
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * Sort sheet — bottom-up, no drag handle (design principle #2).
 *
 * Per user decision Q2: sort is GLOBAL.
 *
 * Shows the 5 sort options (Title, Date added, Last watched, Progress,
 * Total episodes). Tapping an option selects it. Tapping the same option
 * again toggles the direction (ascending/descending).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(
    sortType: LibrarySortType,
    ascending: Boolean,
    onSortChange: (LibrarySortType, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AnikutaBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Sort",
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))

            SectionHeader(text = "Sort By")

            SortOptionRow(
                label = "Title",
                isSelected = sortType == LibrarySortType.TITLE,
                ascending = ascending,
                onClick = {
                    if (sortType == LibrarySortType.TITLE) {
                        onSortChange(sortType, !ascending)
                    } else {
                        onSortChange(LibrarySortType.TITLE, true)
                    }
                },
            )
            SortOptionRow(
                label = "Date Added",
                isSelected = sortType == LibrarySortType.DATE_ADDED,
                ascending = ascending,
                onClick = {
                    if (sortType == LibrarySortType.DATE_ADDED) {
                        onSortChange(sortType, !ascending)
                    } else {
                        onSortChange(LibrarySortType.DATE_ADDED, false)
                    }
                },
            )
            SortOptionRow(
                label = "Last Watched",
                isSelected = sortType == LibrarySortType.LAST_WATCHED,
                ascending = ascending,
                onClick = {
                    if (sortType == LibrarySortType.LAST_WATCHED) {
                        onSortChange(sortType, !ascending)
                    } else {
                        onSortChange(LibrarySortType.LAST_WATCHED, false)
                    }
                },
            )
            SortOptionRow(
                label = "Progress",
                isSelected = sortType == LibrarySortType.PROGRESS,
                ascending = ascending,
                onClick = {
                    if (sortType == LibrarySortType.PROGRESS) {
                        onSortChange(sortType, !ascending)
                    } else {
                        onSortChange(LibrarySortType.PROGRESS, false)
                    }
                },
            )
            SortOptionRow(
                label = "Total Episodes",
                isSelected = sortType == LibrarySortType.TOTAL_EPISODES,
                ascending = ascending,
                onClick = {
                    if (sortType == LibrarySortType.TOTAL_EPISODES) {
                        onSortChange(sortType, !ascending)
                    } else {
                        onSortChange(LibrarySortType.TOTAL_EPISODES, false)
                    }
                },
            )
        }
    }
}

@Composable
private fun SortOptionRow(
    label: String,
    isSelected: Boolean,
    ascending: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
        )
        if (isSelected) {
            Icon(
                imageVector = if (ascending) Icons.Filled.ArrowUpward
                              else Icons.Filled.ArrowDownward,
                contentDescription = if (ascending) "Ascending" else "Descending",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
