package id.walt.policies.policies.status.entry

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

class IETFEntryExtractor(
    private val mdocExtractor: EntryExtractor,
) : EntryExtractor {
    override fun extract(data: JsonElement): JsonElement? = data.jsonObject["status"] ?: mdocExtractor.extract(data)
}