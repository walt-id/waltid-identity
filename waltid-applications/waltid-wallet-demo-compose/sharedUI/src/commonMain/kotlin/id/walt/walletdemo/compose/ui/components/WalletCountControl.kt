package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.action_remove
import org.jetbrains.compose.resources.painterResource

/** Compact visible chrome, with two separate 48 dp touch targets. */
@Composable
internal fun WalletCountControl(
    value: Int,
    range: IntRange,
    enabled: Boolean,
    decreaseLabel: String,
    increaseLabel: String,
    decreaseTag: String,
    increaseTag: String,
    onChange: (Int) -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        Surface(Modifier.size(width = 80.dp, height = 28.dp), shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh) {}
        VerticalDivider(Modifier.height(14.dp))
        Row {
            IconButton(onClick = { onChange(value - 1) }, enabled = enabled && value > range.first,
                modifier = Modifier.size(48.dp).testTag(decreaseTag)) {
                Icon(painterResource(Res.drawable.action_remove), decreaseLabel, Modifier.size(18.dp))
            }
            IconButton(onClick = { onChange(value + 1) }, enabled = enabled && value < range.last,
                modifier = Modifier.size(48.dp).testTag(increaseTag)) {
                Icon(Icons.Filled.Add, increaseLabel, Modifier.size(18.dp))
            }
        }
    }
}
