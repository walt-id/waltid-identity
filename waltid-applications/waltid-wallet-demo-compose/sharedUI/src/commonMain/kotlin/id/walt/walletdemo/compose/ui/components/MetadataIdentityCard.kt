package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import id.walt.walletdemo.compose.logic.MetadataIdentityDisplay

@Composable
internal fun MetadataIdentityCard(
    identity: MetadataIdentityDisplay,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetadataLogo(identity)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    identity.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    identity.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MetadataLogo(identity: MetadataIdentityDisplay) {
    Surface(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (identity.logoUri.isNullOrBlank()) {
            MetadataLogoFallback(identity)
        } else {
            SubcomposeAsyncImage(
                model = identity.logoUri,
                contentDescription = "${identity.title} logo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                loading = { MetadataLogoFallback(identity) },
                error = { MetadataLogoFallback(identity) },
            )
        }
    }
}

@Composable
private fun MetadataLogoFallback(identity: MetadataIdentityDisplay) {
    Box(contentAlignment = Alignment.Center) {
        Text(
            identity.title.initials(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun String.initials(): String =
    trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "ID" }
