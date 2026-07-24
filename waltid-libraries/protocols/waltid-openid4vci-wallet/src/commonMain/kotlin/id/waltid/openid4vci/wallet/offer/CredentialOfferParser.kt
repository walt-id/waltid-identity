package id.waltid.openid4vci.wallet.offer

import id.walt.openid4vci.offers.CROSS_DEVICE_CREDENTIAL_OFFER_URL
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.offers.CredentialOfferRequest
import io.ktor.http.*
import kotlinx.serialization.json.Json

/**
 * Parser for OpenID4VCI credential offer URLs.
 * Implements §4.1 of OpenID4VCI 1.0 specification.
 */
object CredentialOfferParser {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    /**
     * Parses a credential offer URL (openid-credential-offer://)
     * 
     * @param offerUrl The credential offer URL string
     * @return CredentialOfferRequest containing either inline offer or URI reference
     * @throws IllegalArgumentException if URL is malformed
     */
    fun parseCredentialOfferUrl(offerUrl: String): CredentialOfferRequest {
        require(offerUrl.isNotBlank()) { "Offer URL cannot be blank" }

        // Parse the URL
        val url = try {
            Url(offerUrl)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid credential offer URL: $offerUrl", e)
        }

        // Validate scheme
        require(url.protocol.name.equals(CROSS_DEVICE_CREDENTIAL_OFFER_URL.removeSuffix("://"), ignoreCase = true)) {
            "Invalid credential offer URL scheme. Expected: $CROSS_DEVICE_CREDENTIAL_OFFER_URL"
        }

        // Extract query parameters
        val parameters = url.parameters

        // Check for credential_offer (inline)
        val credentialOfferParam = parameters["credential_offer"]
        if (credentialOfferParam != null) {
            val credentialOffer = try {
                json.decodeFromString<CredentialOffer>(credentialOfferParam)
            } catch (e: Exception) {
                throw IllegalArgumentException("Failed to parse inline credential offer", e)
            }
            return CredentialOfferRequest(
                credentialOffer = credentialOffer,
                credentialOfferUri = null
            )
        }

        // Check for credential_offer_uri (by reference)
        val credentialOfferUri = parameters["credential_offer_uri"]
        if (credentialOfferUri != null) {
            // Validate URI
            try {
                Url(credentialOfferUri)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid credential_offer_uri: $credentialOfferUri", e)
            }
            return CredentialOfferRequest(
                credentialOffer = null,
                credentialOfferUri = credentialOfferUri
            )
        }

        throw IllegalArgumentException("Credential offer URL must contain either 'credential_offer' or 'credential_offer_uri' parameter")
    }

    /**
     * Extracts credential offer from URL query parameters directly
     * Useful for parsing offers from HTTP URLs (e.g., same-device flow)
     * 
     * @param url The URL containing credential offer parameters
     * @return CredentialOfferRequest
     */
    fun parseFromQueryParams(url: String): CredentialOfferRequest {
        val parsedUrl = Url(url)
        val parameters = parsedUrl.parameters

        val credentialOfferParam = parameters["credential_offer"]
        if (credentialOfferParam != null) {
            val credentialOffer = json.decodeFromString<CredentialOffer>(credentialOfferParam)
            return CredentialOfferRequest(
                credentialOffer = credentialOffer,
                credentialOfferUri = null
            )
        }

        val credentialOfferUri = parameters["credential_offer_uri"]
        if (credentialOfferUri != null) {
            return CredentialOfferRequest(
                credentialOffer = null,
                credentialOfferUri = credentialOfferUri
            )
        }

        throw IllegalArgumentException("URL must contain either 'credential_offer' or 'credential_offer_uri' parameter")
    }

    /**
     * Checks if a URL is a valid credential offer URL
     * 
     * @param url The URL to check
     * @return true if the URL is a valid credential offer URL
     */
    fun isCredentialOfferUrl(url: String): Boolean {
        return try {
            val parsedUrl = Url(url)
            parsedUrl.protocol.name.equals(CROSS_DEVICE_CREDENTIAL_OFFER_URL.removeSuffix("://"), ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
}
