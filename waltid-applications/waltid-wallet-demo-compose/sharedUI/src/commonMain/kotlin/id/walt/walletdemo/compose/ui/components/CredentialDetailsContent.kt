package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.WalletDemoReviewIsland
import id.walt.walletdemo.compose.logic.WalletDemoReviewRoute
import id.walt.walletdemo.compose.logic.toCardDisplayData
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
    technicalBackSignal: Int = 0,
    onRouteChanged: (WalletDemoReviewRoute, WalletDemoReviewIsland?) -> Unit = { _, _ -> },
) {
    ReviewIslandNavigationHost(
        reviewKey = details.summary.id,
        islands = details.toStoredReviewIslands(),
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.credentialDetails(details.summary.id)),
        scrollContent = true,
        technicalBackSignal = technicalBackSignal,
        onRouteChanged = onRouteChanged,
        showTechnicalHeader = false,
        showModelExpandedValues = { island -> island.id.value != "credential" },
        hasCustomExpandedContent = { island ->
            island.id.value == "credential" &&
                (details.toCardDisplayData().holderName != null || details.groups.any { it.items.isNotEmpty() })
        },
        islandExpandedContent = { island ->
            if (island.id.value == "credential") StoredCredentialClaims(details)
        },
    )
}

@Composable
private fun StoredCredentialClaims(details: CredentialDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        details.toCardDisplayData().holderName?.let { holder ->
            MetadataDetailList(listOf(MetadataDetailItem("Holder", holder)))
        }
        details.groups.filter { it.items.isNotEmpty() }.forEach { group ->
            Text(
                group.title,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            )
            group.items.forEachIndexed { index, item ->
                if (index > 0) MetadataRowDivider()
                ClaimValueRow(item)
            }
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
