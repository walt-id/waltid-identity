package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/** Credential identity at review scale. Selection and navigation belong to distinct host controls. */
@Composable
internal fun CredentialSummaryRow(
    art: CredentialCardArtModel,
    supportingText: String? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val thumbnailWidth = credentialThumbnailWidth(maxWidth, LocalDensity.current.fontScale)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CredentialCardArt(art, compact = true, modifier = Modifier.width(thumbnailWidth).clearAndSetSemantics {})
            SummaryText(art.name, supportingText, Modifier.weight(1f))
        }
    }
}

internal fun credentialThumbnailWidth(availableWidth: Dp, fontScale: Float): Dp =
    if (fontScale >= 1.3f || availableWidth < 180.dp) 48.dp else 64.dp

@Composable
private fun SummaryText(title: String, supportingText: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        supportingText?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
