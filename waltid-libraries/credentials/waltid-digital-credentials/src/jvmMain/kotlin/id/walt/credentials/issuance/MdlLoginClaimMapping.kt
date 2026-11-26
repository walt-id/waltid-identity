package id.walt.credentials.issuance

import com.jayway.jsonpath.InvalidPathException
import com.jayway.jsonpath.JsonPath
import id.walt.mdoc.issuance.MdlAge
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The mDL destinations a configured external login can populate. */
class MdlLoginClaimMapping private constructor(
    private val mapsBirthDate: Boolean,
    private val mapsNamespace: Boolean,
) {
    val canSupplyBirthDate: Boolean
        get() = mapsBirthDate || mapsNamespace

    /** Supply a temporary value for the JSONPath mapper, which writes into existing destinations. */
    fun prepareDestination(credentialData: JsonObject): JsonObject {
        val namespace = when (val value = credentialData[MdlAge.NAMESPACE]) {
            null, JsonNull -> null
            is JsonObject -> value
            else -> return credentialData
        }
        val destination = when {
            mapsBirthDate && (namespace?.get(MdlAge.BIRTH_DATE) ?: JsonNull) == JsonNull ->
                JsonObject(namespace.orEmpty() + (MdlAge.BIRTH_DATE to JsonPrimitive("")))

            mapsNamespace && namespace == null -> JsonObject(emptyMap())
            else -> return credentialData
        }
        return JsonObject(credentialData + (MdlAge.NAMESPACE to destination))
    }

    companion object {
        private const val NAMESPACE_PATH = "$['${MdlAge.NAMESPACE}']"
        private const val BIRTH_DATE_PATH = "$NAMESPACE_PATH['${MdlAge.BIRTH_DATE}']"

        fun forDestinations(paths: Collection<String>): MdlLoginClaimMapping {
            val targets = paths.mapNotNullTo(mutableSetOf()) { path ->
                try {
                    JsonPath.compile(path).path.replace('"', '\'')
                } catch (_: InvalidPathException) {
                    null
                }
            }
            return MdlLoginClaimMapping(BIRTH_DATE_PATH in targets, NAMESPACE_PATH in targets)
        }
    }
}
