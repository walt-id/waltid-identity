package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoPresentationError
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun PresentationErrorSection(
    error: WalletDemoPresentationError,
    enabled: Boolean,
    onNotifyVerifier: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.PresentationError),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "This request cannot be completed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            VerifierReviewSections(error)
            Text(error.message, style = MaterialTheme.typography.bodyMedium)
            Text(
                "OpenID4VP error: ${error.errorCode}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "You can notify the verifier or dismiss the request without sending a response.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onNotifyVerifier,
                    enabled = enabled,
                    modifier = Modifier.testTag(WalletUiTestTags.PresentationErrorNotifyButton),
                ) {
                    Text("Notify verifier")
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = enabled,
                    modifier = Modifier.testTag(WalletUiTestTags.PresentationErrorDismissButton),
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}
