package id.waltid.openid4vci.wallet.clientauth

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.selectJwsAlgorithm
import id.walt.crypto2.keys.Key as Crypto2Key
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/** `client_assertion_type` value identifying an RFC 7523 JWT bearer client assertion. */
const val CLIENT_ASSERTION_TYPE_JWT_BEARER = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"

/**
 * Builds RFC 7523 client assertions for `private_key_jwt` token endpoint client authentication.
 *
 * The assertion proves control of a key the authorization server already holds for this client, so
 * unlike a DPoP proof it deliberately does **not** embed the public key: the server must resolve it
 * from its own registration data. Only `kid` is advertised, to let a server with several registered
 * keys pick the right one.
 */
class ClientAssertionBuilder {

    /**
     * Creates a single-use assertion.
     *
     * [audience] is the authorization server's token endpoint or issuer identifier - RFC 7523 §3
     * permits either, and the value must be whatever the server expects to see in `aud`.
     *
     * [lifetime] is kept short because the assertion is replay-protected only by `jti` and `exp`.
     */
    suspend fun buildAssertion(
        key: Crypto2Key,
        clientId: String,
        audience: String,
        lifetime: Duration = DEFAULT_LIFETIME,
        supportedAlgorithms: Set<String>? = null,
    ): String {
        require(clientId.isNotBlank()) { "Client assertion requires a client id" }
        require(audience.isNotBlank()) { "Client assertion requires an audience" }
        require(lifetime.isPositive()) { "Client assertion lifetime must be positive" }

        val algorithm = key.selectJwsAlgorithm(supportedAlgorithms)
        val issuedAt = Clock.System.now()

        val header = buildJsonObject {
            put("typ", "JWT")
            put("kid", key.id.value)
        }
        val payload = buildJsonObject {
            put("iss", clientId)
            put("sub", clientId)
            put("aud", audience)
            put("jti", Uuid.random().toString())
            put("iat", issuedAt.epochSeconds)
            put("exp", (issuedAt + lifetime).epochSeconds)
        }

        return CompactJws.sign(
            payload = Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray(),
            key = key,
            algorithm = algorithm,
            protectedHeader = header,
        )
    }

    private companion object {
        val DEFAULT_LIFETIME = 5.minutes
    }
}
