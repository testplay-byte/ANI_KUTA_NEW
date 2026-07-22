@file:OptIn(ExperimentalMaterial3Api::class)

package app.confused.anikuta.feature.search.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily
import app.confused.anikuta.feature.search.viewmodel.SearchSource

/** The 5 sort options shown in the Sort dropdown (label → AniList enum). */
val SORT_OPTIONS: List<Pair<String, String>> = listOf(
    "Popularity" to "POPULARITY_DESC",
    "Score" to "SCORE_DESC",
    "Newest" to "START_DATE_DESC",
    "Title A-Z" to "TITLE_ROMAJI",
    "Trending" to "TRENDING_DESC",
    "Favourites" to "FAVOURITES_DESC",
)

/**
 * The collapsing top bar — title + source toggle + search bar + quick row.
 *
 * Ported from the prototype's `SearchTopBar` + the inline quick row. The
 * animations are copied EXACTLY:
 * - `titleFontSize`: 36f → 26f, `tween(300, FastOutSlowInEasing)`.
 * - `sourceAlpha`: 1f → 0f, `tween(300, FastOutSlowInEasing)`.
 * - `sourceWidth`: 180dp → 0dp, `tween(300, FastOutSlowInEasing)`.
 * - Search bar below title: `AnimatedVisibility(fadeIn+expandVertically / fadeOut+shrinkVertically)`.
 * - Quick row: `AnimatedVisibility(fadeOut + shrinkVertically)`.
 *
 * Layout:
 * - When expanded: Title (36sp) + SourceToggle (right) on row 1; full SearchBar
 *   (52dp) on row 2; QuickRow on row 3.
 * - When collapsed: Title (26sp) + compact SearchBar (44dp, weight 1f) on row 1;
 *   QuickRow hidden.
 *
 * @param collapsed `true` when the scroll content is scrolled past 20px.
 * @param query the current search query.
 * @param onQueryChange called on every keystroke.
 * @param onClearQuery called when the X is tapped.
 * @param source the selected source.
 * @param onSourceSelect called when the user taps a different source segment.
 * @param onSourceRetap called when the user taps the already-selected segment
 *   (the UI opens the extension source picker per Q2).
 * @param onSubmit called when the IME Search action is triggered.
 * @param activeFilterCount drives the Filters button badge.
 * @param onOpenFilters called when the Filters button is tapped.
 * @param sort the current sort enum value.
 * @param onSortChange called when a new sort is picked from the dropdown.
 */
@Composable
fun SearchTopBar(
    collapsed: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    source: SearchSource,
    onSourceSelect: (SearchSource) -> Unit,
    onSourceRetap: () -> Unit,
    onSubmit: () -> Unit,
    activeFilterCount: Int,
    onOpenFilters: () -> Unit,
    sort: String,
    onSortChange: (String) -> Unit,
) {
    val titleFontSize by animateFloatAsState(
        targetValue = if (collapsed) 26f else 36f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "titleSize",
    )
    val sourceAlpha by animateFloatAsState(
        targetValue = if (collapsed) 0f else 1f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "sourceAlpha",
    )
    val sourceWidth by animateDpAsState(
        targetValue = if (collapsed) 0.dp else 200.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "sourceWidth",
    )

    var showSortDropdown by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val currentSortLabel = SORT_OPTIONS.find { it.second == sort }?.first ?: "Popularity"

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .statusBarsPadding(),
        ) {
            // ── Row 1: Title + (SourceToggle OR compact SearchBar) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Search",
                    fontFamily = RobotoFamily,
                    fontSize = titleFontSize.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.02).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )

                if (collapsed) {
                    // Collapsed: compact search bar beside the title.
                    Spacer(Modifier.width(12.dp))
                    SearchBar(
                        value = query,
                        onChange = onQueryChange,
                        onClear = onClearQuery,
                        onSubmit = onSubmit,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    // Expanded: source toggle on the right.
                    if (sourceWidth > 0.dp) {
                        SourceToggle(
                            source = source,
                            onSelect = { newSource ->
                                if (newSource == source) {
                                    onSourceRetap()
                                } else {
                                    onSourceSelect(newSource)
                                }
                            },
                            modifier = Modifier
                                .width(sourceWidth)
                                .alpha(sourceAlpha),
                        )
                    }
                }
            }

            // ── Row 2: full search bar (expanded only) ──
            AnimatedVisibility(
                visible = !collapsed,
                enter = fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                    expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                    shrinkVertically(animationSpec = tween(200, easing = FastOutSlowInEasing)),
            ) {
                Column {
                    Spacer(Modifier.padding(top = 4.dp))
                    SearchBar(
                        value = query,
                        onChange = onQueryChange,
                        onClear = onClearQuery,
                        onSubmit = onSubmit,
                        compact = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── Row 3: quick row — Filters (left) + Sort (right) — slides out when collapsed ──
            AnimatedVisibility(
                visible = !collapsed,
                enter = fadeIn(),
                exit = fadeOut() + shrinkVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Filters button (LEFT)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .clickable { onOpenFilters() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = "Filters",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 7.dp),
                        )
                        Text(
                            text = "Filters",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (activeFilterCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            ) {
                                Text(
                                    text = activeFilterCount.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }

                    // Sort dropdown (RIGHT)
                    androidx.compose.foundation.layout.Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .clickable { showSortDropdown = !showSortDropdown }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = currentSortLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = if (showSortDropdown) Icons.Filled.KeyboardArrowUp
                                else Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Sort",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(14.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = showSortDropdown,
                            onDismissRequest = { showSortDropdown = false },
                            // Styled container — rounded, elevated, matches the app's pill aesthetic.
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            SORT_OPTIONS.forEach { (value, label) ->
                                val isSelected = value == sort
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            fontFamily = RobotoFamily,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    trailingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        onSortChange(value)
                                        showSortDropdown = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.padding(top = 2.dp))
        }
    }
}
