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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import id.walt.walletdemo.compose.ui.resources.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            style = MaterialTheme.typography.bodyLarge,
        )
        is DisplayValue.DecodedText -> Text(
            value.value.ifEmpty { stringResource(Res.string.claim_empty_text) },
            modifier = modifier,
            style = MaterialTheme.typography.bodyLarge,
        )
        is DisplayValue.DeferredImage -> DeferredImageValue(value, path, modifier)
        is DisplayValue.Image -> ImageValue(value, path, modifier)
        is DisplayValue.ListValue -> ClaimListValue(value, path, modifier)
        DisplayValue.NullValue -> Text(
            stringResource(Res.string.claim_null_value),
            modifier = modifier,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is DisplayValue.NumberValue -> Text(
            value.value.ifEmpty { stringResource(Res.string.claim_empty_text) },
            modifier = modifier,
            style = MaterialTheme.typography.bodyLarge,
        )
        is DisplayValue.ObjectValue -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (value.entries.isEmpty()) Text(stringResource(Res.string.claim_empty_object))
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
            value.value.ifEmpty { stringResource(Res.string.claim_empty_text) },
            modifier = modifier,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

// Bound concurrent validation work, including ImageIO on Compose iOS.
private val imageDecodeDispatcher = Dispatchers.Default.limitedParallelism(2)

@Composable
private fun DeferredImageValue(source: DisplayValue.DeferredImage, path: ClaimItemPath, modifier: Modifier) {
    var visible by remember(source) { mutableStateOf(false) }
    var resolved by remember(source) { mutableStateOf<DisplayValue?>(null) }
    Box(modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        visible = bounds.width > 0 && bounds.height > 0
    }) {
        val value = resolved
        if (value == null) {
            ClaimImagePlaceholder(loading = true)
        } else {
            ClaimValue(value, path)
        }
    }
    LaunchedEffect(source, visible) {
        if (visible && resolved == null) {
            resolved = withContext(imageDecodeDispatcher) { source.resolve() }
        }
    }
}

private const val MaxListPreviewItems = 25

@Composable
private fun ClaimListValue(value: DisplayValue.ListValue, path: ClaimItemPath, modifier: Modifier) {
    var visibleCount by remember(value, path) { mutableStateOf(MaxListPreviewItems) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (value.values.isEmpty()) Text(stringResource(Res.string.claim_empty_list))
        value.values.take(visibleCount).forEachIndexed { index, child ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${index + 1}.", style = MaterialTheme.typography.bodyLarge)
                ClaimValue(child, path.indexedChild(index), Modifier.weight(1f))
            }
        }
        if (value.values.size > visibleCount) {
            Text(stringResource(Res.string.claim_list_preview, visibleCount, value.values.size),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { visibleCount = (visibleCount + MaxListPreviewItems).coerceAtMost(value.values.size) }) {
                Text(stringResource(Res.string.claim_list_more, (value.values.size - visibleCount).coerceAtMost(MaxListPreviewItems)))
            }
        }
    }
}

private enum class ClaimImageState { Loading, Ready, Failed }

@Composable
private fun ClaimImagePlaceholder(loading: Boolean) {
    Column(Modifier.size(112.dp).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (loading) CircularProgressIndicator(Modifier.size(24.dp)) else WalletIcon(WalletSymbol.Info, contentDescription = null)
        Text(stringResource(if (loading) Res.string.claim_image_loading else Res.string.claim_image_failed),
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ImageValue(value: DisplayValue.Image, path: ClaimItemPath, modifier: Modifier = Modifier) {
    var viewerOpen by rememberSaveable(path.id) { mutableStateOf(false) }
    var imageState by remember(value) { mutableStateOf(ClaimImageState.Loading) }

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
                    enabled = imageState == ClaimImageState.Ready,
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
                onLoading = { imageState = ClaimImageState.Loading },
                onSuccess = { imageState = ClaimImageState.Ready },
                onError = { imageState = ClaimImageState.Failed },
            )
            if (imageState != ClaimImageState.Ready) ClaimImagePlaceholder(imageState == ClaimImageState.Loading)
        }

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
            Text("${value.mimeType} · ${value.byteCount} bytes",
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                color = Color.White, style = MaterialTheme.typography.bodySmall)
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
