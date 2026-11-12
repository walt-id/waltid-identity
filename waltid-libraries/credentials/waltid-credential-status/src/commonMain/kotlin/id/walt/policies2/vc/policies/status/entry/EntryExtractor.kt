package id.walt.policies2.vc.policies.status.entry

import id.walt.credentials.formats.DigitalCredential
import kotlinx.serialization.json.JsonElement

interface EntryExtractor {
    fun extract(data: DigitalCredential): JsonElement?
}
