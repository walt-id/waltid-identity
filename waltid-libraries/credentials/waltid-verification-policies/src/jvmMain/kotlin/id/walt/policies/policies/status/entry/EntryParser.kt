package id.walt.policies.policies.status.entry

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

sealed interface EntryParser<out T> {
    fun parse(json: JsonElement): T

    companion object {

        fun <T> objectParser(
            json: Json,
            elementSerializer: KSerializer<T>,
        ): EntryParser<T> {
            return DefaultEntryParser(json, elementSerializer)
        }

        fun <T> listParser(
            json: Json,
            elementSerializer: KSerializer<T>,
        ): EntryParser<List<T>> {
            return DefaultEntryParser(json, ListSerializer(elementSerializer))
        }

        inline fun <reified T> new(
            json: Json,
            isList: Boolean,
        ) = serializer<T>().let { if (isList) listParser(json, it) else objectParser(json, it) }
    }
}

internal class DefaultEntryParser<out T>(
    private val json: Json, private val serializer: KSerializer<T>
) : EntryParser<T> {
    override fun parse(json: JsonElement): T = this.json.decodeFromJsonElement(serializer, json)
}