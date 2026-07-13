package id.walt.openid4vp.conformance.testplans.http

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * HTTP client for walt.id issuer-api2 management endpoints.
 * Used to create credential offers for pre-authorized code conformance tests.
 */
class IssuerInterface(private val issuerBaseUrl: String) {
    
    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    /**
     * Create a pre-authorized credential offer.
     */
    suspend fun createCredentialOffer(profileId: String): CredentialOfferResponse {
        val response = httpClient.post("$issuerBaseUrl/issuer2/credential-offers") {
            contentType(ContentType.Application.Json)
            setBody(CredentialOfferRequest(
                profileId = profileId,
                authMethod = "PRE_AUTHORIZED"
            ))
        }
        return response.body()
    }

    /**
     * List available credential profiles.
     */
    suspend fun listProfiles(): List<CredentialProfile> {
        val response = httpClient.get("$issuerBaseUrl/issuer2/profiles")
        return response.body()
    }

    fun close() {
        httpClient.close()
    }
}

@Serializable
data class CredentialOfferRequest(
    val profileId: String,
    val authMethod: String
)

@Serializable
data class CredentialOfferResponse(
    val offerId: String,
    val profileId: String,
    val authMethod: String,
    val expiresAt: Long,
    val credentialOffer: String
)

@Serializable
data class CredentialProfile(
    val profileId: String,
    val name: String,
    val credentialConfigurationId: String
)
