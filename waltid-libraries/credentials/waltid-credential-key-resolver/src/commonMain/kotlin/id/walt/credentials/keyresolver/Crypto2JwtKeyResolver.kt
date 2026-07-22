package id.walt.credentials.keyresolver

import id.walt.credentials.keyresolver.resolvers.WellKnownKeyResolver
import id.walt.credentials.keyresolver.resolvers.X5CKeyResolver
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import id.walt.did.dids.resolver.Crypto2DidKeyResolver
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class JwtKeyResolutionSource {
    DID,
    X5C,
    WELL_KNOWN,
    INLINE_JWK,
}

data class ResolvedJwtVerificationKey(
    val key: Key,
    val source: JwtKeyResolutionSource,
    val signerIdentifier: String?,
    val keyId: String?,
    val certificateChain: List<String> = emptyList(),
)

fun interface Crypto2JwtVerificationKeyResolver {
    suspend fun resolveFromJwt(jwtHeader: JsonObject?, jwtPayload: JsonObject): ResolvedJwtVerificationKey?
}

class Crypto2JwtKeyResolver(
    private val didResolver: Crypto2DidKeyResolver = Crypto2DidKeyResolver { did ->
        DidService.resolveToCrypto2Keys(did).getOrThrow()
    },
    private val runtime: CryptoRuntime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider())),
    private val allowInlineJwk: Boolean = false,
) : Crypto2JwtVerificationKeyResolver {
    override suspend fun resolveFromJwt(jwtHeader: JsonObject?, jwtPayload: JsonObject): ResolvedJwtVerificationKey? {
        val signerIdentifier = extractSignerIdentifier(jwtPayload)
        val kid = jwtHeader?.get("kid")?.jsonPrimitive?.contentOrNull
        return try {
            when {
                // 1. DID resolution is authoritative for DID issuer identifiers.
                signerIdentifier != null && DidUtils.isDidUrl(signerIdentifier) -> {
                    val key = resolveFromDid(signerIdentifier, kid)
                    ResolvedJwtVerificationKey(key, JwtKeyResolutionSource.DID, signerIdentifier, kid)
                }
                // 2. Inline X.509 certificate chain.
                jwtHeader?.get("x5c") is JsonArray ->
                    resolveX5c(jwtHeader["x5c"] as JsonArray, signerIdentifier, kid)
                // 3. Inline JWK is opt-in because it does not establish issuer trust by itself.
                allowInlineJwk && jwtHeader?.get("jwk") is JsonObject -> {
                    val jwk = jwtHeader["jwk"] as JsonObject
                    ResolvedJwtVerificationKey(
                        key = restore(
                            EncodedKey.Jwk(
                                BinaryData(Json.encodeToString(jwk).encodeToByteArray()),
                                privateMaterial = false,
                            )
                        ),
                        source = JwtKeyResolutionSource.INLINE_JWK,
                        signerIdentifier = signerIdentifier,
                        keyId = kid,
                    )
                }
                // 4. HTTPS issuer metadata and JWKS.
                signerIdentifier?.startsWith("https://") == true -> {
                    ResolvedJwtVerificationKey(
                        key = restore(WellKnownKeyResolver.resolveJwkFromWellKnown(signerIdentifier, jwtHeader)),
                        source = JwtKeyResolutionSource.WELL_KNOWN,
                        signerIdentifier = signerIdentifier,
                        keyId = kid,
                    )
                }
                else -> null
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            log.debug { "Could not resolve crypto2 JWT signer key: ${cause.message}" }
            null
        }
    }

    suspend fun resolveFromDid(did: String, keyId: String? = null): Key =
        selectDidKey(didResolver.resolveToKeys(did), did, keyId)

    private suspend fun resolveX5c(
        x5c: JsonArray,
        signerIdentifier: String?,
        kid: String?,
    ): ResolvedJwtVerificationKey {
        val certificateChain = x5c.map { it.jsonPrimitive.content }
        return ResolvedJwtVerificationKey(
            key = restore(X5CKeyResolver.resolveJwkFromX5c(x5c)),
            source = JwtKeyResolutionSource.X5C,
            signerIdentifier = signerIdentifier,
            keyId = kid,
            certificateChain = certificateChain,
        )
    }

    private suspend fun restore(jwk: EncodedKey.Jwk): Key {
        require(!jwk.privateMaterial) { "Resolved verification JWK must not contain private material" }
        val keyId = Jwk.metadata(jwk).keyId ?: Jwk.sha256Thumbprint(jwk)
        val stored = jwk.toStoredSoftwareKey(KeyId(keyId), setOf(KeyUsage.VERIFY))
        return runtime.restore(stored)
    }

    private fun selectDidKey(keys: Set<Key>, did: String, kid: String?): Key {
        require(keys.isNotEmpty()) { "No verification keys resolved for DID: $did" }
        if (kid == null) {
            require(keys.size == 1) { "JWT with multiple DID verification keys must include kid" }
            return keys.single()
        }
        if (keys.size == 1 && kid == did && (did.startsWith("did:key:") || did.startsWith("did:jwk:"))) {
            return keys.single()
        }
        val kidCandidates = idCandidates(kid)
        val matches = keys.filter { key -> idCandidates(key.id.value).any(kidCandidates::contains) }
        require(matches.size <= 1) { "Multiple DID verification keys match kid: $kid" }
        return matches.singleOrNull() ?: throw NoSuchElementException("No DID verification key matches kid: $kid")
    }

    private fun idCandidates(id: String): Set<String> =
        setOf(id, id.substringAfter('#', missingDelimiterValue = ""))
            .filter(String::isNotBlank)
            .toSet()

    private fun extractSignerIdentifier(payload: JsonObject): String? =
        (payload["iss"] ?: payload["issuer"])?.let { element ->
            when (element) {
                is JsonNull -> null
                is JsonPrimitive -> element.contentOrNull
                is JsonObject -> element["id"]?.jsonPrimitive?.contentOrNull
                else -> null
            }
        }

    companion object {
        private val log = KotlinLogging.logger { }
    }
}
