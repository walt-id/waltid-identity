package id.walt.walletdemo.compose.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences

/** Decorative readiness cue, not progress. Its bounds stay fixed while the radio waits. */
@Composable
internal fun NfcEngagementIllustration(active: Boolean) {
    val pulse = if (active && !LocalWalletVisualPreferences.current.reduceMotion) {
        rememberInfiniteTransition(label = "NFC signal").animateFloat(0f, 1f,
            infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Signal pulse")
    } else null
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(Modifier.fillMaxWidth().clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(216.dp, 176.dp)) {
            val phase = pulse?.value ?: 0.6f
            scale(size.width / 216f, size.height / 176f, pivot = Offset.Zero) {
                drawCircle(accent.copy(alpha = 0.06f), 70f, Offset(112f, 88f))
                drawRoundRect(surface, Offset(140f, 36f), Size(54f, 86f), CornerRadius(12f))
                drawRoundRect(outline.copy(alpha = 0.35f), Offset(140f, 36f), Size(54f, 86f), CornerRadius(12f), style = Stroke(2f))
                drawRoundRect(outline.copy(alpha = 0.3f), Offset(152f, 51f), Size(30f, 19f), CornerRadius(4f))
                drawCircle(outline.copy(alpha = 0.3f), 3f, Offset(167f, 104f))
                drawRoundRect(surface, Offset(40f, 43f), Size(62f, 108f), CornerRadius(13f))
                drawRoundRect(accent, Offset(40f, 43f), Size(62f, 108f), CornerRadius(13f), style = Stroke(2.5f))
                drawLine(accent, Offset(63f, 51f), Offset(79f, 51f), strokeWidth = 3f, cap = StrokeCap.Round)
                drawRoundRect(accent.copy(alpha = 0.15f), Offset(51f, 78f), Size(40f, 29f), CornerRadius(6f))
                drawLine(accent.copy(alpha = 0.6f), Offset(58f, 87f), Offset(73f, 87f), strokeWidth = 3f, cap = StrokeCap.Round)
                drawLine(accent, Offset(62f, 142f), Offset(80f, 142f), strokeWidth = 2.5f, cap = StrokeCap.Round)
                listOf(15f, 25f, 35f).forEach { radius ->
                    val r = radius + phase * 2f
                    drawArc(accent.copy(alpha = 0.35f + phase * 0.65f), -40f, 80f, false,
                        Offset(103f - r, 80f - r), Size(r * 2, r * 2), style = Stroke(2.5f, cap = StrokeCap.Round))
                }
            }
        }
    }
}
