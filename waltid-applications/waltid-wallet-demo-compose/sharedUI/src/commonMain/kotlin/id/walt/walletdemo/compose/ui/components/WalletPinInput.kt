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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** One password input owns typing, paste and deletion; the positions are decorative. */
@Composable
internal fun WalletPinInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    progressDescription: String,
    digitCount: Int,
    enabled: Boolean,
    isError: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = TextFieldValue(value, selection = TextRange(value.length)),
        onValueChange = { input ->
            val digits = input.text.filter { it in '0'..'9' }.take(digitCount)
            if (enabled && digits != value) onValueChange(digits)
        },
        // Retain the IME session while a brief PIN check/save rejects edits.
        enabled = true,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        cursorBrush = SolidColor(Color.Transparent),
        modifier = modifier.fillMaxWidth().height(64.dp).onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label; stateDescription = progressDescription; if (!enabled) disabled() },
        decorationBox = { innerTextField ->
            Box {
                Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                    val gap = 16.dp.toPx()
                    val width = minOf(40.dp.toPx(), (size.width - gap * (digitCount - 1)) / digitCount)
                    val start = (size.width - (width * digitCount + gap * (digitCount - 1))) / 2
                    repeat(digitCount) { index ->
                        val x = start + index * (width + gap)
                        val active = focused && enabled && index == value.length
                        val tint = when {
                            isError -> colors.error
                            active -> colors.primary
                            else -> colors.outlineVariant
                        }
                        val baseline = size.height / 2 + 14.dp.toPx()
                        drawLine(tint, Offset(x, baseline), Offset(x + width, baseline),
                            strokeWidth = if (active || isError) 3.dp.toPx() else 2.dp.toPx(), cap = StrokeCap.Round)
                        if (index < value.length) drawCircle(colors.onSurface, 5.dp.toPx(),
                            Offset(x + width / 2, size.height / 2 - 3.dp.toPx()))
                    }
                }
                // Keep the actual secure editor in the layout for accessibility, focus and the IME.
                Box(Modifier.matchParentSize().alpha(0f)) { innerTextField() }
            }
        },
    )
}
