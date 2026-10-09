package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared information chrome for online and nearby consent; transport-specific rows stay in slots. */
@Composable
internal fun ReviewInformationGroup(
    title: String,
    issuer: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            issuer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        content()
    }
}
