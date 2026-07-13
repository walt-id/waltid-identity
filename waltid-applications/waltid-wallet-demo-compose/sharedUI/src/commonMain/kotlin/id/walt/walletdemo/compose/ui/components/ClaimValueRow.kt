package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.ClaimItemPath
import id.walt.walletdemo.compose.logic.DisplayValue
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun ClaimValueRow(item: ClaimItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.claim(item.path.id)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            item.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ClaimValue(value = item.value, path = item.path, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ClaimValue(value: DisplayValue, path: ClaimItemPath, modifier: Modifier = Modifier) {
    when (value) {
        is DisplayValue.BooleanValue -> Text(
            value.value.toString(),
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
        )
        is DisplayValue.DecodedText -> Text(
            value.value,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
        )
        is DisplayValue.Image -> ImageValue(value, path, modifier)
        is DisplayValue.ListValue -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            value.values.forEachIndexed { index, child ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium)
                    ClaimValue(child, path.indexedChild(index), Modifier.weight(1f))
                }
            }
        }
        DisplayValue.NullValue -> Text(
            "Not provided",
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is DisplayValue.NumberValue -> Text(
            value.value,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
        )
        is DisplayValue.ObjectValue -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            value.entries.forEach { entry ->
                ClaimValueRow(entry)
            }
        }
        is DisplayValue.Raw -> Text(
            value.value,
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is DisplayValue.Text -> Text(
            value.value,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ImageValue(value: DisplayValue.Image, path: ClaimItemPath, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .testTag(WalletUiTestTags.claimImage(path.id))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AsyncImage(
                model = value.bytes,
                contentDescription = "Credential image",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp, max = 180.dp),
                contentScale = ContentScale.Fit,
            )
            Text(
                value.mimeType,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                listOfNotNull(
                    "${value.byteCount} bytes",
                ).joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
