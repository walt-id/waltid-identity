package id.walt.policies2.vc.policies.status.entry

import kotlinx.serialization.json.JsonElement

interface EntryExtractor {
    fun extract(data: JsonElement): JsonElement?
}
