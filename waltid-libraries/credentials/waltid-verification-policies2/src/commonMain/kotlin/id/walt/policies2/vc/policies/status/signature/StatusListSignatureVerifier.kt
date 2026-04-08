package id.walt.policies2.vc.policies.status.signature

import id.walt.cose.CoseSign1
import id.walt.cose.toCoseVerifier
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class StatusListSignatureVerifier {
    
    private val logger = KotlinLogging.logger {}
    
    /**
     * Verifies a JWT status list credential signature.
     * 
     * @param jwt The JWT string to verify
     * @return Result containing the verified payload as JsonObject, or failure with exception
     */
    suspend fun verifyJwt(jwt: String): Result<JsonObject> = runCatching {
        logger.debug { "Verifying JWT status list signature" }
        
        val jwsParts = jwt.decodeJws()
        val header = jwsParts.header
        
        val key = resolveKeyFromJwtHeader(header).getOrElse { 
            throw KeyResolutionFailedException("Failed to resolve signing key from JWT header: ${it.message}")
        }
        
        logger.debug { "Resolved key for JWT verification: ${key.keyType}" }
        
        key.verifyJws(jwt).getOrElse {
            throw SignatureInvalidException("JWT signature verification failed: ${it.message}")
        }
        
        jwsParts.payload
    }
    
    /**
     * Verifies a CWT status list credential signature.
     * 
     * @param cwtBytes The CWT as raw bytes (not hex encoded)
     * @return Result containing the verified payload as ByteArray, or failure with exception
     */
    suspend fun verifyCwt(cwtBytes: ByteArray): Result<ByteArray> = runCatching {
        logger.debug { "Verifying CWT status list signature" }
        
        val coseSign1 = CoseSign1.fromTagged(cwtBytes)
        
        val key = resolveKeyFromCoseHeaders(coseSign1).getOrElse {
            throw KeyResolutionFailedException("Failed to resolve signing key from CWT headers: ${it.message}")
        }
        
        logger.debug { "Resolved key for CWT verification: ${key.keyType}" }
        
        val verifier = key.toCoseVerifier()
        val isValid = coseSign1.verify(verifier)
        
        if (!isValid) {
            throw SignatureInvalidException("CWT signature verification failed")
        }
        
        coseSign1.payload ?: throw SignatureInvalidException("CWT has no payload")
    }
    
    /**
     * Verifies a CWT status list credential signature from hex string.
     * 
     * @param cwtHex The CWT as hex-encoded string
     * @return Result containing the verified payload as ByteArray, or failure with exception
     */
    suspend fun verifyCwtFromHex(cwtHex: String): Result<ByteArray> = runCatching {
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
    private suspend fun resolveKeyFromJwtHeader(header: JsonObject): Result<Key> = runCatching {
        // Try x5c first (X.509 certificate chain)
        header["x5c"]?.jsonArray?.let { x5cArray ->
            return@runCatching resolveKeyFromX5c(x5cArray)
        }
        
        // Try kid with DID URL
        header["kid"]?.jsonPrimitive?.content?.let { kid ->
            if (DidUtils.isDidUrl(kid)) {
                logger.debug { "Resolving key from DID: $kid" }
                val didWithoutFragment = kid.substringBefore("#")
                return@runCatching DidService.resolveToKey(didWithoutFragment).getOrElse {
                    throw KeyResolutionFailedException("Failed to resolve DID $kid: ${it.message}")
                }
            }
        }
        
        // Try jwk header
        header["jwk"]?.let { jwkElement ->
            logger.debug { "Importing key from jwk header" }
            return@runCatching JWKKey.importJWK(jwkElement.toString()).getOrElse {
                throw KeyResolutionFailedException("Failed to import JWK from header: ${it.message}")
            }
        }
        
        throw KeyResolutionFailedException("No resolvable key information in JWT header (expected x5c, kid with DID, or jwk)")
    }
    
    /**
     * Resolves the signing key from COSE headers.
     * Resolution order:
     * 1. x5chain header - import from certificate chain
     * 2. kid header with DID URL - resolve via DID service
     */
    private suspend fun resolveKeyFromCoseHeaders(coseSign1: CoseSign1): Result<Key> = runCatching {
        val unprotectedHeaders = coseSign1.unprotected
        
        // Try x5chain first (X.509 certificate chain in COSE)
        unprotectedHeaders.x5chain?.let { x5chain ->
            if (x5chain.isNotEmpty()) {
                logger.debug { "Resolving key from x5chain header" }
                val leafCertBytes = x5chain.first().rawBytes
                return@runCatching JWKKey.importFromDerCertificate(leafCertBytes).getOrElse {
                    throw KeyResolutionFailedException("Failed to import key from x5chain: ${it.message}")
                }
            }
        }
        
        // Try kid with DID URL
        unprotectedHeaders.kid?.let { kidBytes ->
            val kid = kidBytes.decodeToString()
            if (DidUtils.isDidUrl(kid)) {
                logger.debug { "Resolving key from DID in CWT kid: $kid" }
                val didWithoutFragment = kid.substringBefore("#")
                return@runCatching DidService.resolveToKey(didWithoutFragment).getOrElse {
                    throw KeyResolutionFailedException("Failed to resolve DID $kid: ${it.message}")
                }
            }
        }
        
        throw KeyResolutionFailedException("No resolvable key information in CWT headers (expected x5chain or kid with DID)")
    }
    
    /**
     * Imports a key from an x5c certificate chain.
     * Uses the leaf certificate (first in the chain).
     */
    private suspend fun resolveKeyFromX5c(x5cArray: JsonArray): Key {
        if (x5cArray.isEmpty()) {
            throw KeyResolutionFailedException("x5c array is empty")
        }
        
        val leafCertBase64 = x5cArray.first().jsonPrimitive.content
        logger.debug { "Importing key from x5c leaf certificate" }
        
        return JWKKey.importDERorPEM(leafCertBase64).getOrElse {
            throw KeyResolutionFailedException("Failed to import key from x5c certificate: ${it.message}")
        }
    }
    
    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Hex string must have even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
