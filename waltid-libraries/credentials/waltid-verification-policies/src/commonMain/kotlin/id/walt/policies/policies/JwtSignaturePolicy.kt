package id.walt.policies.policies

import id.walt.policies.JwtVerificationPolicy
import id.walt.sdjwt.SDJwt
import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.w3c.utils.VCFormat
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.Serializable
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
class JwtSignaturePolicy : JwtVerificationPolicy(
) {
    override val name = "signature"
    override val description =
        "Checks a JWT credential by verifying its cryptographic signature using the key referenced by the DID in `iss`."
    override val supportedVCFormats = setOf(VCFormat.jwt_vc, VCFormat.jwt_vc_json, VCFormat.ldp_vc)

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(credential: String, args: Any?, context: Map<String, Any>): Result<Any> {
        return JwsSignatureScheme().let {
            if (SDJwt.isSDJwt(credential, sdOnly = true)) {
                it.verifySDJwtWithIssuerKeys(credential)
            } else
                it.verify(credential)
        }
    }

    private suspend fun JwsSignatureScheme.verifySDJwtWithIssuerKeys(credential: String): Result<JsonElement> {
        val keysInfo = getIssuerKeysInfo(credential)
        var lastFailure: Throwable? = null

        keysInfo.keys.forEach { key ->
            val keyMap = mutableMapOf(keysInfo.keyId to key)
            runCatching { key.getKeyId() }.getOrNull()?.let { keyId -> keyMap[keyId] = key }

            val result = verifySDJwt(
                credential,
                JWTCryptoProviderManager.getDefaultJWTCryptoProvider(keyMap),
            )
            if (result.isSuccess) return result
            lastFailure = result.exceptionOrNull()
        }

        return Result.failure(lastFailure ?: IllegalArgumentException("No issuer keys found in the DID document"))
    }
}
