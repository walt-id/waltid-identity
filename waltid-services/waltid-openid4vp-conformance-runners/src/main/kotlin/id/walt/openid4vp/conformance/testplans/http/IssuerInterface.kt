package id.walt.openid4vp.conformance.testplans.http

import id.walt.openid4vp.conformance.testplans.runner.req.CredentialOfferAuthMethod
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * HTTP client for walt.id issuer-api2 management endpoints.
 * Used to create credential offers for issuer-initiated conformance tests.
 */
class IssuerInterface(private val issuerBaseUrl: String) : AutoCloseable {

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    /** Create a credential offer using the authentication method required by the variant. */
    suspend fun createCredentialOffer(
        profileId: String,
        authMethod: CredentialOfferAuthMethod,
        staticTxCode: String? = null,
    ): CredentialOfferResponse {
        val preAuthorizedTxCode = staticTxCode.takeIf { authMethod == CredentialOfferAuthMethod.PRE_AUTHORIZED }
        val response = httpClient.post("$issuerBaseUrl/issuer2/credential-offers") {
            contentType(ContentType.Application.Json)
            setBody(CredentialOfferRequest(
                profileId = profileId,
                authMethod = authMethod,
                txCode = preAuthorizedTxCode?.toTxCodeMetadata(),
                txCodeValue = preAuthorizedTxCode,
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

    override fun close() {
        httpClient.close()
    }
}

@Serializable
data class CredentialOfferRequest(
    val profileId: String,
    val authMethod: CredentialOfferAuthMethod,
    val txCode: TxCode? = null,
    val txCodeValue: String? = null,
)

@Serializable
data class TxCode(
    @SerialName("input_mode")
    val inputMode: String,
    val length: Int,
    val description: String,
)

@Serializable
data class CredentialOfferResponse(
    val offerId: String,
    val profileId: String,
    val authMethod: String,
    val expiresAt: Long,
    val credentialOffer: String,
    val txCodeValue: String? = null,
)

@Serializable
data class CredentialProfile(
    val profileId: String,
    val name: String,
    val credentialConfigurationId: String
)

private fun String.toTxCodeMetadata() = TxCode(
    inputMode = if (all(Char::isDigit)) "numeric" else "text",
    length = length,
    description = "OpenID4VCI conformance transaction code",
)
