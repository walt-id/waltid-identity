package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** The entire row selects. Navigation belongs to the selected information group's Details action. */
@Composable
internal fun ReviewCredentialChoice(
    selected: Boolean,
    multiple: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    hint: String? = null,
    onSelect: () -> Unit,
    content: @Composable () -> Unit,
) {
    val selectionModifier = when {
        readOnly -> Modifier
        multiple -> Modifier.toggleable(selected, enabled = enabled, role = Role.Checkbox) { onSelect() }
        else -> Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
    }
    Row(modifier.fillMaxWidth().then(selectionModifier).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!readOnly) {
            if (multiple) Checkbox(selected, onCheckedChange = null, enabled = enabled)
            else RadioButton(selected, onClick = null, enabled = enabled)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
