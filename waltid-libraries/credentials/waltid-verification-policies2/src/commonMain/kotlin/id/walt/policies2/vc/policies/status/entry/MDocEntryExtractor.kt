package id.walt.policies2.vc.policies.status.entry

import id.walt.mdoc.doc.MDoc
import id.walt.mdoc.mso.StatusListInfo
import id.walt.policies2.vc.policies.status.model.IETFEntry
import kotlinx.serialization.json.*

class MDocEntryExtractor : EntryExtractor {
    private val json = Json
    override fun extract(data: JsonElement): JsonElement? = data.jsonObject["mdoc"]?.let {
        MDoc.fromCBORHex(it.jsonPrimitive.content).MSO?.status?.statusList?.let {
            json.encodeToJsonElement(createEntry(it))
        }
    }

    private fun createEntry(info: StatusListInfo): IETFEntry =
        IETFEntry(
            statusList = IETFEntry.StatusListField(
                index = info.index.toULong(), uri = info.uri
            )
        )
}
