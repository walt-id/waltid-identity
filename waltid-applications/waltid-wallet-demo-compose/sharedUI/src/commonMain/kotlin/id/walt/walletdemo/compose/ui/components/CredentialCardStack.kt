package id.walt.walletdemo.compose.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import id.walt.walletdemo.compose.logic.CredentialDetails

@Composable
internal fun CredentialCardStack(
    details: List<CredentialDetails>,
    onOpenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
    expandedId: String? = null,
) {
    if (details.isEmpty()) return
    val othersVisibility = remember { Animatable(1f) }
    val selectedProgress = remember { Animatable(0f) }

    LaunchedEffect(expandedId) {
        if (expandedId != null) {
            othersVisibility.animateTo(0f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
            selectedProgress.animateTo(1f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
        } else {
            selectedProgress.animateTo(0f, tween(durationMillis = 280, easing = FastOutSlowInEasing))
            othersVisibility.animateTo(1f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cardHeight = maxWidth / Id1AspectRatio
        val offsets = cardOffsets(
            count = details.size,
            peek = CredentialCardPeek,
            cardHeight = cardHeight,
        )
        val restHeight = offsets.last() + cardHeight
        val stackHeight = restHeight + ((cardHeight - restHeight) * selectedProgress.value)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stackHeight)
                .clipToBounds(),
        ) {
            details.forEachIndexed { index, item ->
                val id = item.summary.id
                val isSelected = id == expandedId
                val restOffset = offsets.getOrElse(index) { 0.dp }
                val y = if (isSelected) restOffset * (1f - selectedProgress.value) else restOffset
                Box(
                    modifier = Modifier
                        .offset(y = y)
                        .zIndex(if (isSelected) details.size.toFloat() else index.toFloat())
                        .alpha(if (isSelected) 1f else othersVisibility.value),
                ) {
                    CredentialCard(
                        details = item,
                        onClick = { onOpenDetails(id) },
                    )
                }
            }
        }
    }
}

internal fun cardOffsets(
    count: Int,
    peek: Dp,
    cardHeight: Dp,
): List<Dp> {
    var y = 0.dp
    return List(count) { index ->
        val offset = y
        val isLast = index == count - 1
        y += if (cardHeight <= 0.dp) {
            0.dp
        } else if (isLast) {
            cardHeight
        } else {
            peek
        }
        offset
    }
}
