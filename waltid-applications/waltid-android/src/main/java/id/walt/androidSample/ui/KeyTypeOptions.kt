package id.walt.androidSample.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun <T> KeyTypeOptions(
    options: List<T>,
    selectedOption: T,
    modifier: Modifier = Modifier,
    onOptionSelected: (T) -> Unit,
) {
    Column(
        modifier = modifier,
    ) {
        options.forEach { text ->
            Row(
                modifier = Modifier
                    .wrapContentWidth(Alignment.Start)
                    .selectable(
                        selected = (text == selectedOption),
                        onClick = { onOptionSelected(text) }
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = (text == selectedOption),
                    onClick = { onOptionSelected(text) }
                )
                Text(
                    text = text.toString(),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}