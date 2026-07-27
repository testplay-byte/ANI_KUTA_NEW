package app.confused.anikuta.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A section header — accent-colored, left-aligned text.
 *
 * Per `DESIGN_LANGUAGE/01-principles/core-principles.md` #6 (accent-colored
 * left-aligned section headers) + `02-components/components.md` §9.
 *
 * Used on: History screen (Today/Yesterday/Earlier), Settings sections,
 * Browse/Library group headers.
 *
 * @param text The section label.
 * @param modifier Standard modifier.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
