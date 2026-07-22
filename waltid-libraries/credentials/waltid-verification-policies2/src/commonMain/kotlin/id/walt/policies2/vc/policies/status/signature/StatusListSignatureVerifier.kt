package id.walt.policies2.vc.policies.status.signature

import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.Cose
import id.walt.cose.coseCompliantCbor
import id.walt.cose.verify
import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.credentials.keyresolver.ResolvedJwtVerificationKey
import id.walt.credentials.formats.DigitalCredential
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.did.dids.DidUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

data class VerifiedStatusList<T>(
    val payload: T,
    val signer: ResolvedJwtVerificationKey,
)

data class StatusListSignerAuthorizationRequest(
    val referencedCredential: DigitalCredential,
    val statusListUri: String,
    val signer: ResolvedJwtVerificationKey,
)

fun interface StatusListSignerAuthorizer {
    suspend fun authorize(request: StatusListSignerAuthorizationRequest): Boolean
}

@OptIn(ExperimentalSerializationApi::class)
class StatusListSignatureVerifier(
    allowUntrustedInlineJwk: Boolean = false,
    private val keyResolver: Crypto2JwtKeyResolver = Crypto2JwtKeyResolver(
        allowInlineJwk = allowUntrustedInlineJwk,
    ),
    private val allowedJwtAlgorithms: Set<JwsAlgorithm> = JwsAlgorithm.entries.toSet(),
    private val allowedCoseAlgorithms: Set<Int> = DEFAULT_COSE_ALGORITHMS,
) {
    
    private val logger = KotlinLogging.logger {}
    
    /**
     * Verifies a JWT status list credential signature.
     * 
     * @param jwt The JWT string to verify
     * @return Result containing the verified payload as JsonObject, or failure with exception
     */
    suspend fun verifyJwt(jwt: String): Result<JsonObject> =
        verifyJwtWithSigner(jwt).map(VerifiedStatusList<JsonObject>::payload)

    suspend fun verifyJwtWithSigner(jwt: String): Result<VerifiedStatusList<JsonObject>> = resultOfSuspend {
        logger.debug { "Verifying JWT status list signature" }

        val decoded = try {
            CompactJws.decodeUnverified(jwt)
        } catch (cause: Throwable) {
            throw SignatureInvalidException("Invalid JWT structure: ${cause.message}")
        }
        val payload = try {
            Json.parseToJsonElement(decoded.payload.decodeToString()) as? JsonObject
                ?: throw IllegalArgumentException("JWT payload must be a JSON object")
        } catch (cause: Throwable) {
            throw SignatureInvalidException("Invalid JWT payload: ${cause.message}")
        }
        val resolved = resolveKeyFromJwtHeader(decoded.protectedHeader, payload)
        logger.debug { "Resolved crypto2 key for JWT verification: ${resolved.key.spec}" }
        try {
            CompactJws.verify(jwt, resolved.key, allowedJwtAlgorithms)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            throw SignatureInvalidException("JWT signature verification failed: ${cause.message}")
        }
        VerifiedStatusList(payload, resolved)
    }
    
    /**
     * Verifies a CWT status list credential signature.
     * 
     * @param cwtBytes The CWT as raw bytes (not hex encoded)
     * @return Result containing the verified payload as ByteArray, or failure with exception
     */
    suspend fun verifyCwt(cwtBytes: ByteArray): Result<ByteArray> =
        verifyCwtWithSigner(cwtBytes).map(VerifiedStatusList<ByteArray>::payload)

    suspend fun verifyCwtWithSigner(cwtBytes: ByteArray): Result<VerifiedStatusList<ByteArray>> = resultOfSuspend {
        logger.debug { "Verifying CWT status list signature" }
        
        val coseSign1 = try {
            CoseSign1.fromTagged(cwtBytes)
        } catch (cause: Throwable) {
            throw SignatureInvalidException("Invalid CWT structure: ${cause.message}")
        }
        
        val resolved = resolveKeyFromCoseHeaders(coseSign1)
        logger.debug { "Resolved crypto2 key for CWT verification: ${resolved.key.spec}" }
        val isValid = coseSign1.verify(resolved.key, allowedCoseAlgorithms)
        
        if (!isValid) {
            throw SignatureInvalidException("CWT signature verification failed")
        }
        
        VerifiedStatusList(
            coseSign1.payload ?: throw SignatureInvalidException("CWT has no payload"),
            resolved,
        )
    }
    
    /**
     * Verifies a CWT status list credential signature from hex string.
     * 
     * @param cwtHex The CWT as hex-encoded string
     * @return Result containing the verified payload as ByteArray, or failure with exception
     */
    suspend fun verifyCwtFromHex(cwtHex: String): Result<ByteArray> = resultOfSuspend {
        val cwtBytes = cwtHex.hexToByteArray()
        verifyCwt(cwtBytes).getOrThrow()
    }
    
    /**
     * Resolves the signing key from JWT header.
     * Resolution order:
     * 1. x5c header - import from certificate chain
     * 2. kid header with DID URL - resolve via DID service
     * 3. jwk header - import directly
     */
    private suspend fun resolveKeyFromJwtHeader(
        header: JsonObject,
        payload: JsonObject,
    ): ResolvedJwtVerificationKey {
        val keyId = header["kid"]?.jsonPrimitive?.contentOrNull
        val payloadForResolution = if (
            payload["iss"] == null && payload["issuer"] == null && keyId != null && DidUtils.isDidUrl(keyId)
        ) {
            JsonObject(payload + ("iss" to JsonPrimitive(keyId.substringBefore('#'))))
        } else payload
        return keyResolver.resolveFromJwt(header, payloadForResolution) // see Crypto2JwtKeyResolver
        ?: throw KeyResolutionFailedException(
            "No resolvable key information in JWT header (expected trusted issuer, x5c, or explicitly enabled jwk)"
        )
    }
    
    /**
     * Resolves the signing key from COSE headers.
     * 
     * Resolution order:
     * 1. x5chain header in PROTECTED headers (ISO 18013-5 Second Edition §12.3.6.3) - import from certificate chain
     * 2. kid header with DID URL in PROTECTED headers - resolve via DID service
     * 
     * Note: ISO 18013-5 Second Edition mandates x5chain in protected headers.
     */
    private suspend fun resolveKeyFromCoseHeaders(coseSign1: CoseSign1): ResolvedJwtVerificationKey {
        // Decode protected headers
        val protectedHeaders = coseCompliantCbor.decodeFromByteArray<CoseHeaders>(coseSign1.protected)
        
        // Try x5chain in PROTECTED headers (ISO 18013-5 Second Edition compliant)
        protectedHeaders.x5chain?.let { x5chain ->
            if (x5chain.isNotEmpty()) {
                logger.debug { "Resolving key from x5chain in PROTECTED headers (ISO 18013-5 compliant)" }
                return resolveKeyFromX5c(
                    JsonArray(x5chain.map { JsonPrimitive(Base64.Default.encode(it.rawBytes)) })
                )
            }
        }
        
        // Try kid with DID URL in PROTECTED headers
        protectedHeaders.kid?.let { kidBytes ->
            val kid = kidBytes.decodeToString()
            if (DidUtils.isDidUrl(kid)) {
                logger.debug { "Resolving key from DID in CWT kid (protected): $kid" }
                val didWithoutFragment = kid.substringBefore("#")
                return keyResolver.resolveFromJwt(
                    jwtHeader = buildJsonObject { put("kid", kid) },
                    jwtPayload = buildJsonObject { put("iss", didWithoutFragment) },
                ) ?: throw KeyResolutionFailedException("Failed to resolve DID $kid")
            }
        }
        
        throw KeyResolutionFailedException("No resolvable key information in CWT protected headers (expected x5chain or kid with DID)")
    }
    
    /**
     * Imports a key from an x5c certificate chain.
     * Uses the leaf certificate (first in the chain).
     */
    private suspend fun resolveKeyFromX5c(x5cArray: JsonArray): ResolvedJwtVerificationKey {
        if (x5cArray.isEmpty()) {
            throw KeyResolutionFailedException("x5c array is empty")
        }
        
        logger.debug { "Importing key from x5c leaf certificate" }
        return keyResolver.resolveFromJwt(
            jwtHeader = buildJsonObject { put("x5c", x5cArray) },
            jwtPayload = buildJsonObject { },
        ) ?: throw KeyResolutionFailedException("Failed to import key from x5c certificate")
    }
    
    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Hex string must have even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    companion object {
        private val DEFAULT_COSE_ALGORITHMS = setOf(
            Cose.Algorithm.ES256,
            Cose.Algorithm.ES384,
            Cose.Algorithm.ES512,
            Cose.Algorithm.EdDSA,
            Cose.Algorithm.PS256,
            Cose.Algorithm.PS384,
            Cose.Algorithm.PS512,
            Cose.Algorithm.RS256,
            Cose.Algorithm.RS384,
            Cose.Algorithm.RS512,
        )
    }
}

private suspend fun <T> resultOfSuspend(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cause: CancellationException) {
    throw cause
} catch (cause: Throwable) {
    Result.failure(cause)
}
