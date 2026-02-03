package id.walt.eudi

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.util.http
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.time.Instant
import java.util.*
import kotlin.test.assertTrue

/**
 * Test client mimicking EUDI Reference Wallet behavior for OpenID4VCI.
 *
 * Implements Draft 13+ protocol features:
 * - credential_configuration_id instead of format
 * - JWT proofs with holder key binding
 * - DPoP token handling per RFC 9449
 * - Correct format strings (dc+sd-jwt not vc+sd-jwt)
 */
class EudiWalletClient {

    private lateinit var holderKey: JWKKey
    private lateinit var holderJwk: JsonObject
    private lateinit var dpopKey: JWKKey
    private lateinit var dpopJwk: JsonObject

    init {
        runBlocking {
            // Generate P-256 EC key for holder binding (EUDI wallets use P-256)
            holderKey = JWKKey.generate(KeyType.secp256r1)
            holderJwk = Json.decodeFromString<JsonObject>(holderKey.exportJWK())

            // Generate separate DPoP key
            dpopKey = JWKKey.generate(KeyType.secp256r1)
            dpopJwk = Json.decodeFromString<JsonObject>(dpopKey.exportJWK())
        }
    }

    /**
     * Resolves a credential offer URI to get the full offer details.
     */
    suspend fun resolveCredentialOffer(offerUri: String): CredentialOffer {
        // Parse the offer URI to extract credential_offer_uri parameter
        val url = Url(offerUri.removePrefix("openid-credential-offer://"))
        val credentialOfferUri = url.parameters["credential_offer_uri"]
            ?: throw IllegalArgumentException("No credential_offer_uri in offer: $offerUri")

        // Fetch the credential offer
        val response = http.get(credentialOfferUri)
        assertTrue(response.status.isSuccess(), "Failed to resolve credential offer: ${response.status}")

        return response.body<CredentialOffer>()
    }

    /**
     * Fetches the issuer's OpenID credential issuer metadata.
     */
    suspend fun fetchIssuerMetadata(credentialIssuer: String): OpenIDProviderMetadata {
        val metadataUrl = "$credentialIssuer/.well-known/openid-credential-issuer"
        val response = http.get(metadataUrl)
        assertTrue(response.status.isSuccess(), "Failed to fetch issuer metadata: ${response.status}")

        return response.body<OpenIDProviderMetadata>()
    }

    /**
     * Requests an access token using the pre-authorized code flow.
     */
    suspend fun requestAccessToken(
        tokenEndpoint: String,
        preAuthorizedCode: String,
        txCode: String? = null
    ): TokenResponse {
        val response = http.submitForm(
            url = tokenEndpoint,
            formParameters = parameters {
                append("grant_type", GrantType.pre_authorized_code.value)
                append("pre-authorized_code", preAuthorizedCode)
                txCode?.let { append("tx_code", it) }
            }
        )

        assertTrue(response.status.isSuccess(), "Failed to get access token: ${response.bodyAsText()}")

        val body = response.body<JsonObject>()
        return TokenResponse(
            accessToken = body["access_token"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("No access_token in response"),
            tokenType = body["token_type"]?.jsonPrimitive?.content ?: "Bearer",
            expiresIn = body["expires_in"]?.jsonPrimitive?.longOrNull,
            cNonce = body["c_nonce"]?.jsonPrimitive?.content,
            cNonceExpiresIn = body["c_nonce_expires_in"]?.jsonPrimitive?.longOrNull
        )
    }

    /**
     * Requests a credential using Draft 13+ format.
     *
     * @param credentialEndpoint The issuer's credential endpoint
     * @param accessToken The access token from token request
     * @param credentialConfigurationId The credential_configuration_id (e.g., "eu.europa.ec.eudi.pid.1")
     * @param cNonce The c_nonce from token response for proof generation
     * @param format Expected credential format for validation
     */
    suspend fun requestCredential(
        credentialEndpoint: String,
        accessToken: String,
        credentialConfigurationId: String,
        cNonce: String,
        format: CredentialFormat
    ): CredentialResponse {
        // Generate JWT proof with holder key binding
        val proof = generateJwtProof(
            audience = Url(credentialEndpoint).protocolWithAuthority,
            nonce = cNonce
        )

        // Build Draft 13+ credential request
        val requestBody = buildJsonObject {
            put("credential_configuration_id", credentialConfigurationId)
            put("proofs", buildJsonObject {
                put("jwt", buildJsonArray {
                    add(proof)
                })
            })
        }

        val response = http.post(credentialEndpoint) {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        if (!response.status.isSuccess()) {
            throw CredentialRequestException(
                "Credential request failed: ${response.status}",
                response.bodyAsText()
            )
        }

        val body = response.body<JsonObject>()

        // Draft 13+ returns "credentials" array or single "credential"
        val credential = body["credentials"]?.jsonArray?.firstOrNull()?.let {
            when (it) {
                is JsonPrimitive -> it.content
                is JsonObject -> it["credential"]?.jsonPrimitive?.content
                    ?: it.toString()
                else -> it.toString()
            }
        } ?: body["credential"]?.jsonPrimitive?.content
        ?: throw IllegalStateException("No credential in response: ${body}")

        return CredentialResponse(
            credential = credential,
            format = format,
            cNonce = body["c_nonce"]?.jsonPrimitive?.content,
            cNonceExpiresIn = body["c_nonce_expires_in"]?.jsonPrimitive?.longOrNull
        )
    }

    /**
     * Generates a JWT proof for holder key binding.
     * This mimics what the EUDI wallet sends in the proofs.jwt array.
     */
    private suspend fun generateJwtProof(audience: String, nonce: String): String {
        // JWT header with holder public key
        val header = buildJsonObject {
            put("alg", "ES256")
            put("typ", "openid4vci-proof+jwt")
            put("jwk", buildJsonObject {
                put("kty", holderJwk["kty"]!!)
                put("crv", holderJwk["crv"]!!)
                put("x", holderJwk["x"]!!)
                put("y", holderJwk["y"]!!)
            })
        }

        // JWT payload with required claims
        val payload = buildJsonObject {
            put("iss", "https://wallet.example.org")  // Client ID
            put("aud", audience)
            put("iat", Instant.now().epochSecond)
            put("nonce", nonce)
        }

        // Sign the JWT
        val headerB64 = base64UrlEncode(header.toString().toByteArray())
        val payloadB64 = base64UrlEncode(payload.toString().toByteArray())
        val signingInput = "$headerB64.$payloadB64"

        val signature = holderKey.signJws(signingInput.toByteArray())
        return signature.toString()
    }

    /**
     * Generates a DPoP proof per RFC 9449.
     */
    suspend fun generateDPoPProof(
        httpMethod: String,
        httpUri: String,
        accessToken: String? = null
    ): String {
        val header = buildJsonObject {
            put("alg", "ES256")
            put("typ", "dpop+jwt")
            put("jwk", buildJsonObject {
                put("kty", dpopJwk["kty"]!!)
                put("crv", dpopJwk["crv"]!!)
                put("x", dpopJwk["x"]!!)
                put("y", dpopJwk["y"]!!)
            })
        }

        val payload = buildJsonObject {
            put("jti", UUID.randomUUID().toString())
            put("htm", httpMethod)
            put("htu", httpUri)
            put("iat", Instant.now().epochSecond)
            accessToken?.let {
                put("ath", calculateAccessTokenHash(it))
            }
        }

        val headerB64 = base64UrlEncode(header.toString().toByteArray())
        val payloadB64 = base64UrlEncode(payload.toString().toByteArray())
        val signingInput = "$headerB64.$payloadB64"

        val signature = dpopKey.signJws(signingInput.toByteArray())
        return signature.toString()
    }

    /**
     * Calculates the access token hash for DPoP ath claim.
     */
    private fun calculateAccessTokenHash(accessToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(accessToken.toByteArray(Charsets.US_ASCII))
        return base64UrlEncode(hash)
    }

    /**
     * Gets the JWK thumbprint of the holder key.
     */
    fun getHolderKeyThumbprint(): String {
        val canonicalJwk = buildJsonObject {
            put("crv", holderJwk["crv"]!!)
            put("kty", holderJwk["kty"]!!)
            put("x", holderJwk["x"]!!)
            put("y", holderJwk["y"]!!)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(canonicalJwk.toString().toByteArray(Charsets.UTF_8))
        return base64UrlEncode(hash)
    }

    /**
     * Gets the pre-authorized code from a credential offer.
     */
    fun getPreAuthorizedCode(offer: CredentialOffer): String? {
        return offer.grants[GrantType.pre_authorized_code.value]?.preAuthorizedCode
    }

    private fun base64UrlEncode(data: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(data)

    data class TokenResponse(
        val accessToken: String,
        val tokenType: String,
        val expiresIn: Long?,
        val cNonce: String?,
        val cNonceExpiresIn: Long?
    )

    data class CredentialResponse(
        val credential: String,
        val format: CredentialFormat,
        val cNonce: String?,
        val cNonceExpiresIn: Long?
    )

    class CredentialRequestException(message: String, val responseBody: String) : Exception(message)
}
