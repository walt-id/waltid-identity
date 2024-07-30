package id.walt.credentials.verification

import kotlinx.serialization.json.JsonElement
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class JwtVerificationPolicy(override val name: String, override val description: String? = null) :
    VerificationPolicy(name, description) {
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    abstract suspend fun verify(credential: String, args: Any? = null, context: Map<String, Any>): Result<Any>

}

abstract class JavaJwtVerificationPolicy(
    override val name: String,
    override val description: String? = null
): JwtVerificationPolicy(name, description) {
    override suspend fun verify(credential: String, args: Any?, context: Map<String, Any>): Result<Any> {
        return runCatching { javaVerify(credential, args, context) }
    }

    abstract fun javaVerify(credential: String, args: Any? = null, context: Map<String, Any>): Any
}
