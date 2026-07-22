package id.walt.verifier2.handlers.authrequest

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import id.walt.verifier2.data.SessionEvent
import id.walt.verifier2.data.Verification2Session
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

/**
 * Handles the `request_uri_method=post` flow per OID4VP 1.0 §5.6.
 *
 * When the wallet sends an HTTP POST to the request URI endpoint (instead of the default GET),
 * it MAY include:
 * - `wallet_metadata`: the wallet's capabilities as a JSON object
 * - `wallet_nonce`: a fresh random value to bind this specific request, preventing replay
 *
 * The verifier MUST include any received `wallet_nonce` in the signed request object payload.
 * This prevents an attacker from replaying a captured request object to a different wallet.
 *
 * Per spec §5.6: "When received, the Verifier MUST use it as the `wallet_nonce` value in
 * the signed authorization request object."
 */
object Verifier2RequestUriPostHandler {

    private val log = KotlinLogging.logger { }
    private val crypto2Runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    suspend fun RoutingCall.respondRequestUriPostCrypto2(
        verificationSession: Verification2Session,
        updateSessionCallback: suspend (session: Verification2Session, event: SessionEvent, block: Verification2Session.() -> Unit) -> Unit,
        resolveCrypto2SigningKey: suspend (Verification2Session) -> Crypto2Key?,
    ) = respondRequestUriPost(
        verificationSession = verificationSession,
        updateSessionCallback = updateSessionCallback,
        resolveSigningKey = { null },
        resolveCrypto2SigningKey = resolveCrypto2SigningKey,
    )

    /**
     * Handles a POST to `/{sessionId}/request`.
     *
     * Parses `wallet_nonce` from the POST body, re-signs the request object with it injected,
     * and returns the fresh JWT with Content-Type `application/oauth-authz-req+jwt`.
     *
     * If the session uses an unsigned request, the POST body is ignored and the normal
     * unsigned authorization request is returned as JSON (same behaviour as GET).
     */
    suspend fun RoutingCall.respondRequestUriPost(
        verificationSession: Verification2Session,
        updateSessionCallback: suspend (session: Verification2Session, event: SessionEvent, block: Verification2Session.() -> Unit) -> Unit,
        resolveSigningKey: suspend (Verification2Session) -> Key? = { it.setup.core.key?.key },
        resolveCrypto2SigningKey: suspend (Verification2Session) -> Crypto2Key? = { null },
    ) {
        val call = this

        // Parse wallet_nonce and wallet_metadata from the URL-encoded POST body
        val bodyParams = call.receiveParameters()
        val walletNonce = bodyParams["wallet_nonce"]
        val walletMetadataJson = bodyParams["wallet_metadata"]

        log.debug {
            "Request URI POST for session ${verificationSession.id}: " +
                "wallet_nonce=${walletNonce?.take(16)}, wallet_metadata=${walletMetadataJson?.take(50)}"
        }

        val isSigned = verificationSession.requestMode == Verification2Session.RequestMode.REQUEST_URI_SIGNED
        val existingJwt = verificationSession.signedAuthorizationRequestJwt

        if (!isSigned || existingJwt == null) {
            // Unsigned session: fall back to normal GET handling
            Verifier2AuthorizationRequestHandler.run {
                respondAuthorizationRequest(verificationSession, updateSessionCallback)
            }
            return
        }

        // Re-sign the request object including wallet_nonce per OID4VP 1.0 §5.6
        val crypto2SigningKey = resolveCrypto2SigningKey(verificationSession)
        val signingKey = if (crypto2SigningKey == null) resolveSigningKey(verificationSession) else null
        require(crypto2SigningKey != null || signingKey != null) {
            "No signing key available to bind wallet_nonce to the signed request"
        }
        if (crypto2SigningKey != null) {
            verifyExistingRequestObject(existingJwt, crypto2SigningKey)
        } else {
            verifyExistingRequestObject(existingJwt, requireNotNull(signingKey))
        }

        // Decode existing JWT to get headers and payload
        val parts = existingJwt.split(".")
        require(parts.size == 3) { "Existing signed request JWT is malformed" }

        val existingHeaderBytes = parts[0].decodeFromBase64Url()
        val existingPayloadBytes = parts[1].decodeFromBase64Url()

        val existingHeaders = Json.parseToJsonElement(existingHeaderBytes.decodeToString()).jsonObject
        val existingPayload = Json.parseToJsonElement(existingPayloadBytes.decodeToString()).jsonObject

        // Inject wallet_nonce into the payload (per spec §5.6 §response §wallet_nonce)
        val newPayload = buildJsonObject {
            existingPayload.forEach { (k, v) -> put(k, v) }
            if (walletNonce != null) {
                put("wallet_nonce", JsonPrimitive(walletNonce))
            }
            // Refresh iat since we're re-signing
            put("iat", JsonPrimitive(Clock.System.now().epochSeconds))
        }

        // Re-sign with the same headers (alg, typ, x5c, kid etc.)
        val freshJwt = if (crypto2SigningKey != null) {
            signRequestObject(crypto2SigningKey, newPayload, existingHeaders)
        } else {
            signRequestObject(requireNotNull(signingKey), newPayload, existingHeaders)
        }

        updateSessionCallback(verificationSession, SessionEvent.authorization_request_requested) {
            status = Verification2Session.VerificationSessionStatus.IN_USE
        }

        log.trace { "Re-signed request object with wallet_nonce for session ${verificationSession.id}" }
        call.respondText(freshJwt, ContentType.parse("application/oauth-authz-req+jwt"))
    }

    @Deprecated("Use the Crypto2Key overload")
    internal suspend fun signRequestObject(
        signingKey: Key,
        payload: JsonObject,
        headers: JsonObject,
    ): String {
        requireMatchingKeyId(headers, signingKey.getKeyId())
        val crypto2Key = resolveCrypto2Key(signingKey, setOf(KeyUsage.SIGN))
        return if (crypto2Key != null) {
            val algorithm = JwsAlgorithm.parse(
                requireNotNull(headers["alg"]?.jsonPrimitive?.contentOrNull) {
                    "Existing signed request JWT is missing alg"
                }
            )
            CompactJws.sign(
                payload = Json.encodeToString(payload).encodeToByteArray(),
                key = crypto2Key,
                algorithm = algorithm,
                protectedHeader = headers,
            )
        } else signingKey.signJws(payload.toString().encodeToByteArray(), headers)
    }

    @Deprecated("Use the Crypto2Key overload")
    internal suspend fun verifyExistingRequestObject(jwt: String, signingKey: Key) {
        val decoded = CompactJws.decodeUnverified(jwt)
        requireMatchingKeyId(decoded.protectedHeader, signingKey.getKeyId())
        val algorithm = JwsAlgorithm.parse(
            requireNotNull(decoded.protectedHeader["alg"]?.jsonPrimitive?.contentOrNull) {
                "Existing signed request JWT is missing alg"
            }
        )
        val crypto2Key = resolveCrypto2Key(signingKey, setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        if (crypto2Key != null) {
            CompactJws.verify(jwt, crypto2Key, algorithm)
        } else {
            signingKey.getPublicKey().verifyJws(jwt).getOrThrow()
        }
    }

    internal suspend fun signRequestObject(
        signingKey: Crypto2Key,
        payload: JsonObject,
        headers: JsonObject,
    ): String {
        requireMatchingKeyId(headers, signingKey.id.value)
        val algorithm = JwsAlgorithm.parse(
            requireNotNull(headers["alg"]?.jsonPrimitive?.contentOrNull) {
                "Existing signed request JWT is missing alg"
            }
        )
        return CompactJws.sign(
            payload = Json.encodeToString(payload).encodeToByteArray(),
            key = signingKey,
            algorithm = algorithm,
            protectedHeader = headers,
        )
    }

    internal suspend fun verifyExistingRequestObject(jwt: String, signingKey: Crypto2Key) {
        val decoded = CompactJws.decodeUnverified(jwt)
        requireMatchingKeyId(decoded.protectedHeader, signingKey.id.value)
        val algorithm = decoded.algorithm
        val publicJwk = requireNotNull(signingKey.capabilities.publicKeyExporter) {
            "Request signing key does not export public material"
        }.exportPublicKey().toPublicJwk(signingKey.spec)
        val verificationKey = crypto2Runtime.restore(
            StoredKey.Software(
                version = StoredKey.CURRENT_VERSION,
                id = signingKey.id,
                spec = signingKey.spec,
                usages = setOf(KeyUsage.VERIFY),
                material = publicJwk,
            )
        )
        CompactJws.verify(jwt, verificationKey, algorithm)
    }

    private fun requireMatchingKeyId(headers: JsonObject, actualKeyId: String) {
        val protectedKeyId = requireNotNull(headers["kid"]?.jsonPrimitive?.contentOrNull) {
            "Existing signed request JWT is missing kid"
        }
        require(protectedKeyId == actualKeyId || protectedKeyId.endsWith("#$actualKeyId")) {
            "Resolved signing key does not match the existing signed request kid"
        }
    }

    private suspend fun resolveCrypto2Key(signingKey: Key, usages: Set<KeyUsage>) =
        (signingKey as? JWKKey)
            ?.takeUnless { it.keyType == KeyType.secp256k1 }
            ?.let { key ->
                val jwk = key.exportJWKObject()
                EncodedKey.Jwk(
                    BinaryData(Json.encodeToString(jwk).encodeToByteArray()),
                    privateMaterial = key.hasPrivateKey,
                ).toStoredSoftwareKey(KeyId(key.getKeyId()), usages)
            }
            ?.let { crypto2Runtime.restore(it) }
}
