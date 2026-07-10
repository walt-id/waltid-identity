package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoPresentationPreview
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.logic.toVerifierDetails
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun PresentationReviewSection(
    preview: WalletDemoPresentationPreview,
    selectedCredentialIds: Set<String>,
    enabled: Boolean,
    readOnly: Boolean = false,
    onToggleCredential: (String) -> Unit,
    onCredentialClick: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.PresentationReview),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        VerifierDetailsCard(preview.toVerifierDetails())

        Text(
            "Shared credentials",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        preview.credentialOptions.forEach { option ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WalletUiTestTags.presentationCredential(option.credentialId)),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!readOnly) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = option.credentialId in selectedCredentialIds,
                            onCheckedChange = { onToggleCredential(option.credentialId) },
                            enabled = enabled,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(option.issuer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            option.subject?.let {
                                Text("Subject: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(option.format, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                CredentialCard(
                    details = option.toCredentialDetails(),
                    modifier = Modifier.padding(start = if (readOnly) 0.dp else 48.dp),
                    onClick = { onCredentialClick(option.credentialId) },
                )
                HorizontalDivider()
            }
        }

        if (!readOnly) {
            ReviewActionsRow(
                enabled = enabled,
                hasSelection = selectedCredentialIds.isNotEmpty(),
                onSubmit = onSubmit,
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun ReviewActionsRow(
    enabled: Boolean,
    hasSelection: Boolean,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.testTag(WalletUiTestTags.PresentationActions),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onSubmit,
            enabled = enabled && hasSelection,
            modifier = Modifier.testTag(WalletUiTestTags.PresentationSubmitButton),
        ) {
            Text("Share")
        }
        TextButton(
            onClick = onCancel,
            enabled = enabled,
            modifier = Modifier.testTag(WalletUiTestTags.PresentationCancelButton),
        ) {
            Text("Cancel review")
        }
    }
}
