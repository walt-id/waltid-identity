package id.walt.credentials.issuance

import id.walt.credentials.issuance.CredentialDataMergeUtils.mdocNamespaceMapping
import id.walt.credentials.issuance.CredentialDataMergeUtils.mergeMdocPayloadWithMapping
import id.walt.mdoc.issuance.MdlAge
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Instant

/** Resolves mdoc namespace mappings before JSON-to-CBOR conversion. */
object MdocDataMapping {
    suspend fun resolve(
        credentialData: JsonObject,
        dataMapping: JsonObject?,
        docType: String,
        validFrom: Instant,
        clock: Clock,
        context: () -> Map<String, JsonElement>,
    ): JsonObject {
        val namespaceMapping = dataMapping?.mdocNamespaceMapping(credentialData)
        val mappedData = namespaceMapping?.let {
            credentialData.mergeMdocPayloadWithMapping(
                mapping = it,
                context = context(),
                data = dataFunctionsFor(clock),
            )
        } ?: credentialData
        if (docType == MdlAge.DOC_TYPE) {
            (mappedData[MdlAge.NAMESPACE] as? JsonObject)?.let {
                MdlAge.validate(it, validFrom, "credentialData.${MdlAge.NAMESPACE}")
            }
        }
        return mappedData
    }
}
