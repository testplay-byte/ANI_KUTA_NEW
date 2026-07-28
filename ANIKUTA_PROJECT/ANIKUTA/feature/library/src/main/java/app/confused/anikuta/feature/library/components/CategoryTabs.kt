package app.confused.anikuta.feature.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.common.model.Category
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.feature.library.CategoryFilter

/**
 * Horizontal scrollable category-tab strip.
 *
 * "All" is always first. Then each visible category. Active tab is highlighted
 * with the primary color + a 32dp × 2dp underline.
 *
 * In selection mode, the caller replaces this with a Select All / Clear bar.
 */
@Composable
fun CategoryTabs(
    categories: List<Category>,
    activeFilter: CategoryFilter,
    onSelect: (CategoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            CategoryTab(
                label = "All",
                isActive = activeFilter is CategoryFilter.All,
                onClick = { onSelect(CategoryFilter.All) },
            )
            categories.forEach { category ->
                CategoryTab(
                    label = category.name,
                    isActive = activeFilter is CategoryFilter.One &&
                        (activeFilter as CategoryFilter.One).category.id == category.id,
                    onClick = { onSelect(CategoryFilter.One(category)) },
                )
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun CategoryTab(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(2.dp)
                .background(
                    if (isActive) MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.Transparent,
                ),
        )
    }
}
