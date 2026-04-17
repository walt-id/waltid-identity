package id.waltid.openid4vci.wallet.offer

import id.walt.openid4vci.offers.CROSS_DEVICE_CREDENTIAL_OFFER_URL
import kotlin.test.*

class CredentialOfferParserTest {

    @Test
    fun testParseInlineCredentialOfferUrl() {
        val jsonOffer = """{"credential_issuer":"https://example.com","credential_configuration_ids":["test_id"]}"""
        val offerUrl = "${CROSS_DEVICE_CREDENTIAL_OFFER_URL}?credential_offer=$jsonOffer"

        val request = CredentialOfferParser.parseCredentialOfferUrl(offerUrl)

        assertNotNull(request.credentialOffer)
        assertEquals("https://example.com", request.credentialOffer?.credentialIssuer)
        assertEquals(listOf("test_id"), request.credentialOffer?.credentialConfigurationIds)
        assertNull(request.credentialOfferUri)
    }

    @Test
    fun testParseCredentialOfferUriUrl() {
        val offerUri = "https://example.com/offer/123"
        val offerUrl = "${CROSS_DEVICE_CREDENTIAL_OFFER_URL}?credential_offer_uri=$offerUri"

        val request = CredentialOfferParser.parseCredentialOfferUrl(offerUrl)

        assertNull(request.credentialOffer)
        assertEquals(offerUri, request.credentialOfferUri)
    }

    @Test
    fun testParseFromQueryParams() {
        val jsonOffer = """{"credential_issuer":"https://example.com","credential_configuration_ids":["test_id"]}"""
        val httpUrl = "https://wallet.example.com/callback?credential_offer=$jsonOffer"

        val request = CredentialOfferParser.parseFromQueryParams(httpUrl)

        assertNotNull(request.credentialOffer)
        assertEquals("https://example.com", request.credentialOffer?.credentialIssuer)
        assertNull(request.credentialOfferUri)
    }

    @Test
    fun testIsCredentialOfferUrl() {
        val validUrl = "${CROSS_DEVICE_CREDENTIAL_OFFER_URL}?credential_offer_uri=https://example.com"
        val invalidUrl = "https://example.com/not-an-offer"

        assertTrue(CredentialOfferParser.isCredentialOfferUrl(validUrl))
        assertFalse(CredentialOfferParser.isCredentialOfferUrl(invalidUrl))
    }

    @Test
    fun testParseInvalidScheme() {
        val invalidUrl = "wrong-scheme://?credential_offer_uri=https://example.com"
        assertFailsWith<IllegalArgumentException> {
            CredentialOfferParser.parseCredentialOfferUrl(invalidUrl)
        }
    }

    @Test
    fun testParseMissingParameters() {
        val invalidUrl = "${CROSS_DEVICE_CREDENTIAL_OFFER_URL}?something_else=value"
        assertFailsWith<IllegalArgumentException> {
            CredentialOfferParser.parseCredentialOfferUrl(invalidUrl)
        }
    }

    @Test
    fun testParseMalformedJson() {
        val invalidJson = """{"credential_issuer":"""
        val offerUrl = "${CROSS_DEVICE_CREDENTIAL_OFFER_URL}?credential_offer=$invalidJson"
        assertFailsWith<IllegalArgumentException> {
            CredentialOfferParser.parseCredentialOfferUrl(offerUrl)
        }
    }
}
