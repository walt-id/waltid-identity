package id.walt.walletdemo.compose.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoMetadataDisplay
import id.walt.walletdemo.compose.logic.WalletDemoOfferedCredentialMetadata
import id.walt.walletdemo.compose.logic.resolvedCardTitle
import kotlin.math.absoluteValue

@Composable
internal fun FlippableOfferCard(
    credential: WalletDemoOfferedCredentialMetadata,
    issuerDisplay: WalletDemoMetadataDisplay?,
    issuerFallback: String,
) {
    var flipped by rememberSaveable(credential.configurationId) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "offerCardFlip",
    )
    val title = credential.resolvedCardTitle()
    val art = (credential.display ?: WalletDemoMetadataDisplay(
        name = title,
        logoUri = null,
        logoAltText = null,
    )).toCardArt(
        id = credential.configurationId,
        fallbackName = title,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(Id1AspectRatio)
            .pointerInput(flipped) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount.absoluteValue > 24f) {
                        flipped = !flipped
                    }
                }
            }
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable { flipped = !flipped },
    ) {
        if (rotation <= 90f) {
            CredentialCardArt(art = art, modifier = Modifier.fillMaxSize())
        } else {
            OfferCardBack(
                issuerDisplay = issuerDisplay,
                issuerFallback = issuerFallback,
                format = credential.format,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f },
            )
        }
    }
}

@Composable
private fun OfferCardBack(
    issuerDisplay: WalletDemoMetadataDisplay?,
    issuerFallback: String,
    format: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Issuer", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        MetadataIdentityRow(
            display = issuerDisplay,
            fallbackName = issuerFallback,
        )
        Text("Format", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(format, style = MaterialTheme.typography.bodyMedium)
    }
}
