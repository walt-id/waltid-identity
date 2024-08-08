package id.walt.credentials.verification

import kotlinx.serialization.Serializable
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@Serializable
@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class JwtVerificationPolicy : VerificationPolicy() {
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    abstract suspend fun verify(credential: String, args: Any? = null, context: Map<String, Any>): Result<Any>

}

abstract class JavaJwtVerificationPolicy : JwtVerificationPolicy() {
    override suspend fun verify(credential: String, args: Any?, context: Map<String, Any>): Result<Any> {
        return runCatching { javaVerify(credential, args, context) }
    }

    abstract fun javaVerify(credential: String, args: Any? = null, context: Map<String, Any>): Any
}
