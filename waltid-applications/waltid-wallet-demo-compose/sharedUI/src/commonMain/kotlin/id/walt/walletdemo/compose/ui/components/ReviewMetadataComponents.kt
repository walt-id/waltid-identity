package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import id.walt.walletdemo.compose.logic.WalletDemoMetadataDisplay
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun ReviewMetadataSection(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
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
                modifier = Modifier.padding(contentPadding),
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
    val supportingText: String? = null,
    val linkUri: String? = null,
    val sourcePath: String? = null,
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
    val supportingText = item.supportingText?.takeIf(String::isNotBlank)
    val prefersStacked = supportingText != null || item.label.length > 28 || value.length > 38 ||
        item.label.contains('\n') || value.contains('\n') || linkUri != null

    BoxWithConstraints(
        modifier = item.sourcePath
            ?.let { Modifier.testTag(WalletUiTestTags.claim(it)) }
            ?: Modifier,
    ) {
        if (prefersStacked || maxWidth < 260.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MetadataLabel(item.label)
                MetadataValue(value, linkUri, uriHandler::openUri)
                supportingText?.let { MetadataSupportingText(it) }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetadataLabel(item.label, Modifier.weight(0.42f))
                MetadataValue(
                    value = value,
                    linkUri = linkUri,
                    onLinkClick = uriHandler::openUri,
                    modifier = Modifier.weight(0.58f),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun MetadataLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun MetadataValue(
    value: String,
    linkUri: String?,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodySmall,
        color = if (linkUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        textDecoration = if (linkUri != null) TextDecoration.Underline else TextDecoration.None,
        textAlign = textAlign,
        modifier = modifier.then(
            if (linkUri != null) Modifier.clickable { onLinkClick(linkUri) } else Modifier,
        ),
    )
}

@Composable
private fun MetadataSupportingText(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun MetadataRowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp,
    )
}

@Composable
internal fun MetadataDisclosure(
    title: String,
    initiallyExpanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) content()
    }
}

private fun isHttpsUrl(value: String): Boolean = value.trim().startsWith("https://", ignoreCase = true)
