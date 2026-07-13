package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun CredentialDetailsContent(
    details: CredentialDetails,
    modifier: Modifier = Modifier,
    includeTechnicalDetails: Boolean = false,
) {
    var showTechnicalDetails by rememberSaveable(details.summary.id) { mutableStateOf(false) }
    val hasTechnicalDetails = includeTechnicalDetails && details.technicalGroups.any { it.items.isNotEmpty() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.credentialDetails(details.summary.id)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CredentialOverviewSection(details)
        if (details.groups.isEmpty() && details.technicalGroups.isEmpty()) {
            Text(
                "No credential details available",
            )
        }
        details.groups.forEach { group ->
            ClaimGroupSection(group)
        }
        if (hasTechnicalDetails) {
            TextButton(onClick = { showTechnicalDetails = !showTechnicalDetails }) {
                Text(if (showTechnicalDetails) "Hide raw credential data" else "Show raw credential data")
            }
        }
        if (hasTechnicalDetails && showTechnicalDetails) {
            details.technicalGroups.forEach { group ->
                ClaimGroupSection(group)
            }
        }
    }
}
