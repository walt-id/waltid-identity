import id.walt.sdjwt.JWTCryptoProvider
import id.walt.sdjwt.JwtVerificationResult
import id.walt.sdjwt.cinterop.ios.DS_Operations
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.JsonObject
import platform.Security.*
import kotlin.js.ExperimentalJsExport

@OptIn(ExperimentalForeignApi::class)
class DigitalSignaturesJWTCryptoProvider(private val algorithm: String, private val key: SecKeyRef) :
    JWTCryptoProvider {
    override fun sign(payload: JsonObject, keyID: String?, typ: String): String {

        val result = DS_Operations.signWithBody(
            body = payload.toString(),
            alg = algorithm,
            key = key,
            typ = typ,
            keyId = keyID
        )

        return when {
            result.success() -> result.data()!!
            else -> result.errorMessage() ?: ""
        }
    }

    override fun verify(jwt: String): JwtVerificationResult {
        val result = DS_Operations.verifyWithJws(jws = jwt, key = key)

        return when {
            result.success() -> JwtVerificationResult(result.success()!!)
            else -> JwtVerificationResult(false, message = result.errorMessage() ?: "")
        }
    }
}
