package id.walt.walletdemo.compose.android

import id.walt.wallet2.mobile.MobileWalletDigitalCredentialPreview
import id.walt.wallet2.mobile.MobileWalletDigitalCredentialRequestInfo
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialOption
import id.walt.wallet2.mobile.MobileWalletPresentationDisclosure
import id.walt.wallet2.mobile.MobileWalletReaderTrust
import id.walt.wallet2.mobile.MobileWalletTransactionDataItem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DigitalCredentialConsentTextTest {
    /**
     * The presentation signs over transaction_data, so a dialog that omits it asks the user to
     * authorize a payment they were never shown. This is the whole reason the consent text is a pure
     * function rather than inline dialog code.
     */
    @Test
    fun `consent text shows every transaction the presentation will authorize`() {
        val message = digitalCredentialConsentMessage(
            preview = preview(
                transactionData = listOf(
                    transactionDataItem(
                        displayName = "Payment Authorization",
                        detailsJson = """{"amount":"42.00","currency":"EUR","payee":"ACME Corp"}""",
                    ),
                ),
            ),
            selectedOptions = listOf(credentialOption()),
        )

        assertTrue(message.contains("Authorizing Payment Authorization"), message)
        listOf("amount: 42.00", "currency: EUR", "payee: ACME Corp").forEach { line ->
            assertTrue(message.contains(line), "consent text is missing '$line':\n$message")
        }
    }

    /** Multiple items must each appear: showing only the first would authorize the rest unseen. */
    @Test
    fun `consent text shows each of several transaction data items`() {
        val message = digitalCredentialConsentMessage(
            preview = preview(
                transactionData = listOf(
                    transactionDataItem("Payment Authorization", """{"amount":"42.00"}"""),
                    transactionDataItem("Document Signing", """{"documentDigest":"abc123"}"""),
                ),
            ),
            selectedOptions = listOf(credentialOption()),
        )

        assertTrue(message.contains("Authorizing Payment Authorization"), message)
        assertTrue(message.contains("Authorizing Document Signing"), message)
        assertTrue(message.contains("documentDigest: abc123"), message)
    }

    /** An undecodable item is still shown raw: silently dropping it would hide the authorization. */
    @Test
    fun `consent text falls back to the raw details when they are not a json object`() {
        val message = digitalCredentialConsentMessage(
            preview = preview(
                transactionData = listOf(transactionDataItem("Odd Profile", "not-json")),
            ),
            selectedOptions = listOf(credentialOption()),
        )

        assertTrue(message.contains("Authorizing Odd Profile"), message)
        assertTrue(message.contains("not-json"), message)
    }

    @Test
    fun `consent text names the requester the protocol and the disclosed claims`() {
        val message = digitalCredentialConsentMessage(
            preview = preview(),
            selectedOptions = listOf(credentialOption()),
        )

        assertTrue(message.contains("Requester: https://verifier.example"), message)
        assertTrue(message.contains("Protocol: openid4vp-v1-unsigned"), message)
        assertTrue(message.contains("given_name: Ada"), message)
        // No transaction data was requested, so no authorization section may be invented.
        assertFalse(message.contains("Authorizing"), message)
    }

    private fun preview(
        transactionData: List<MobileWalletTransactionDataItem> = emptyList(),
    ): MobileWalletDigitalCredentialPreview = MobileWalletDigitalCredentialPreview(
        requestId = "request-1",
        protocol = "openid4vp-v1-unsigned",
        verifiedOrigin = "https://verifier.example",
        request = MobileWalletDigitalCredentialRequestInfo(
            clientId = null,
            verifierMetadata = null,
            nonce = "nonce-123",
            responseMode = "dc_api",
            transactionData = transactionData,
        ),
        credentialOptions = listOf(credentialOption()),
        credentialRequirements = emptyList(),
        readerTrust = MobileWalletReaderTrust.NotApplicable,
    )

    private fun credentialOption(): MobileWalletPresentationCredentialOption =
        MobileWalletPresentationCredentialOption(
            queryId = "pid",
            credentialId = "credential-1",
            format = "mso_mdoc",
            issuer = "Test Issuer",
            subject = null,
            label = "Driving licence",
            credentialDataJson = "{}",
            disclosures = listOf(
                MobileWalletPresentationDisclosure(
                    path = "org.iso.18013.5.1/given_name",
                    name = "given_name",
                    valueJson = "\"Ada\"",
                    displayValue = "Ada",
                    selectivelyDisclosable = false,
                ),
            ),
        )

    private fun transactionDataItem(
        displayName: String,
        detailsJson: String,
    ): MobileWalletTransactionDataItem = MobileWalletTransactionDataItem(
        type = "org.waltid.transaction-data.${displayName.lowercase().replace(' ', '-')}",
        displayName = displayName,
        credentialQueryIds = listOf("pid"),
        supportedFields = emptyList(),
        rawJson = detailsJson,
        detailsJson = detailsJson,
    )
}
