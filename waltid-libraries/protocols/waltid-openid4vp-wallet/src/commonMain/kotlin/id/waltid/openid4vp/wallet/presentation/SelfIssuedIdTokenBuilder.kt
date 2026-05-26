package id.waltid.openid4vp.wallet.presentation

import id.walt.crypto.keys.Key
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Builds a Self-Issued ID Token per SIOPv2 §6 and OID4VP 1.0 §"Combining this specification
 * with SIOPv2", for use with `response_type=vp_token id_token`.
 *
 * The ID Token is signed with the holder's key. The `sub` and `iss` are both set to the
 * JWK Thumbprint Subject Syntax Type URI (`urn:ietf:params:oauth:jwk-thumbprint:sha-256:<thumbprint>`)
 * when a DID is not available, or to the holder DID when one is provided.
 *
 * References:
 * - SIOPv2 §6 "Self-Issued ID Token"
 * - SIOPv2 §4.1 "Subject Syntax Types"
 * - OID4VP 1.0 §"Combining this specification with SIOPv2"
 */
object SelfIssuedIdTokenBuilder {

    private val log = KotlinLogging.logger { }

    /**
     * Creates a signed Self-Issued ID Token for the given authorization request.
     *
     * @param authorizationRequest the original authorization request
     * @param holderKey the wallet's holder key (used for signing and as subject)
     * @param holderDid optional DID; when non-null, used as `sub`/`iss` instead of JWK thumbprint
     * @return the compact-serialized signed ID Token (JWS)
     */
    suspend fun build(
        authorizationRequest: AuthorizationRequest,
        holderKey: Key,
        holderDid: String?
    ): String {
        val publicKey = holderKey.getPublicKey()

        // Determine Subject Syntax Type:
        // - DID: `sub` = DID, no `sub_jwk`
        // - JWK Thumbprint: `sub` = "urn:ietf:params:oauth:jwk-thumbprint:sha-256:<thumbprint>",
        //                   `sub_jwk` = public key JWK
        val useDidSubject = !holderDid.isNullOrEmpty() && holderDid.startsWith("did:")
        val sub: String = when {
            useDidSubject -> holderDid
            else -> "urn:ietf:params:oauth:jwk-thumbprint:sha-256:${publicKey.getThumbprint()}" // JWK Thumbprint Subject Syntax Type URI per RFC 7638 + SIOPv2 §4.1
        }
        val subJwk = if (!useDidSubject) publicKey.exportJWKObject() else null

        val now = Clock.System.now()
        val exp = now + 5.minutes

        val headers = buildJsonObject {
            put("typ", "JWT")
            put("alg", holderKey.keyType.jwsAlg)
            if (useDidSubject) {
                // Include kid for DID-based subject so verifier can find the correct key
                put("kid", holderDid)
            }
        }

        val payload = buildJsonObject {
            // iss = sub per SIOPv2 §6: "this claim MUST be set to the value of the sub claim"
            put("iss", sub)
            put("sub", sub)
            put("aud", JsonPrimitive(authorizationRequest.clientId))
            put("nonce", JsonPrimitive(authorizationRequest.nonce))
            put("iat", JsonPrimitive(now.epochSeconds))
            put("exp", JsonPrimitive(exp.epochSeconds))

            // sub_jwk: REQUIRED when Subject Syntax Type is JWK Thumbprint (SIOPv2 §6)
            if (subJwk != null) {
                put("sub_jwk", subJwk)
            }
        }

        log.trace { "Building Self-Issued ID Token: sub=$sub, aud=${authorizationRequest.clientId}" }

        return holderKey.signJws(
            plaintext = payload.toString().encodeToByteArray(),
            headers = headers
        )
    }
}
