@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet.presentation

import id.walt.crypto.keys.Key
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.supportsJwsAlgorithm
import id.walt.crypto2.keys.toPublicJwk
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.did.dids.DidService
import id.waltid.openid4vp.wallet.WalletCrypto2KeyAdapter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
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

    @Deprecated("Use the Crypto2Key overload")
    suspend fun build(
        authorizationRequest: AuthorizationRequest,
        holderKey: Key,
        holderDid: String?,
    ): String = buildWithKey(authorizationRequest, holderKey, holderDid, null)

    suspend fun build(
        authorizationRequest: AuthorizationRequest,
        holderKey: Crypto2Key,
        holderDid: String?,
    ): String = buildWithKey(authorizationRequest, null, holderDid, holderKey)

    /**
     * Creates a signed Self-Issued ID Token for the given authorization request.
     *
     * @param authorizationRequest the original authorization request
     * @param holderKey the wallet's holder key (used for signing and as subject)
     * @param holderDid optional DID; when non-null, used as `sub`/`iss` instead of JWK thumbprint
     * @return the compact-serialized signed ID Token (JWS)
     */
    @Deprecated("Use the Crypto2Key overload")
    suspend fun build(
        authorizationRequest: AuthorizationRequest,
        holderKey: Key,
        holderDid: String?,
        holderCrypto2Key: Crypto2Key?,
    ): String = buildWithKey(authorizationRequest, holderKey, holderDid, holderCrypto2Key)

    private suspend fun buildWithKey(
        authorizationRequest: AuthorizationRequest,
        holderKey: Key?,
        holderDid: String?,
        holderCrypto2Key: Crypto2Key?,
    ): String {
        val crypto2Key = holderCrypto2Key ?: holderKey?.let { WalletCrypto2KeyAdapter.signingKey(it) }
        require(crypto2Key != null || holderKey != null) { "A holder signing key is required" }
        val publicJwk = crypto2Key?.let { key ->
            requireNotNull(key.capabilities.publicKeyExporter) { "Holder key does not export public material" }
                .exportPublicKey()
                .toPublicJwk(key.spec)
        }
        val publicKey = if (crypto2Key == null) requireNotNull(holderKey).getPublicKey() else null

        // Determine Subject Syntax Type:
        // - DID: `sub` = DID, no `sub_jwk`
        // - JWK Thumbprint: `sub` = "urn:ietf:params:oauth:jwk-thumbprint:sha-256:<thumbprint>",
        //                   `sub_jwk` = public key JWK
        val clientMetadata = requireNotNull(authorizationRequest.clientMetadata) {
            "Verifier metadata is required for a Self-Issued ID Token"
        }
        val supportedSubjectSyntaxTypes = requireNotNull(
            clientMetadata.subjectSyntaxTypesSupported?.toSet()
        ) { "Verifier metadata must contain subject_syntax_types_supported for a Self-Issued ID Token" }
        require(supportedSubjectSyntaxTypes.isNotEmpty()) {
            "Verifier subject_syntax_types_supported must not be empty"
        }
        val didSyntaxType = holderDid?.split(':')?.take(2)?.joinToString(":")
        val useDidSubject = !holderDid.isNullOrEmpty() && holderDid.startsWith("did:") &&
            ("did" in supportedSubjectSyntaxTypes || didSyntaxType in supportedSubjectSyntaxTypes)
        require(useDidSubject || JWK_THUMBPRINT_SUBJECT_SYNTAX_TYPE in supportedSubjectSyntaxTypes) {
            "Verifier and holder have no compatible subject syntax type"
        }
        val sub: String = when {
            useDidSubject -> holderDid
            else -> "urn:ietf:params:oauth:jwk-thumbprint:sha-256:${
                publicJwk?.let { Jwk.sha256Thumbprint(it) } ?: requireNotNull(publicKey).getThumbprint()
            }" // JWK Thumbprint Subject Syntax Type URI per RFC 7638 + SIOPv2 §4.1
        }
        val subJwk = if (!useDidSubject) {
            publicJwk?.let(Jwk::parse) ?: requireNotNull(publicKey).exportJWKObject()
        } else null

        val now = Clock.System.now()
        val exp = now + 5.minutes
        val signingAlgorithm = JwsAlgorithm.parse(clientMetadata.idTokenSignedResponseAlg ?: "RS256")
        if (crypto2Key != null) {
            require(crypto2Key.spec.supportsJwsAlgorithm(signingAlgorithm) &&
                crypto2Key.capabilities.supportsSignatureAlgorithm(signingAlgorithm.toSignatureAlgorithm())) {
                "Verifier requires ID token algorithm ${signingAlgorithm.identifier}, but holder key uses ${crypto2Key.spec}"
            }
        } else {
            val legacyKey = requireNotNull(holderKey)
            require(signingAlgorithm.identifier == legacyKey.keyType.jwsAlg) {
                "Verifier requires ID token algorithm ${signingAlgorithm.identifier}, but holder key uses ${legacyKey.keyType.jwsAlg}"
            }
        }

        val headers = buildJsonObject {
            put("typ", "JWT")
            put("alg", signingAlgorithm.identifier)
            if (useDidSubject) {
                // Include kid for DID-based subject so verifier can find the correct key
                val keyId = crypto2Key?.id?.value ?: requireNotNull(holderKey).getKeyId()
                put("kid", DidService.resolveAuthenticationMethodId(holderDid, keyId))
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

        return if (crypto2Key != null) {
            CompactJws.sign(
                payload = Json.encodeToString(payload).encodeToByteArray(),
                key = crypto2Key,
                algorithm = signingAlgorithm,
                protectedHeader = headers,
            )
        } else requireNotNull(holderKey).signJws(payload.toString().encodeToByteArray(), headers)
    }

    private const val JWK_THUMBPRINT_SUBJECT_SYNTAX_TYPE = "urn:ietf:params:oauth:jwk-thumbprint"

}
