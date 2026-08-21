package id.walt.walletdemo.compose.ui.components

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
    val logoUri: String? = null,
    val logoAltText: String? = null,
)

internal fun CredentialCardDisplayData.toCardArt(): CredentialCardArtModel =
    CredentialCardArtModel(
        id = id,
        name = title,
        backgroundColor = backgroundColor,
        backgroundImageUri = backgroundImageUri,
        textColor = textColor,
        logoUri = logoUri,
        logoAltText = logoAltText,
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
        logoUri = logoUri,
        logoAltText = logoAltText,
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
    val logoUri = art.logoUri?.takeIf(::isHttpsUrl)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(Id1AspectRatio)
            .shadow(if (compact) 2.dp else 6.dp, shape, clip = false)
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
                    if (aspect in 1.2f..2.0f) {
                        androidx.compose.foundation.Image(
                            painter = state.painter,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                },
            )
        }

        if (logoUri != null) {
            SubcomposeAsyncImage(
                model = logoUri,
                contentDescription = art.logoAltText ?: "${art.name} logo",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(namePadding)
                    .size(logoSize),
                contentScale = ContentScale.Fit,
                error = {
                    DefaultWaltLogo(
                        modifier = Modifier.size(logoSize),
                        color = labelColor,
                    )
                },
                loading = {
                    DefaultWaltLogo(
                        modifier = Modifier.size(logoSize),
                        color = labelColor,
                    )
                },
            )
        } else {
            DefaultWaltLogo(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(namePadding)
                    .size(logoSize),
                color = labelColor,
            )
        }

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

@Composable
private fun DefaultWaltLogo(
    modifier: Modifier,
    color: Color,
) {
    Text(
        text = "walt.id",
        color = color,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = modifier,
        maxLines = 1,
    )
}

internal fun parseCssColor(value: String?): Color? {
    val hex = value?.trim()?.removePrefix("#").orEmpty()
    val normalized = when (hex.length) {
        3 -> hex.map { "$it$it" }.joinToString("")
        6 -> hex
        8 -> hex.takeLast(6)
        else -> return null
    }
    val rgb = normalized.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or rgb)
}

private fun isHttpsUrl(value: String): Boolean =
    value.trim().startsWith("https://", ignoreCase = true)
