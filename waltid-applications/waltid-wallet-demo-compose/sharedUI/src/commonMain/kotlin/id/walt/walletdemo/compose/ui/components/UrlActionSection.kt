package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun UrlActionSection(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    buttonText: String,
    enabled: Boolean,
    inputTestTag: String,
    buttonTestTag: String,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(inputTestTag),
            minLines = 3,
            maxLines = 3,
        )
        Button(
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier.testTag(buttonTestTag),
        ) {
            Text(buttonText)
        }
    }
}
