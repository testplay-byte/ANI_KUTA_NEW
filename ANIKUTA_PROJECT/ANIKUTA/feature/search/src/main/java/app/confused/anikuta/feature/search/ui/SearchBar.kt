package app.confused.anikuta.feature.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * The search input bar — two sizes (full + compact).
 *
 * Ported from the prototype's `SearchScreen.kt`'s private `SearchBar` composable.
 * Visual rules (copy-paste exactly):
 * - Full size: 52dp height, 20dp search icon, 16sp text.
 * - Compact size: 44dp height, 18dp search icon, 14sp text (sits beside the
 *   title when the top bar is collapsed).
 * - Shape: `RoundedCornerShape(50)` (pill).
 * - Background: `surfaceVariant` at 40% alpha.
 * - Search icon (left) + BasicTextField (weight 1f) + clear button (right, only
 *   when text is non-empty).
 * - `cursorBrush = primary` (the #B1F256 lime green).
 * - `KeyboardOptions(imeAction = Search)` + `KeyboardActions(onSearch = ...)`.
 *
 * @param value the current query.
 * @param onChange called on every keystroke.
 * @param onClear called when the clear (X) button is tapped.
 * @param onSubmit called when the user hits the IME Search action.
 * @param compact `true` for the collapsed-top-bar variant (44dp), `false` for
 *   the expanded variant (52dp).
 */
@Composable
fun SearchBar(
    value: String,
    onChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val height = if (compact) 44.dp else 52.dp
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (compact) 18.dp else 20.dp),
            )
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    fontSize = if (compact) 14.sp else 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = RobotoFamily,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSubmit()
                    keyboard?.hide()
                }),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            text = "Search anime...",
                            fontSize = if (compact) 14.sp else 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = RobotoFamily,
                        )
                    }
                    innerTextField()
                },
            )
            if (value.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onClear() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
