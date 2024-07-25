package id.walt.credentials.verification.policies

import kotlinx.serialization.json.JsonObject

//@JsPromise
//@JsExport.Ignore
actual class RevocationPolicy: RevocationPolicyMp()  {
    actual override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        TODO("Not yet implemented")
    }
}
