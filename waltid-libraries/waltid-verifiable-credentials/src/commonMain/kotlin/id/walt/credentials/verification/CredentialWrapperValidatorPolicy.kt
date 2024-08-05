package id.walt.credentials.verification

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
abstract class CredentialWrapperValidatorPolicy : VerificationPolicy() {
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    abstract suspend fun verify(data: JsonObject, args: Any? = null, context: Map<String, Any>): Result<Any>
}

abstract class JavaCredentialWrapperValidatorPolicy : CredentialWrapperValidatorPolicy() {
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        return runCatching { javaVerify(data, args, context) }
    }

    abstract fun javaVerify(data: JsonObject, args: Any?, context: Map<String, Any>): Any
}
