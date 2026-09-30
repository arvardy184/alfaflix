package com.application.alfaflix.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.application.alfaflix.R
import com.application.alfaflix.core.result.Staleness
import com.application.alfaflix.ui.theme.Spacing

/**
 * Tells the user their content is saved rather than live.
 *
 * Deliberately quiet — a surface tint and one line of text, never a dialog or a blocking
 * error. The content behind it is still perfectly usable, and the banner's job is to
 * explain, not to interrupt.
 *
 * Marked as a polite live region so a screen reader announces the change in status without
 * cutting off whatever it is currently reading.
 */
@Composable
fun StaleDataBanner(
    staleness: Staleness,
    onRetry: (() -> Unit)? = null,
    horizontalPadding: Dp = Spacing.lg,
    modifier: Modifier = Modifier,
) {
    val relativeTime = remember(staleness.lastUpdatedAt) {
        DateUtils.getRelativeTimeSpanString(
            staleness.lastUpdatedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }

    val headline = when (staleness.reason) {
        Staleness.Reason.OFFLINE -> stringResource(R.string.stale_offline)
        Staleness.Reason.REFRESH_FAILED -> stringResource(R.string.stale_refresh_failed)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = Spacing.sm)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.stale_updated, relativeTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}
