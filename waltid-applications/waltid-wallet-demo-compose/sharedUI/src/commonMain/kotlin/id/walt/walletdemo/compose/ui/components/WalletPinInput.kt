package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** One password input owns typing, paste and deletion; the circles are decorative. */
@Composable
internal fun WalletPinInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    progressDescription: String,
    maxLength: Int,
    digitCount: Int,
    allowUnicodeDigits: Boolean,
    enabled: Boolean,
    isError: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = { input ->
            val digits = input.filter { it in '0'..'9' || (allowUnicodeDigits && it.isDigit()) }.take(maxLength)
            if (digits != value) onValueChange(digits)
        },
        enabled = enabled,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        cursorBrush = SolidColor(Color.Transparent),
        modifier = modifier.fillMaxWidth().height(64.dp).onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label; stateDescription = progressDescription },
        decorationBox = { innerTextField ->
            Box {
                Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                    val gap = 8.dp.toPx()
                    val diameter = minOf(44.dp.toPx(), (size.width - gap * (digitCount - 1)) / digitCount)
                    val start = (size.width - (diameter * digitCount + gap * (digitCount - 1))) / 2
                    repeat(digitCount) { index ->
                        val center = Offset(start + diameter / 2 + index * (diameter + gap), size.height / 2)
                        val active = focused && enabled && index == value.length
                        val tint = if (isError) colors.error else colors.primary
                        drawCircle(if (active) colors.primaryContainer else colors.surfaceContainerHighest,
                            radius = diameter / 2, center = center)
                        if (active || isError) drawCircle(tint, radius = diameter / 2 - 1.dp.toPx(),
                            center = center, style = Stroke(2.dp.toPx()))
                        if (index < value.length) drawCircle(colors.onSurface, 6.dp.toPx(), center)
                        else drawCircle(colors.onSurfaceVariant, 4.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
                    }
                }
                // Keep the actual secure editor in the layout for accessibility, focus and the IME.
                Box(Modifier.matchParentSize().alpha(0f)) { innerTextField() }
            }
        },
    )
}
