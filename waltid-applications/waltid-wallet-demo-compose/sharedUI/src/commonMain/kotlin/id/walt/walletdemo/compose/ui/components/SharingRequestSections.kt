package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoReaderTrust
import id.walt.walletdemo.compose.logic.WalletDemoSharingDetail
import id.walt.walletdemo.compose.logic.WalletDemoSharingEncryptionMechanism
import id.walt.walletdemo.compose.logic.WalletDemoSharingRequest
import id.walt.walletdemo.compose.logic.WalletDemoSharingRequester
import id.walt.walletdemo.compose.logic.WalletDemoSharingResponseProtection
import id.walt.walletdemo.compose.ui.WalletUiTestTags

/**
 * Renders requester identity plus the technical request facts revealed by tapping that box.
 *
 * Only the concepts [request] actually carries are rendered. A transport that has no reader
 * authentication or no requester metadata gets no such section rather than a section saying the
 * request is anonymous or unauthenticated.
 */
@Composable
internal fun SharingRequestSections(
    request: WalletDemoSharingRequest,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        VerifierMetadataCard(request)

        request.transactionData.forEach { group ->
            ClaimGroupSection(group, collapsible = false)
        }
    }
}

@Composable
private fun VerifierMetadataCard(request: WalletDemoSharingRequest) {
    val requester = request.requester
    val displayName = requester?.display?.name?.trim()?.takeIf { it.isNotEmpty() }
    val fallbackName = requester?.fallbackName?.trim()?.takeIf { it.isNotEmpty() }
    val identityName = displayName ?: fallbackName
    val verifiedOrigin = requester?.verifiedOrigin?.trim()?.takeIf { it.isNotEmpty() }
    val originIsIdentity = verifiedOrigin != null && verifiedOrigin == identityName
    val verifiedOriginDetail = verifiedOrigin
        ?.takeIf { !originIsIdentity }
        ?.let { WalletDemoSharingDetail(VERIFIED_ORIGIN_LABEL, it) }
    val requesterDetails = requester?.details.orEmpty().filter { !it.value.isNullOrBlank() }
    val technicalDetails = request.technicalDetails.filter { !it.value.isNullOrBlank() }
    val hasDetails = verifiedOriginDetail != null ||
        requesterDetails.isNotEmpty() ||
        request.readerTrust != null ||
        request.responseProtection != WalletDemoSharingResponseProtection.None ||
        technicalDetails.isNotEmpty()
    if (identityName == null && verifiedOrigin == null && !hasDetails) return

    var expanded by rememberSaveable { mutableStateOf(false) }

    ExpandableMetadataCard(
        title = "Verifier",
        expanded = expanded,
        onToggle = { expanded = !expanded },
        modifier = Modifier.testTag(WalletUiTestTags.PresentationVerifierSection),
        toggleTestTag = WalletUiTestTags.PresentationRequesterDetailsToggle,
        summary = {
            if (identityName != null) {
                MetadataIdentityRow(
                    display = requester?.display,
                    fallbackName = identityName,
                    supportingText = VERIFIED_ORIGIN_LABEL.takeIf { originIsIdentity },
                )
            } else {
                Text(
                    text = verifiedOrigin ?: "Verifier",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        details = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag(WalletUiTestTags.PresentationRequesterDetails),
            ) {
                val identityItems = buildList {
                    verifiedOriginDetail?.let { add(MetadataDetailItem(it.label, it.value, it.linkUri)) }
                    requesterDetails.forEach { add(MetadataDetailItem(it.label, it.value, it.linkUri)) }
                }
                if (identityItems.isNotEmpty()) {
                    MetadataDetailList(identityItems)
                }
                request.readerTrust?.let {
                    if (identityItems.isNotEmpty()) MetadataRowDivider()
                    ReaderTrustDetails(it)
                }
                if (identityItems.isNotEmpty() || request.readerTrust != null) MetadataRowDivider()
                ResponseProtectionDetails(request.responseProtection)
                if (technicalDetails.isNotEmpty()) {
                    MetadataRowDivider()
                    Text(
                        "Technical request details",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag(WalletUiTestTags.PresentationTechnicalDetailsSection),
                    )
                    MetadataDetailList(
                        technicalDetails.map { MetadataDetailItem(it.label, it.value, it.linkUri) },
                        modifier = Modifier.testTag(WalletUiTestTags.VerifierTechnicalDetails),
                    )
                }
            }
        },
    )
}

@Composable
private fun ReaderTrustDetails(readerTrust: WalletDemoReaderTrust) {
    val headline: String
    val explanation: String
    val identity: WalletDemoSharingDetail?
    when (readerTrust) {
        WalletDemoReaderTrust.NotAuthenticated -> {
            headline = "Reader not authenticated"
            explanation = "The request carried no reader signature, so this wallet cannot tell you who is asking."
            identity = null
        }
        WalletDemoReaderTrust.PendingVerification -> {
            headline = "Reader authentication will be verified before sharing"
            explanation = "The reader signature is checked when you share, and nothing is sent if it fails."
            identity = null
        }
        is WalletDemoReaderTrust.Untrusted -> {
            headline = "Reader identity not trusted by this wallet"
            explanation = readerTrust.reason
            identity = null
        }
        is WalletDemoReaderTrust.Trusted -> {
            headline = "Trusted reader"
            explanation = "The reader signature was verified and this wallet recognises the reader."
            identity = WalletDemoSharingDetail("Reader identity", readerTrust.readerIdentity)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.testTag(WalletUiTestTags.PresentationReaderTrustSection),
    ) {
        Text(headline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        identity?.let {
            MetadataDetailList(listOf(MetadataDetailItem(it.label, it.value)))
        }
    }
}

@Composable
private fun ResponseProtectionDetails(protection: WalletDemoSharingResponseProtection) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag(WalletUiTestTags.PresentationResponseProtectionSection),
    ) {
        Text(
            "Response protection",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        MetadataDetailList(
            buildList {
                add(
                    MetadataDetailItem(
                        "Message-level encryption",
                        when (protection) {
                            WalletDemoSharingResponseProtection.None -> "Not requested"
                            is WalletDemoSharingResponseProtection.Encrypted -> "Required"
                        },
                    )
                )
                if (protection is WalletDemoSharingResponseProtection.Encrypted) {
                    add(MetadataDetailItem("Encryption mechanism", protection.mechanism.displayName))
                    add(MetadataDetailItem("Key management algorithm", protection.keyManagementAlgorithm))
                    add(MetadataDetailItem("Content encryption algorithm", protection.contentEncryptionAlgorithm))
                    add(MetadataDetailItem("Verifier key ID", protection.verifierKeyId))
                    add(MetadataDetailItem("Verifier key thumbprint", protection.verifierKeyThumbprint))
                }
            }
        )
    }
}

/** Names the one requester claim the transport authenticated, wherever it is shown. */
private const val VERIFIED_ORIGIN_LABEL = "Verified website"

private val WalletDemoSharingEncryptionMechanism.displayName: String
    get() = when (this) {
        WalletDemoSharingEncryptionMechanism.Jwe -> "JWE encrypted response"
        WalletDemoSharingEncryptionMechanism.DcApiJwt -> "OpenID4VP dc_api.jwt"
        WalletDemoSharingEncryptionMechanism.AnnexCHpke -> "ISO 18013-7 Annex C HPKE"
    }
