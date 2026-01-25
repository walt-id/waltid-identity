package id.walt.policies2.vc.policies.status.entry

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

class IETFEntryExtractor(
    private val mdocExtractor: EntryExtractor,
) : EntryExtractor {
    override fun extract(data: JsonElement): JsonElement? = data.jsonObject["status"] ?: mdocExtractor.extract(data)
}
