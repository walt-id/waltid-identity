package id.walt.credentials.verification.policies

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

//@JsPromise
//@JsExport.Ignore
@Serializable
actual class RevocationPolicy: RevocationPolicyMp()  {
    actual override suspend fun verify(data: JsonElement, args: Any?, context: Map<String, Any>): Result<Any> {
        TODO("Not yet implemented")
    }
}
