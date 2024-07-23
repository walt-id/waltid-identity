package id.walt.credentials.verification.policies

import kotlinx.serialization.json.JsonElement

actual class RevocationPolicy actual constructor() :
    RevocationPolicyMp() {
    actual override suspend fun verify(
        data: JsonElement,
        args: Any?,
        context: Map<String, Any>
    ): Result<Any> {
        TODO("Not yet implemented")
    }
}