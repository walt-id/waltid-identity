package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
            if (value.value) "Yes" else "No",
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
            value.values.take(MaxListPreviewItems).forEachIndexed { index, child ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium)
                    ClaimValue(child, path.indexedChild(index), Modifier.weight(1f))
                }
            }
            if (value.values.size > MaxListPreviewItems) {
                Text(
                    "Showing first $MaxListPreviewItems of ${value.values.size} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

private const val MaxListPreviewItems = 25

@Composable
private fun ImageValue(value: DisplayValue.Image, path: ClaimItemPath, modifier: Modifier = Modifier) {
    var viewerOpen by rememberSaveable(path.id) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .testTag(WalletUiTestTags.claimImage(path.id))
                .clickable(
                    onClickLabel = "View credential image full screen",
                    onClick = { viewerOpen = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = value.bytes,
                contentDescription = "Credential image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            value.mimeType,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "${value.byteCount} bytes",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (viewerOpen) {
        CredentialImageViewer(
            value = value,
            path = path,
            onDismiss = { viewerOpen = false },
        )
    }
}

@Composable
private fun CredentialImageViewer(
    value: DisplayValue.Image,
    path: ClaimItemPath,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .testTag(WalletUiTestTags.claimImageViewer(path.id)),
        ) {
            AsyncImage(
                model = value.bytes,
                contentDescription = "Full-screen credential image",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 64.dp),
                contentScale = ContentScale.Fit,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.48f), CircleShape)
                    .testTag(WalletUiTestTags.claimImageViewerClose(path.id)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close full-screen credential image",
                    tint = Color.White,
                )
            }
        }
    }
}
