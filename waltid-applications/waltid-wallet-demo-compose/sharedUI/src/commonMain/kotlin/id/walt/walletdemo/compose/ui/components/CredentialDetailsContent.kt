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
import id.walt.walletdemo.compose.logic.WalletDemoReviewIslandKind
import id.walt.walletdemo.compose.logic.toStoredReviewIslands
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
        CredentialInformationContent(details)
    }
}

@Composable
internal fun StoredCredentialDetailsContent(
    details: CredentialDetails,
    modifier: Modifier = Modifier,
) {
    ReviewIslandNavigationHost(
        reviewKey = details.summary.id,
        islands = details.toStoredReviewIslands(),
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.credentialDetails(details.summary.id)),
        scrollContent = true,
        showModelExpandedValues = { island -> island.kind != WalletDemoReviewIslandKind.Information },
    ) { island ->
        if (island.kind == WalletDemoReviewIslandKind.Information) {
            CredentialInformationContent(details)
        }
    }
}

@Composable
private fun CredentialInformationContent(details: CredentialDetails) {
    val systemInfoGroup = details.toSystemInfoGroup()
    if (details.groups.isEmpty() && systemInfoGroup == null) {
        Text("No credential details available")
    }
    details.groups.forEach { group -> ClaimGroupSection(group) }
    systemInfoGroup?.let { ClaimGroupSection(it) }
}
