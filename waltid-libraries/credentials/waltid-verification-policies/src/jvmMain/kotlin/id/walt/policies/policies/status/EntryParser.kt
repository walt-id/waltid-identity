package id.walt.policies.policies.status

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer

interface EntryParser<T> {
    fun parse(json: JsonElement): List<T>
}

class ListEntryParser<T>(
    private val jsonModule: Json,
) : EntryParser<T> {
    override fun parse(json: JsonElement): List<T> = jsonModule.decodeFromJsonElement<List<T>>(json)
}

class ObjectEntryParser<T>(
    private val jsonModule: Json,
    private val serializer: KSerializer<T>
) : EntryParser<T> {
    override fun parse(json: JsonElement): List<T> = listOf(jsonModule.decodeFromJsonElement(serializer, json))
}

inline fun <reified T> createEntryParser(jsonModule: Json, list: Boolean = false): EntryParser<T> = if (list) {
    ListEntryParser(jsonModule)
} else {
    ObjectEntryParser(jsonModule, serializer<T>())
}