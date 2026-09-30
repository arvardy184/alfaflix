package com.application.alfaflix.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.application.alfaflix.ui.theme.Spacing

/**
 * A section title.
 *
 * Marked as a heading so screen-reader users can jump between sections instead of reading
 * the whole screen linearly.
 */
@Composable
fun SectionHeader(
    title: String,
    horizontalPadding: Dp = Spacing.lg,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = Spacing.sm)
            .semantics { heading() },
    )
}
