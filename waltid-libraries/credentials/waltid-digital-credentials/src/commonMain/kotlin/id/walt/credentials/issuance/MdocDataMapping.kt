package id.walt.credentials.issuance

import id.walt.credentials.issuance.CredentialDataMergeUtils.mdocNamespaceMapping
import id.walt.credentials.issuance.CredentialDataMergeUtils.mergeMdocPayloadWithMapping
import id.walt.mdoc.issuance.MdlAge
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
        mdlBirthYear: MdlBirthYear? = null,
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
        if (docType != MdlAge.DOC_TYPE) return mappedData
        val namespace = mappedData[MdlAge.NAMESPACE] as? JsonObject ?: return mappedData
        val path = "credentialData.${MdlAge.NAMESPACE}"
        val finalNamespace = when (mdlBirthYear) {
            MdlBirthYear.Inherited -> {
                val birthDate = MdlAge.parseBirthDate(namespace[MdlAge.BIRTH_DATE], "$path.${MdlAge.BIRTH_DATE}")
                JsonObject(namespace + (MdlAge.AGE_BIRTH_YEAR to JsonPrimitive(birthDate.year)))
            }
            is MdlBirthYear.Explicit -> {
                val birthDate = MdlAge.parseBirthDate(namespace[MdlAge.BIRTH_DATE], "$path.${MdlAge.BIRTH_DATE}")
                val yearPath = "$path.${MdlAge.AGE_BIRTH_YEAR}"
                MdlAge.requireMatchesBirthYear(MdlAge.parseBirthYear(mdlBirthYear.value, yearPath), birthDate, yearPath)
                namespace
            }
            null -> namespace
        }
        MdlAge.validate(finalNamespace, validFrom, path)
        return if (finalNamespace === namespace) mappedData
        else JsonObject(mappedData + (MdlAge.NAMESPACE to finalNamespace))
    }
}
