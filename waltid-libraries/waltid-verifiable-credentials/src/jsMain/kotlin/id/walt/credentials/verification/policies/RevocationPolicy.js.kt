package id.walt.credentials.verification.policies

import kotlinx.serialization.json.JsonElement
import love.forte.plugin.suspendtrans.annotation.JsPromise

//@JsPromise
//@JsExport.Ignore
actual class RevocationPolicy: RevocationPolicyMp()  {
    override suspend fun verify(data: JsonElement, args: Any?, context: Map<String, Any>): Result<Any> {
        TODO("Not yet implemented")
    }
}