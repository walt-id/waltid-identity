package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.WalletDemoIssuerMetadata
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun IssuanceIssuerSection(issuer: WalletDemoIssuerMetadata) {
    var issuerExpanded by rememberSaveable(issuer.credentialIssuer) { mutableStateOf(false) }
    val issuerName = issuer.display?.name?.trim()?.takeIf { it.isNotEmpty() }
    val issuerIdentifier = issuer.credentialIssuer.trim()
    val issuerDetails = listOf(
        MetadataDetailItem(
            label = "Credential Issuer",
            value = issuerIdentifier.takeIf { issuerName != null && it != issuerName },
            linkUri = issuerIdentifier,
        ),
    ).filter { !it.value.isNullOrBlank() }
    ExpandableMetadataCard(
        title = "Issuer",
        expanded = issuerExpanded,
        onToggle = { issuerExpanded = !issuerExpanded },
        modifier = Modifier.testTag(WalletUiTestTags.OfferIssuerSection),
        toggleTestTag = WalletUiTestTags.OfferIssuerDetailsToggle,
        summary = {
            MetadataIdentityRow(
                display = issuer.display,
                fallbackName = issuerIdentifier,
            )
        },
        details = {
            if (issuerDetails.isNotEmpty()) {
                MetadataDetailList(
                    issuerDetails,
                    modifier = Modifier.testTag(WalletUiTestTags.OfferIssuerDetails),
                )
            }
        },
    )
}
