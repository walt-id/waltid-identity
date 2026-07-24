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

        log.info { "Resolving credential offer from URI" }
        log.trace { "Credential offer URI: $credentialOfferUri" }

        // Validate URI format
        val url = try {
            Url(credentialOfferUri)
        } catch (e: Exception) {
            log.error(e) { "Invalid credential offer URI format: $credentialOfferUri" }
            throw IllegalArgumentException("Invalid credential offer URI: $credentialOfferUri", e)
        }

        log.trace { "Validated URI format, preparing HTTP request" }

        // Fetch the credential offer
        val response: HttpResponse = try {
            log.debug { "Fetching credential offer from: ${url.host}" }
            httpClient.get(url)
        } catch (e: Exception) {
            log.error(e) { "Network error while fetching credential offer from: ${url.host}" }
            throw Exception("Failed to fetch credential offer from URI: $credentialOfferUri", e)
        }

        // Check response status
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            log.error {
                "Credential offer fetch failed - Status: ${response.status.value} ${response.status.description}, " +
                "Response body: ${errorBody.take(200)}${if (errorBody.length > 200) "..." else ""}"
            }
            throw Exception("Failed to fetch credential offer. Status: ${response.status}")
        }

        log.trace { "Received successful response (${response.status.value}), parsing credential offer" }

        // Parse the response
        val credentialOffer = try {
            response.body<CredentialOffer>()
        } catch (e: Exception) {
            val responseBody = response.bodyAsText()
            log.error(e) {
                "Failed to parse credential offer response - " +
                "Body preview: ${responseBody.take(200)}${if (responseBody.length > 200) "..." else ""}"
            }
            throw Exception("Failed to parse credential offer response", e)
        }

        log.info {
            "Successfully resolved credential offer - " +
            "Issuer: ${credentialOffer.credentialIssuer}, " +
            "Credentials: ${credentialOffer.credentialConfigurationIds.size}"
        }
        log.trace { "Credential configuration IDs: ${credentialOffer.credentialConfigurationIds.joinToString()}" }
        
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
        log.trace { "Resolving credential offer - Inline: ${credentialOffer != null}, URI: ${credentialOfferUri != null}" }
        
        return when {
            credentialOffer != null && credentialOfferUri == null -> {
                log.info { "Using inline credential offer - Issuer: ${credentialOffer.credentialIssuer}" }
                log.trace { "Inline offer credentials: ${credentialOffer.credentialConfigurationIds.size}" }
                credentialOffer
            }

            credentialOffer == null && credentialOfferUri != null -> {
                log.info { "Resolving credential offer from URI reference" }
                resolveCredentialOfferUri(credentialOfferUri)
            }

            credentialOffer != null && credentialOfferUri != null -> {
                log.error { "Invalid credential offer request: both inline offer and URI provided" }
                throw IllegalArgumentException("Cannot provide both inline credential offer and URI")
            }

            else -> {
                log.error { "Invalid credential offer request: neither inline offer nor URI provided" }
                throw IllegalArgumentException("Must provide either inline credential offer or URI")
            }
        }
    }
}
