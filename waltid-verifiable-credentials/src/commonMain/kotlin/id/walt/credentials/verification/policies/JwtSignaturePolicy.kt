package id.walt.credentials.verification.policies

import id.walt.credentials.schemes.JwsSignatureScheme
import id.walt.credentials.verification.JwtVerificationPolicy
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class JwtSignaturePolicy : JwtVerificationPolicy(
    "signature",
    "Checks a JWT credential by verifying its cryptographic signature using the key referenced by the DID in `iss`."
) {
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(credential: String, args: Any?, context: Map<String, Any>): Result<Any> {
        return JwsSignatureScheme().verify(credential)
    }
}
