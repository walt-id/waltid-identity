package id.walt.sdjwt

import id.walt.platform.utils.ios.HMAC_Operations
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalForeignApi::class)
class HMACJWTCryptoProvider(private val algorithm: String, private val key: ByteArray) :
    JWTCryptoProvider {
    override fun sign(payload: JsonObject, keyID: String?, typ: String): String {

        val result = HMAC_Operations.signWithBody(
            body = payload.toString(),
            alg = algorithm,
            key = key.toData(),
            typ = typ,
            keyId = keyID
        )

        return when {
            result.success() -> result.data()!!
            else -> result.errorMessage() ?: ""
        }
    }

    override fun verify(jwt: String): JwtVerificationResult {
        val result = HMAC_Operations.verifyWithJws(jws = jwt, key = key.toData())

        return when {
            result.success() -> JwtVerificationResult(result.success()!!)
            else -> JwtVerificationResult(false, message = result.errorMessage() ?: "")
        }
    }
}
