package id.walt.policies2.vc.policies.status.entry

import id.walt.credentials.formats.DigitalCredential
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

class IETFEntryExtractor(
    private val mdocExtractor: EntryExtractor,
) : EntryExtractor {
    override fun extract(data: DigitalCredential): JsonElement? = data.credentialData.jsonObject["status"] ?: mdocExtractor.extract(data)
}
