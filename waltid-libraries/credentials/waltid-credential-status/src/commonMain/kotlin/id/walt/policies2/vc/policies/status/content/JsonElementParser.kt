package id.walt.policies2.vc.policies.status.content

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class JsonElementParser<T>(
    private val serializer: KSerializer<T>
) : ContentParser<JsonElement, T> {
    private val jsonModule = Json { ignoreUnknownKeys = true }

    override fun parse(response: JsonElement): T = jsonModule.decodeFromJsonElement(serializer, response)
}
