package id.walt.target.ios.keys

import id.walt.platform.utils.ios.JWE_Operations
import platform.Foundation.NSData

object JweEncryption {

    fun encrypt(plaintext: NSData, recipientJwk: String, encAlg: String, kid: String?): JweResult {
        val result = JWE_Operations.encryptWithPlaintext(plaintext, recipientJwk, encAlg, kid)
        return if (result.success()) {
            JweResult(data = result.data(), error = null)
        } else {
            JweResult(data = null, error = result.errorMessage())
        }
    }
}

class JweResult(val data: String?, val error: String?) {
    fun isSuccess(): Boolean = error == null
}
