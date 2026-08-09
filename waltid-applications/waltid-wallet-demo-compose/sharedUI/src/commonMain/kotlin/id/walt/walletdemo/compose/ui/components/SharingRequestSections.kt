package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoReaderTrust
import id.walt.walletdemo.compose.logic.WalletDemoSharingDetail
import id.walt.walletdemo.compose.logic.WalletDemoSharingEncryptionMechanism
import id.walt.walletdemo.compose.logic.WalletDemoSharingRequest
import id.walt.walletdemo.compose.logic.WalletDemoSharingRequester
import id.walt.walletdemo.compose.logic.WalletDemoSharingResponseProtection
import id.walt.walletdemo.compose.ui.WalletUiTestTags

/**
 * Renders the request concepts of a sharing review: requester, transaction authorization, reader
 * trust, response protection and technical details.
 *
 * Only the concepts [request] actually carries are rendered. A transport that has no reader
 * authentication or no requester metadata gets no such section rather than a section saying the
 * request is anonymous or unauthenticated - that distinction is what makes an absent section
 * readable as "the protocol has no such notion" instead of "the answer was bad".
 */
@Composable
internal fun SharingRequestSections(request: WalletDemoSharingRequest, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        request.requester?.let { RequesterSection(it) }

        request.transactionData.forEach { group ->
            ClaimGroupSection(group)
        }

        request.readerTrust?.let { ReaderTrustSection(it) }

        ResponseProtectionSection(request.responseProtection)

        if (request.technicalDetails.any { !it.value.isNullOrBlank() }) {
            TechnicalDetailsSection(request.technicalDetails)
        }
    }
}

@Composable
private fun RequesterSection(requester: WalletDemoSharingRequester) {
    val displayName = requester.display?.name?.trim()?.takeIf { it.isNotEmpty() }
    val fallbackName = requester.fallbackName?.trim()?.takeIf { it.isNotEmpty() }
    val identityName = displayName ?: fallbackName
    val details = buildList {
        // A verified origin leads the details because it is the only requester claim that was
        // authenticated rather than self-asserted. It is dropped when it is already the identity being
        // shown - a request with no verifier metadata is headed by its origin, and repeating it as a
        // labelled row would read as two independent facts about the requester.
        add(WalletDemoSharingDetail("Verified website", requester.verifiedOrigin?.takeIf { it != identityName }))
        addAll(requester.details)
    }.filter { !it.value.isNullOrBlank() }

    if (identityName == null && details.isEmpty()) return

    ReviewMetadataSection(
        title = "Requester",
        modifier = Modifier.testTag(WalletUiTestTags.PresentationVerifierSection),
    ) {
        if (identityName != null) {
            MetadataIdentityRow(
                display = requester.display,
                fallbackName = identityName,
                supportingText = null,
            )
            if (details.isNotEmpty()) MetadataRowDivider()
        }
        MetadataDetailList(details.map { MetadataDetailItem(it.label, it.value, it.linkUri) })
    }
}

@Composable
private fun ReaderTrustSection(readerTrust: WalletDemoReaderTrust) {
    ReviewMetadataSection(
        title = "Reader authentication",
        modifier = Modifier.testTag(WalletUiTestTags.PresentationReaderTrustSection),
    ) {
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
                // Deliberately not phrased as a signature failure: a failed signature never reaches
                // review at all, so saying so here would misdescribe a verified but unrecognised reader.
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

        Text(headline, style = MaterialTheme.typography.bodyMedium)
        Text(
            explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        identity?.let {
            MetadataRowDivider()
            MetadataDetailList(listOf(MetadataDetailItem(it.label, it.value)))
        }
    }
}

@Composable
private fun ResponseProtectionSection(protection: WalletDemoSharingResponseProtection) {
    ReviewMetadataSection(
        title = "Response protection",
        modifier = Modifier.testTag(WalletUiTestTags.PresentationResponseProtectionSection),
    ) {
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

@Composable
private fun TechnicalDetailsSection(details: List<WalletDemoSharingDetail>) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ReviewMetadataSection(
        title = "Technical request details",
        modifier = Modifier.testTag(WalletUiTestTags.PresentationTechnicalDetailsSection),
        contentPadding = if (expanded) {
            PaddingValues(16.dp)
        } else {
            PaddingValues(horizontal = 16.dp, vertical = 2.dp)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable { expanded = !expanded }
                .testTag(WalletUiTestTags.VerifierTechnicalDetailsToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (expanded) "Hide details" else "Show details")
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
            )
        }

        if (expanded) {
            MetadataRowDivider()
            MetadataDetailList(
                details.map { MetadataDetailItem(it.label, it.value, it.linkUri) },
                modifier = Modifier.testTag(WalletUiTestTags.VerifierTechnicalDetails),
            )
        }
    }
}

private val WalletDemoSharingEncryptionMechanism.displayName: String
    get() = when (this) {
        WalletDemoSharingEncryptionMechanism.Jwe -> "JWE encrypted response"
        WalletDemoSharingEncryptionMechanism.DcApiJwt -> "OpenID4VP dc_api.jwt"
        WalletDemoSharingEncryptionMechanism.AnnexCHpke -> "ISO 18013-7 Annex C HPKE"
    }
