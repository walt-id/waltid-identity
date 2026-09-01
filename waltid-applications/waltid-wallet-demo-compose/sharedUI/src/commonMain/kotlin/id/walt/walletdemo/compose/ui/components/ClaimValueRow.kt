package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.ClaimItemPath
import id.walt.walletdemo.compose.logic.DisplayValue
import id.walt.walletdemo.compose.logic.QrCodePayload
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import kotlin.math.floor
import kotlin.math.roundToInt

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
        is DisplayValue.QrCode -> QrCodeValue(value, path, modifier)
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
            listOfNotNull(
                "${value.byteCount} bytes",
            ).joinToString(" • "),
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
    CredentialMediaViewer(
        viewerTag = WalletUiTestTags.claimImageViewer(path.id),
        closeTag = WalletUiTestTags.claimImageViewerClose(path.id),
        viewerDescription = "Credential image viewer",
        closeDescription = "Close full-screen credential image",
        onDismiss = onDismiss,
    ) {
        AsyncImage(
            model = value.bytes,
            contentDescription = "Full-screen credential image",
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 64.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun QrCodeValue(
    value: DisplayValue.QrCode,
    path: ClaimItemPath,
    modifier: Modifier = Modifier,
) {
    val qrCode = remember(value) {
        runCatching { encodeQrCode(value.payload) }.getOrNull()
    }
    var viewerOpen by rememberSaveable(path.id) { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (qrCode != null) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .testTag(WalletUiTestTags.claimQrCode(path.id))
                    .clickable(
                        onClickLabel = "View QR code full screen",
                        onClick = { viewerOpen = true },
                    ),
            ) {
                QrCodeCanvas(
                    qrCode = qrCode,
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = "QR code" },
                )
            }
        } else {
            Text(
                "QR code unavailable",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            "QR code",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            when (val payload = value.payload) {
                is QrCodePayload.Text -> "${payload.value.length} characters"
                is QrCodePayload.Binary -> "ICAO Compact VDS, ${payload.bytes.size.toReadableByteCount()}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (viewerOpen && qrCode != null) {
        CredentialMediaViewer(
            viewerTag = WalletUiTestTags.claimQrCodeViewer(path.id),
            closeTag = WalletUiTestTags.claimQrCodeViewerClose(path.id),
            viewerDescription = "QR code viewer",
            closeDescription = "Close full-screen QR code",
            onDismiss = { viewerOpen = false },
        ) {
            QrCodeCanvas(
                qrCode = qrCode,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .semantics { contentDescription = "Full-screen QR code" },
            )
        }
    }
}

@Composable
private fun QrCodeCanvas(qrCode: ImageBitmap, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color.White)) {
        val totalModules = qrCode.width + QrQuietZoneModules * 2
        val moduleSize = floor(minOf(size.width, size.height) / totalModules)
            .roundToInt()
            .coerceAtLeast(1)
        val renderedSize = totalModules * moduleSize
        val qrSize = qrCode.width * moduleSize
        val origin = IntOffset(
            x = ((size.width - renderedSize) / 2f).roundToInt() + QrQuietZoneModules * moduleSize,
            y = ((size.height - renderedSize) / 2f).roundToInt() + QrQuietZoneModules * moduleSize,
        )
        drawImage(
            image = qrCode,
            dstOffset = origin,
            dstSize = IntSize(qrSize, qrSize),
            filterQuality = FilterQuality.None,
        )
    }
}

@Composable
private fun CredentialMediaViewer(
    viewerTag: String,
    closeTag: String,
    viewerDescription: String,
    closeDescription: String,
    onDismiss: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .semantics { contentDescription = viewerDescription }
                .testTag(viewerTag),
        ) {
            content()
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.48f), CircleShape)
                    .testTag(closeTag),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = closeDescription,
                    tint = Color.White,
                )
            }
        }
    }
}

private const val QrQuietZoneModules = 4

private fun Int.toReadableByteCount(): String = if (this == 1) "1 byte" else "$this bytes"
