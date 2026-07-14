package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.toSystemInfoGroup
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun CredentialDetailsContent(
    details: CredentialDetails,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.credentialDetails(details.summary.id)),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CredentialOverviewSection(details)
        val systemInfoGroup = details.toSystemInfoGroup()
        if (details.groups.isEmpty() && systemInfoGroup == null) {
            Text(
                "No credential details available",
            )
        }
        details.groups.forEach { group ->
            ClaimGroupSection(group)
        }
        systemInfoGroup?.let { ClaimGroupSection(it) }
    }
}
