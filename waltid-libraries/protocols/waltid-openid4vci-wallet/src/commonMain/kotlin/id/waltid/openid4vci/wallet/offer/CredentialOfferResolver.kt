package id.waltid.openid4vci.wallet.offer

import id.walt.openid4vci.offers.CredentialOffer
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

private val log = KotlinLogging.logger {}

/**
 * Resolves credential offer URIs by fetching them via HTTP.
 * Implements §4.2 of OpenID4VCI 1.0 specification (Credential Offer Endpoint).
 * 
 * @property httpClient The HTTP client to use for fetching offers
 */
class CredentialOfferResolver(
    private val httpClient: HttpClient,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    /**
     * Resolves a credential offer URI to a CredentialOffer object
     * 
     * @param credentialOfferUri The URI to fetch the credential offer from
     * @return The resolved CredentialOffer
     * @throws IllegalArgumentException if URI is invalid
     * @throws Exception if HTTP request fails or response is invalid
     */
    suspend fun resolveCredentialOfferUri(credentialOfferUri: String): CredentialOffer {
        require(credentialOfferUri.isNotBlank()) { "Credential offer URI cannot be blank" }

        log.debug { "Resolving credential offer URI: $credentialOfferUri" }

        // Validate URI format
        val url = try {
            Url(credentialOfferUri)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid credential offer URI: $credentialOfferUri", e)
        }

        // Fetch the credential offer
        val response: HttpResponse = try {
            httpClient.get(url)
        } catch (e: Exception) {
            log.error(e) { "Failed to fetch credential offer from URI: $credentialOfferUri" }
            throw Exception("Failed to fetch credential offer from URI: $credentialOfferUri", e)
        }

        // Check response status
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            log.error { "Failed to fetch credential offer. Status: ${response.status}, Body: $errorBody" }
            throw Exception("Failed to fetch credential offer. Status: ${response.status}")
        }

        // Parse the response
        val credentialOffer = try {
            response.body<CredentialOffer>()
        } catch (e: Exception) {
            val responseBody = response.bodyAsText()
            log.error(e) { "Failed to parse credential offer response. Body: $responseBody" }
            throw Exception("Failed to parse credential offer response", e)
        }

        log.debug { "Successfully resolved credential offer from URI" }
        return credentialOffer
    }

    /**
     * Resolves a credential offer from either inline data or URI reference
     * 
     * @param credentialOffer The inline credential offer (if present)
     * @param credentialOfferUri The credential offer URI (if present)
     * @return The resolved CredentialOffer
     * @throws IllegalArgumentException if both parameters are null or both are provided
     */
    suspend fun resolveCredentialOffer(
        credentialOffer: CredentialOffer?,
        credentialOfferUri: String?,
    ): CredentialOffer {
        return when {
            credentialOffer != null && credentialOfferUri == null -> {
                log.debug { "Using inline credential offer" }
                credentialOffer
            }

            credentialOffer == null && credentialOfferUri != null -> {
                log.debug { "Resolving credential offer from URI" }
                resolveCredentialOfferUri(credentialOfferUri)
            }

            credentialOffer != null && credentialOfferUri != null -> {
                throw IllegalArgumentException("Cannot provide both inline credential offer and URI")
            }

            else -> {
                throw IllegalArgumentException("Must provide either inline credential offer or URI")
            }
        }
    }
}
