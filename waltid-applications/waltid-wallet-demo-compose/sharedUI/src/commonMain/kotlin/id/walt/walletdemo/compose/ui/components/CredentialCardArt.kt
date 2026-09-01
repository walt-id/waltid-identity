package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import id.walt.credentials.display.CssColors
import id.walt.walletdemo.compose.logic.CredentialCardDisplayData
import id.walt.walletdemo.compose.logic.WalletDemoMetadataDisplay
import id.walt.walletdemo.compose.ui.WalletUiTestTags

internal const val Id1AspectRatio = 1.586f
internal val CredentialCardPeek = 56.dp
internal val DefaultWaltCardBlue = Color(0xFF1B4FDB)

internal data class CredentialCardArtModel(
    val id: String,
    val name: String,
    val backgroundColor: String? = null,
    val backgroundImageUri: String? = null,
    val textColor: String? = null,
)

internal fun CredentialCardDisplayData.toCardArt(): CredentialCardArtModel =
    CredentialCardArtModel(
        id = id,
        name = title,
        backgroundColor = backgroundColor,
        backgroundImageUri = backgroundImageUri,
        textColor = textColor,
    )

internal fun WalletDemoMetadataDisplay.toCardArt(
    id: String,
    fallbackName: String,
): CredentialCardArtModel =
    CredentialCardArtModel(
        id = id,
        name = name?.takeIf { it.isNotBlank() } ?: fallbackName,
        backgroundColor = backgroundColor,
        backgroundImageUri = backgroundImageUri,
        textColor = textColor,
    )

@Composable
internal fun CredentialCardArt(
    art: CredentialCardArtModel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(if (compact) 10.dp else 14.dp)
    val constructedColor = parseCssColor(art.backgroundColor) ?: DefaultWaltCardBlue
    val labelColor = parseCssColor(art.textColor) ?: Color.White
    val backgroundImageUri = art.backgroundImageUri?.takeIf(::isHttpsUrl)
    var metadataArtState by remember(backgroundImageUri) {
        mutableStateOf(
            if (backgroundImageUri == null) {
                CredentialCardMetadataArtState.Absent
            } else {
                CredentialCardMetadataArtState.Pending
            },
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(Id1AspectRatio)
            .shadow(if (compact) 10.dp else 16.dp, shape, clip = false)
            .clip(shape)
            .testTag(WalletUiTestTags.credentialCard(art.id)),
    ) {
        val logoSize = if (compact) 22.dp else 36.dp
        val namePadding = if (compact) 10.dp else 16.dp
        val nameSize = if (compact) 13.sp else 18.sp

        Box(modifier = Modifier.fillMaxSize().background(constructedColor))
        if (backgroundImageUri != null) {
            SubcomposeAsyncImage(
                model = backgroundImageUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                success = { state ->
                    val size = state.painter.intrinsicSize
                    val aspect = if (size.height > 0f) size.width / size.height else 0f
                    val accepted = aspect in 1.2f..2.0f
                    SideEffect {
                        metadataArtState = if (accepted) {
                            CredentialCardMetadataArtState.Ready
                        } else {
                            CredentialCardMetadataArtState.Rejected
                        }
                    }
                    if (accepted) {
                        Image(
                            painter = state.painter,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                },
                error = {
                    SideEffect { metadataArtState = CredentialCardMetadataArtState.Rejected }
                },
            )
        }

        if (showConstructedCardArtOverlay(metadataArtState)) {
            DefaultWaltLogo(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(namePadding)
                    .size(logoSize)
                    .testTag(WalletUiTestTags.CredentialCardConstructedArt),
            )
            Text(
                text = art.name,
                color = labelColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = nameSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(namePadding)
                    .fillMaxWidth(0.72f),
            )
        }
    }
}

internal enum class CredentialCardMetadataArtState {
    Absent,
    Pending,
    Ready,
    Rejected,
}

internal fun showConstructedCardArtOverlay(state: CredentialCardMetadataArtState): Boolean =
    when (state) {
        CredentialCardMetadataArtState.Absent,
        CredentialCardMetadataArtState.Rejected,
        -> true
        CredentialCardMetadataArtState.Pending,
        CredentialCardMetadataArtState.Ready,
        -> false
    }

@Composable
internal expect fun DefaultWaltLogo(modifier: Modifier)

internal fun parseCssColor(value: String?): Color? {
    val parsed = CssColors.parse(value) ?: return null
    return Color(
        red = parsed.red,
        green = parsed.green,
        blue = parsed.blue,
        alpha = parsed.alpha,
    )
}

private fun isHttpsUrl(value: String): Boolean =
    value.trim().startsWith("https://", ignoreCase = true)
