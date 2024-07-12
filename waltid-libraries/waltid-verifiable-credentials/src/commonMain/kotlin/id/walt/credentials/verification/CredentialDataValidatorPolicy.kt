package id.walt.credentials.verification

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@Serializable
@OptIn(ExperimentalJsExport::class)
@JsExport
abstract class CredentialDataValidatorPolicy(
    @Transient override val name: String = "unknown CredentialDataValidatorPolicy",
    @Transient override val description: String? = null
) : VerificationPolicy(name, description) {
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    abstract suspend fun verify(data: JsonObject, args: Any? = null, context: Map<String, Any>): Result<Any>
}
