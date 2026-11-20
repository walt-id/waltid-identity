package id.walt.policies2.vc.policies.status.entry

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import kotlinx.serialization.json.JsonElement

class IETFEntryExtractor(
    private val mdocExtractor: EntryExtractor,
) : EntryExtractor {
    override fun extract(data: DigitalCredential): JsonElement? {
        val statusClaim = data.credentialData["status"].requireIetfStatusClaim(data.statusAudience())
        if (statusClaim != null) return statusClaim
        return if (data is MdocsCredential) mdocExtractor.extract(data) else null
    }
}
