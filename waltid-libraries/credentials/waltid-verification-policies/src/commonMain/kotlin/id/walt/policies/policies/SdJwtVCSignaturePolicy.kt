package id.walt.policies.policies

import id.walt.crypto.exceptions.VerificationException
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import id.walt.policies.JwtVerificationPolicy
import id.walt.sdjwt.JWTCryptoProvider
import id.walt.sdjwt.SDJwtVC
import id.walt.w3c.utils.VCFormat
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

expect object JWTCryptoProviderManager {
    fun getDefaultJWTCryptoProvider(keys: Map<String, Key>): JWTCryptoProvider
}

class SdJwtVCSignaturePolicy() : JwtVerificationPolicy() {
    override val name = "signature_sd-jwt-vc"
    override val description =
        "Checks a SD-JWT-VC credential by verifying its cryptographic signature using the key referenced by the DID in `iss`."
    override val supportedVCFormats = setOf(VCFormat.sd_jwt_vc)

    private suspend fun resolveIssuerKeysFromSdJwt(sdJwt: SDJwtVC): Set<Key> {
        val kid = sdJwt.issuer ?: randomUUIDString()
        return if (DidUtils.isDidUrl(kid)) {
            DidService.resolveToKeys(kid).getOrThrow()
        } else {
            val issuerEncodedCert = sdJwt.header["x5c"]?.jsonArray?.first()?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("x5c header parameter is missing or empty.")
            val key = JWKKey.importPEM(
                pem = JWKKey.wrapAsPem(issuerEncodedCert.decodeFromBase64()),
            ).getOrThrow().let { JWKKey(it.exportJWK(), kid) }
            setOf(key)
        }
    }

    @OptIn(ExperimentalJsExport::class)
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(credential: String, args: Any?, context: Map<String, Any>): Result<JsonElement> {
        return runCatching {
            val sdJwtVC = SDJwtVC.parse(credential)

            if (!sdJwtVC.isPresentation) {
                // Get all possible issuer keys from the DID document
                val issuerKeys = resolveIssuerKeysFromSdJwt(sdJwtVC)

                if (issuerKeys.isEmpty()) {
                    throw VerificationException("No issuer keys found in the DID document")
                }

                // Try to verify with each key
                // Return the first successful result or the last error
                issuerKeys.firstSuccessOrThrow(
                    { failures ->
                        VerificationException(
                            message = "Verification failed with all keys from the DID document",
                            cause = failures.lastOrNull()
                        )
                    }) { it.verifyJws(credential) }.getOrThrow()

            } else {
                // For presentations, get all possible issuer keys
                val issuerKeys = resolveIssuerKeysFromSdJwt(sdJwtVC)
                val issuerKey =
                    issuerKeys.firstOrNull() ?: throw VerificationException("No issuer keys found in the DID document")

                val holderKey = JWKKey.importJWK(sdJwtVC.holderKeyJWK.toString()).getOrThrow()

                // Create a map of all possible issuer keys by their key IDs
                val keyMap = issuerKeys.associateBy { it.getKeyId() }.toMutableMap()

                // Add the default key ID mapping
                keyMap[sdJwtVC.keyID ?: issuerKey.getKeyId()] = issuerKey
                // Add the holder key
                keyMap[holderKey.getKeyId()] = holderKey

                val verificationResult = sdJwtVC.verifyVC(
                    JWTCryptoProviderManager.getDefaultJWTCryptoProvider(keyMap),
                    requiresHolderKeyBinding = true,
                    context["clientId"]?.toString(),
                    context["challenge"]?.toString()
                )

                if (!verificationResult.verified) {
                    throw VerificationException("SD-JWT verification failed: ${verificationResult.message}")
                }

                sdJwtVC.undisclosedPayload
            }
        }
    }

    private inline fun <T, R> Collection<T>.firstSuccessOrThrow(
        exceptionProvider: (List<Throwable>) -> Exception, block: (T) -> R
    ): R {
        val failures = mutableListOf<Throwable>()
        return firstNotNullOfOrNull { item ->
            runCatching { block(item) }.fold(onSuccess = { it }, onFailure = { failures.add(it); null })
        } ?: throw exceptionProvider(failures)
    }
}
