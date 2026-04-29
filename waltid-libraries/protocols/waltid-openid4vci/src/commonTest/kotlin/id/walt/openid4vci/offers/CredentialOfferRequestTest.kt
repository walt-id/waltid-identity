package id.walt.openid4vci.offers

import io.ktor.http.Url
import io.ktor.util.toMap
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CredentialOfferRequestTest {

    private val json = Json { encodeDefaults = false; explicitNulls = false }

    @Test
    fun `credential offer request builds offer parameter`() {
        val offer = CredentialOffer.withAuthorizationCodeGrant(
            credentialIssuer = "https://issuer.example",
            credentialConfigurationIds = listOf("cred-id-1"),
            issuerState = "issuer-state-123",
        )
        val request = CredentialOfferRequest(credentialOffer = offer)
        val url = request.toUrl()

        assertTrue(url.startsWith(CROSS_DEVICE_CREDENTIAL_OFFER_URL))
        val params = Url(url).parameters.toMap()
        val encoded = params["credential_offer"]?.first()
        assertNotNull(encoded)
        val decoded = json.decodeFromString(CredentialOffer.serializer(), encoded)
        assertEquals(offer, decoded)
        assertEquals(offer.grants?.authorizationCode?.issuerState, decoded.grants?.authorizationCode?.issuerState)
    }

    @Test
    fun `credential offer request builds offer uri parameter`() {
        val request = CredentialOfferRequest(credentialOfferUri = "https://issuer.example/offers/123")
        val url = request.toUrl()

        assertTrue(url.startsWith(CROSS_DEVICE_CREDENTIAL_OFFER_URL))
        val params = Url(url).parameters.toMap()
        assertEquals("https://issuer.example/offers/123", params["credential_offer_uri"]?.first())
    }

    @Test
    fun `credential offer request encodes credential offer payload`() {
        val offer = CredentialOffer.withAuthorizationCodeGrant(
            credentialIssuer = "https://issuer.example",
            credentialConfigurationIds = listOf("cred-id-1"),
        )
        val request = CredentialOfferRequest(credentialOffer = offer)
        val params = request.toHttpParameters()

        val encoded = params["credential_offer"]?.first()
        assertNotNull(encoded)
        val decoded = json.decodeFromString(CredentialOffer.serializer(), encoded)
        assertEquals(offer, decoded)
    }

    @Test
    fun `credential offer request rejects both offer and uri`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialOfferRequest(
                credentialOffer = CredentialOffer.withAuthorizationCodeGrant(
                    credentialIssuer = "https://issuer.example",
                    credentialConfigurationIds = listOf("cred-id-1"),
                ),
                credentialOfferUri = "https://issuer.example/offers/123",
            )
        }
    }

    @Test
    fun `credential offer request rejects missing offer and uri`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialOfferRequest()
        }
    }

    @Test
    fun `credential offer request rejects reserved custom parameters`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialOfferRequest(
                credentialOfferUri = "https://issuer.example/offers/123",
                customParameters = mapOf("credential_offer" to listOf("override")),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CredentialOfferRequest(
                credentialOfferUri = "https://issuer.example/offers/123",
                customParameters = mapOf("credential_offer_uri" to listOf("override")),
            )
        }
    }
}
