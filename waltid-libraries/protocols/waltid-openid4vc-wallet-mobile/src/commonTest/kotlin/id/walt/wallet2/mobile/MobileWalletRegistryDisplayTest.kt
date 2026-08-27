package id.walt.wallet2.mobile

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MobileWalletRegistryDisplayTest {

    @Test
    fun paymentCardUsesHolderNameAndMaskedLastFour() {
        val display = MobileWalletRegistryDisplay.resolve(
            format = MobileWalletDigitalCredentialFormat.MDOC,
            type = PAYMENT_CARD,
            credentialData = buildJsonObject {
                putJsonObject(PAYMENT_CARD) {
                    put("card_holder_name", "Ada Lovelace")
                    put("card_last4", "4242")
                }
            },
            storedLabel = "Ignored stored label",
        )

        assertEquals("Ada Lovelace", display.title)
        assertEquals("********4242", display.subtitle)
    }

    @Test
    fun mobileDrivingLicenceUsesStaticTitleAndDocumentNumber() {
        val display = MobileWalletRegistryDisplay.resolve(
            format = MobileWalletDigitalCredentialFormat.MDOC,
            type = "org.iso.18013.5.1.mDL",
            credentialData = buildJsonObject {
                putJsonObject("org.iso.18013.5.1") {
                    put("document_number", "D-123-456")
                }
            },
            storedLabel = "mDL",
        )

        assertEquals("Mobile Driving License", display.title)
        assertEquals("D-123-456", display.subtitle)
    }

    @Test
    fun mdlNamespaceKeyMatchesTheSameMapping() {
        val display = MobileWalletRegistryDisplay.resolve(
            format = MobileWalletDigitalCredentialFormat.MDOC,
            type = "org.iso.18013.5.1",
            credentialData = buildJsonObject {
                putJsonObject("org.iso.18013.5.1") {
                    put("document_number", "NS-99")
                }
            },
            storedLabel = null,
        )

        assertEquals("Mobile Driving License", display.title)
        assertEquals("NS-99", display.subtitle)
    }

    @Test
    fun pidUsesStaticTitleAndDocumentNumber() {
        val display = MobileWalletRegistryDisplay.resolve(
            format = MobileWalletDigitalCredentialFormat.MDOC,
            type = "eu.europa.ec.eudi.pid.1",
            credentialData = buildJsonObject {
                putJsonObject("eu.europa.ec.eudi.pid.1") {
                    put("document_number", "AT-0001")
                }
            },
            storedLabel = "PID",
        )

        assertEquals("Personal ID", display.title)
        assertEquals("AT-0001", display.subtitle)
    }

    @Test
    fun missingMappedClaimFallsBackFieldByField() {
        val display = MobileWalletRegistryDisplay.resolve(
            format = MobileWalletDigitalCredentialFormat.MDOC,
            type = PAYMENT_CARD,
            credentialData = buildJsonObject {
                putJsonObject(PAYMENT_CARD) {
                    put("card_scheme", "visa")
                }
            },
            storedLabel = "Payment card",
        )

        assertEquals("Payment card", display.title)
        assertEquals("Payment Card 1", display.subtitle)
        assertFalse(display.subtitle.contains(PAYMENT_CARD))
    }

    @Test
    fun unknownTypeUsesStoredLabelAndHumanizedSubtitle() {
        val display = MobileWalletRegistryDisplay.resolve(
            format = MobileWalletDigitalCredentialFormat.MDOC,
            type = "org.example.unknown.widget.1",
            credentialData = buildJsonObject {},
            storedLabel = "Stored widget",
        )

        assertEquals("Stored widget", display.title)
        assertEquals("Widget 1", display.subtitle)
        assertFalse(display.title.contains("org.example"))
        assertFalse(display.subtitle.contains("org.example"))
    }

    @Test
    fun unknownTypeWithoutLabelHumanizesTitle() {
        val display = MobileWalletRegistryDisplay.resolve(
            format = MobileWalletDigitalCredentialFormat.SD_JWT_VC,
            type = "https://credentials.example/this_case",
            credentialData = buildJsonObject {
                put("vct", "https://credentials.example/this_case")
            },
            storedLabel = null,
        )

        assertEquals("This Case", display.title)
        assertEquals("This Case", display.subtitle)
    }

    private companion object {
        const val PAYMENT_CARD = "eu.europa.ec.eudi.sca.payment_card.1"
    }
}
