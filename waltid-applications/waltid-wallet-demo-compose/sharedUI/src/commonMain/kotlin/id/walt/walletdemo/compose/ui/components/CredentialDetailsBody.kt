package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.toSystemInfoGroup
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CredentialDetailsBody(
    details: CredentialDetails,
    onTechnicalDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val technicalGroups = details.groups.filter { it.id == "technical" } + listOfNotNull(details.toSystemInfoGroup())
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.credentialDetails(details.summary.id)),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CredentialOverviewSection(details)
        if (details.groups.isEmpty() && technicalGroups.isEmpty()) {
            Text("No credential details available")
        }
        details.groups.filter { it.id != "technical" && it.id != "requested" }.forEach { group ->
            key(group.id) { ClaimGroupSection(group) }
        }
        if (technicalGroups.isNotEmpty()) WalletSection {
            WalletNavigationRow(
                stringResource(Res.string.credential_technical_details), onClick = onTechnicalDetails,
                modifier = Modifier.testTag("credential-technical-details"),
                icon = { WalletIcon(WalletSymbol.Info, null) },
            )
        }
    }
}
