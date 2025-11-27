package id.walt.policies.policies.status.entry

import kotlinx.serialization.json.JsonElement

interface EntryExtractor {
    fun extract(data: JsonElement): JsonElement?
}