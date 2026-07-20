package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import id.walt.walletdemo.compose.logic.WalletDemoMetadataDisplay

@Composable
internal fun ReviewMetadataSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun MetadataIdentityRow(
    display: WalletDemoMetadataDisplay?,
    fallbackName: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier,
) {
    val name = display?.name?.takeIf { it.isNotBlank() } ?: fallbackName
    val logoUri = display?.logoUri?.takeIf(::isHttpsUrl)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (logoUri != null) {
                SubcomposeAsyncImage(
                    model = logoUri,
                    contentDescription = display.logoAltText ?: "$name logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    loading = { MetadataLogoFallback(name) },
                    error = { MetadataLogoFallback(name) },
                )
            } else {
                MetadataLogoFallback(name)
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            supportingText?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MetadataLogoFallback(name: String) {
    Text(
        text = name.firstOrNull()?.uppercase() ?: "?",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

internal data class MetadataDetailItem(
    val label: String,
    val value: String?,
    val linkUri: String? = null,
)

@Composable
internal fun MetadataDetailList(
    items: List<MetadataDetailItem>,
    modifier: Modifier = Modifier,
) {
    val visibleItems = items.filter { !it.value.isNullOrBlank() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visibleItems.forEachIndexed { index, item ->
            if (index > 0) MetadataRowDivider()
            MetadataDetailLine(item)
        }
    }
}

@Composable
private fun MetadataDetailLine(item: MetadataDetailItem) {
    val value = item.value?.takeIf { it.isNotBlank() } ?: return
    val linkUri = item.linkUri?.takeIf(::isHttpsUrl)
    val uriHandler = LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (linkUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (linkUri != null) TextDecoration.Underline else TextDecoration.None,
            modifier = if (linkUri != null) Modifier.clickable { uriHandler.openUri(linkUri) } else Modifier,
        )
    }
}

@Composable
internal fun MetadataRowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp,
    )
}

private fun isHttpsUrl(value: String): Boolean = value.trim().startsWith("https://", ignoreCase = true)
