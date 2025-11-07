package id.walt.policies.policies.status.entry

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

class W3CEntryExtractor : EntryExtractor {
    override fun extract(data: JsonElement): JsonElement? = data.jsonObject["vc"]?.jsonObject?.get("credentialStatus")
}