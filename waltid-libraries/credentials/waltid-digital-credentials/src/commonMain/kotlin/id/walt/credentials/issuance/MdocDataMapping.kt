package id.walt.credentials.issuance

import id.walt.credentials.issuance.CredentialDataMergeUtils.mdocNamespaceMapping
import id.walt.credentials.issuance.CredentialDataMergeUtils.mergeMdocPayloadWithMapping
import id.walt.mdoc.issuance.MdlAge
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
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
        val namespaceMapping = dataMapping?.mdocNamespaceMapping(credentialData) ?: return credentialData
        val mdlAgeMapping = (namespaceMapping[MdlAge.NAMESPACE] as? JsonObject)?.takeIf {
            docType == MdlAge.DOC_TYPE && it[MdlAge.AGE_IN_YEARS] == JsonPrimitive(MdlAge.AGE_MAPPING)
        }
        val mapping = if (mdlAgeMapping == null) {
            namespaceMapping
        } else {
            val otherElements = JsonObject(mdlAgeMapping - MdlAge.AGE_IN_YEARS)
            JsonObject(namespaceMapping + (MdlAge.NAMESPACE to otherElements))
        }
        val mappedData = credentialData.mergeMdocPayloadWithMapping(
            mapping = mapping,
            context = context(),
            data = dataFunctionsFor(clock),
        )
        if (mdlAgeMapping == null) return mappedData

        // Derive age from the final birth date and the MSO validity start.
        val mdlData = mappedData.getValue(MdlAge.NAMESPACE).jsonObject
        val birthDatePath = "credentialData.${MdlAge.NAMESPACE}.${MdlAge.BIRTH_DATE}"
        val birthDate = MdlAge.parseBirthDate(mdlData[MdlAge.BIRTH_DATE], birthDatePath)
        val age = MdlAge.completedYears(birthDate, validFrom, birthDatePath)
        val mdlDataWithAge = JsonObject(mdlData + (MdlAge.AGE_IN_YEARS to JsonPrimitive(age)))
        return JsonObject(mappedData + (MdlAge.NAMESPACE to mdlDataWithAge))
    }
}
