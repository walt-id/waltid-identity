package id.walt.policies.policies.status.entry

import id.walt.mdoc.doc.MDoc
import id.walt.policies.policies.status.model.IETFEntry
import id.walt.policies.policies.status.model.StatusEntry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MDocEntryExtractor : EntryExtractor {
    override fun extract(data: JsonElement): StatusEntry? = data.jsonObject["mdoc"]?.let {
        MDoc.fromCBORHex(it.jsonPrimitive.content).MSO?.status?.statusList?.let {
            IETFEntry(
                statusList = IETFEntry.StatusListField(
                    index = it.index.toULong(), uri = it.uri
                )
            )
        }
    }
}