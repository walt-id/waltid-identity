package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val frontId = expandedId ?: details.last().summary.id
    val density = LocalDensity.current
    var measuredCardHeight by remember { mutableStateOf(0.dp) }

    val offsets = cardOffsets(
        count = details.size,
        expandedIndex = details.indexOfFirst { it.summary.id == expandedId }.takeIf { it >= 0 },
        peek = CredentialCardPeek,
        cardHeight = measuredCardHeight,
    )
    val stackHeight = if (measuredCardHeight > 0.dp) offsets.last() + measuredCardHeight else Dp.Unspecified

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (stackHeight != Dp.Unspecified) Modifier.height(stackHeight) else Modifier),
    ) {
        details.forEachIndexed { index, item ->
            val id = item.summary.id
            val isFront = id == frontId
            Box(
                modifier = Modifier
                    .offset(y = offsets.getOrElse(index) { 0.dp })
                    .zIndex(if (isFront) details.size.toFloat() else index.toFloat())
                    .onSizeChanged { size ->
                        if (index == 0) {
                            measuredCardHeight = with(density) { size.height.toDp() }
                        }
                    },
            ) {
                CredentialCard(
                    details = item,
                    onClick = {
                        if (isFront) {
                            onOpenDetails(id)
                        } else {
                            expandedId = id
                        }
                    },
                    onLongClick = { onOpenDetails(id) },
                )
            }
        }
    }
}

internal fun cardOffsets(
    count: Int,
    expandedIndex: Int?,
    peek: Dp,
    cardHeight: Dp,
): List<Dp> {
    var y = 0.dp
    return List(count) { index ->
        val offset = y
        val isExpanded = expandedIndex == index
        val isLast = index == count - 1
        y += if (cardHeight <= 0.dp) {
            0.dp
        } else if (isExpanded || (expandedIndex == null && isLast)) {
            cardHeight
        } else {
            peek
        }
        offset
    }
}
