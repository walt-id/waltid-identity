package id.walt.wallet2.handlers

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.selectJwsAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.wallet2.data.WalletKeyStoreEntry
import id.waltid.openid4vci.wallet.dpop.DPoPProofBuilder
import id.waltid.openid4vci.wallet.dpop.USE_DPOP_NONCE
import io.github.oshai.kotlinlogging.KotlinLogging
import id.walt.crypto2.keys.Key as Crypto2Key
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Shared DPoP (RFC 9449) and crypto2 key-resolution helpers for the two OpenID4VCI issuance
 * implementations in this package.
 *
 * [WalletIssuanceSessionService] (session-based, driven by the mobile wallet facade) and
 * [WalletIssuanceHandler] (single-call, driven by the wallet server routes) need identical proof
 * construction, token-type handling and nonce negotiation. Previously only the session service
 * implemented DPoP, so server-hosted wallets could not talk to a DPoP-constrained issuer at all.
 *
 * Only the protocol-level pieces live here. Proof construction is exposed as a plain function
 * rather than a ready-made [id.waltid.openid4vci.wallet.token.DPoPProofFactory] because the two
 * callers map failures onto different error taxonomies (`IssuanceStageException` versus the
 * handler's web exceptions), and that mapping has to happen inside the factory lambda.
 */

private val log = KotlinLogging.logger {}
private val crypto2Runtime = CryptoRuntime(defaultSoftwareKeyProviders())

/** DPoP signing algorithms the authorization server advertises, or null when it advertises none. */
internal fun AuthorizationServerMetadata.dpopSigningAlgorithms(): Set<String>? =
    dpopSigningAlgValuesSupported?.takeIf { it.isNotEmpty() }

/**
 * Advertised DPoP algorithms that [keyMaterial] can actually sign, or null when DPoP must be skipped.
 *
 * DPoP is **optional for the wallet**: an authorization server that advertises it still issues a
 * plain Bearer token when no proof accompanies the request. A key that cannot produce any advertised
 * algorithm therefore means "do not attempt DPoP", not "fail issuance" - treating it as an error
 * breaks every wallet whose key type the server happens not to list, for example an Ed25519
 * `did:key` wallet talking to a server advertising only `ES256`.
 *
 * [WalletIssuanceSessionService] deliberately does **not** use this and fails instead; see its
 * `dpopAlgorithms()` for why the two paths differ.
 */
internal suspend fun usableDpopAlgorithms(
    asMetadata: AuthorizationServerMetadata,
    keyMaterial: WalletKeyStoreEntry,
): Set<String>? {
    val advertised = asMetadata.dpopSigningAlgorithms() ?: return null

    val key = try {
        keyMaterial.requireCrypto2SigningKey()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        log.debug(error) { "Skipping DPoP: key '${keyMaterial.keyId}' has no crypto2 signing representation" }
        return null
    }

    // selectJwsAlgorithm throws rather than returning null, so this is the only available probe.
    return try {
        key.selectJwsAlgorithm(advertised)
        advertised
    } catch (_: IllegalArgumentException) {
        log.debug {
            "Skipping DPoP: key '${keyMaterial.keyId}' cannot sign any advertised algorithm ($advertised); " +
                "requesting a Bearer token instead"
        }
        null
    }
}

/**
 * Builds one DPoP proof.
 *
 * [accessToken] is supplied for protected-resource requests (credential, deferred and notification
 * endpoints), where it is hashed into the `ath` claim, and omitted for token requests.
 */
internal suspend fun buildDpopProof(
    keyMaterial: WalletKeyStoreEntry,
    algorithms: Set<String>,
    endpoint: String,
    accessToken: String? = null,
    nonce: String? = null,
): String = DPoPProofBuilder().buildProof(
    key = keyMaterial.requireCrypto2SigningKey(),
    httpMethod = "POST",
    targetUri = endpoint,
    accessToken = accessToken,
    nonce = nonce,
    supportedAlgorithms = algorithms,
)

/**
 * Which DPoP algorithms apply to requests carrying an issued token.
 *
 * A `DPoP` token type without advertised algorithms is a server contradiction, and an unrecognised
 * token type must not be silently downgraded to `Bearer` - that would strip sender constraining
 * from every subsequent request.
 */
internal fun dpopAlgorithmsForToken(
    tokenType: String,
    advertisedAlgorithms: Set<String>?,
): Set<String>? = when {
    tokenType.equals("DPoP", ignoreCase = true) -> requireNotNull(advertisedAlgorithms) {
        "Authorization server returned a DPoP token, but no usable DPoP signing algorithm was " +
            "established for this request"
    }

    tokenType.equals("Bearer", ignoreCase = true) -> null
    else -> error("Authorization server returned an unsupported token type")
}

/** `Authorization` scheme matching the issued token type; anything not DPoP is treated as Bearer. */
internal fun authorizationScheme(tokenType: String?): String =
    if (tokenType.equals("DPoP", ignoreCase = true)) "DPoP" else "Bearer"

/**
 * What a protected-resource request needs in order to present a DPoP-bound access token.
 *
 * Its presence alone means the token is DPoP-typed - [dpopAlgorithmsForToken] already rejected the
 * contradictory combinations - so callers do not need to carry the token type separately.
 */
internal data class DpopRequestContext(
    val algorithms: Set<String>,
    val keyMaterial: WalletKeyStoreEntry,
)

/**
 * OAuth error code of a failed response.
 *
 * RFC 9449 lets a server demand a fresh DPoP nonce either through `WWW-Authenticate` or through
 * the error in the response body, so both are checked.
 */
internal suspend fun HttpResponse.oauthErrorCode(): String? {
    if (status.isSuccess()) return null
    if (headers[HttpHeaders.WWWAuthenticate]?.contains(USE_DPOP_NONCE, ignoreCase = true) == true) {
        return USE_DPOP_NONCE
    }
    return try {
        Json.parseToJsonElement(bodyAsText()).jsonObject["error"]?.jsonPrimitive?.contentOrNull
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}

/**
 * Resolves the crypto2 signing key for a wallet key entry, lazily migrating a legacy JWK when the
 * entry only holds one.
 */
internal suspend fun WalletKeyStoreEntry.requireCrypto2SigningKey(): Crypto2Key =
    crypto2Key
        ?: legacyKey?.let { migrateLocalJwk(it) }?.let { crypto2Runtime.restore(it) }
        ?: error("Key '$keyId' has no usable crypto2 signing representation")

/**
 * Migrates a legacy local JWK to a crypto2 software key.
 *
 * secp256k1 is excluded because crypto2's software providers cannot represent it.
 *
 * The two copies this replaces had silently drifted apart: the session service granted
 * `SIGN, VERIFY` while the handler granted only `SIGN`. The superset is kept - a signing key can
 * always verify, and the narrower set made migrated keys unusable for verification.
 */
internal suspend fun migrateLocalJwk(key: Key) =
    (key as? JWKKey)?.takeUnless { it.keyType == KeyType.secp256k1 }?.let {
        val jwk = it.exportJWKObject()
        EncodedKey.Jwk(
            BinaryData(Json.encodeToString(jwk).encodeToByteArray()),
            privateMaterial = Jwk.containsPrivateMaterial(jwk),
        ).toStoredSoftwareKey(KeyId(it.getKeyId()), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
    }
