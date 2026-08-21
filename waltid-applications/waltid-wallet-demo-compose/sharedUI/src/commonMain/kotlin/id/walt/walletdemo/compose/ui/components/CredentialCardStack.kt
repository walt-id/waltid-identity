package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import id.walt.walletdemo.compose.logic.CredentialDetails

@Composable
internal fun CredentialCardStack(
    details: List<CredentialDetails>,
    onOpenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (details.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cardHeight = maxWidth / Id1AspectRatio
        val offsets = cardOffsets(
            count = details.size,
            peek = CredentialCardPeek,
            cardHeight = cardHeight,
        )
        val stackHeight = offsets.last() + cardHeight

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(stackHeight),
        ) {
            details.forEachIndexed { index, item ->
                val id = item.summary.id
                Box(
                    modifier = Modifier
                        .offset(y = offsets.getOrElse(index) { 0.dp })
                        .zIndex(index.toFloat()),
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
