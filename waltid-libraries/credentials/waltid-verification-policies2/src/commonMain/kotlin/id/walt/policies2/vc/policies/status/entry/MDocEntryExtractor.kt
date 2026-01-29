package id.walt.policies2.vc.policies.status.entry

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.mdoc.objects.mso.Status
import id.walt.policies2.vc.policies.status.model.IETFEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

class MDocEntryExtractor : EntryExtractor {
    companion object {
        private val json = Json
    }

    override fun extract(data: DigitalCredential): JsonElement {
        val mDoc = data as? MdocsCredential
        requireNotNull(mDoc) { "Expecting MdocsCredential, but couldn't cast." }
        val statusListInfo = mDoc.documentMso.status?.statusList
        requireNotNull(statusListInfo) { "Expecting statusListInfo, but found none." }
        return json.encodeToJsonElement(createEntry(statusListInfo))
    }

    private fun createEntry(info: Status.StatusListInfo): IETFEntry =
        IETFEntry(statusList = IETFEntry.StatusListField(index = info.index, uri = info.uri.string))
}
