package id.walt.credentials.verification

import kotlinx.serialization.json.JsonObject
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class CredentialDataValidatorPolicy(
    override val name: String,
    override val description: String? = null
) : VerificationPolicy(name, description) {
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    abstract suspend fun verify(data: JsonObject, args: Any? = null, context: Map<String, Any>): Result<Any>

}
