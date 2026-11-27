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
    private val mapsAge: Boolean,
    private val mapsBirthYear: Boolean,
    private val mapsNamespace: Boolean,
) {
    val canSupplyBirthDate: Boolean
        get() = mapsBirthDate || mapsNamespace

    internal val canSupplyAge: Boolean
        get() = mapsAge || mapsNamespace

    internal val canSupplyBirthYear: Boolean
        get() = mapsBirthYear || mapsNamespace

    /** Supply a temporary value for the JSONPath mapper, which writes into existing destinations. */
    fun prepareDestination(credentialData: JsonObject): JsonObject {
        val namespace = when (val value = credentialData[MdlAge.NAMESPACE]) {
            null, JsonNull -> null
            is JsonObject -> value
            else -> return credentialData
        }
        val missingBirthDate = mapsBirthDate && (namespace?.get(MdlAge.BIRTH_DATE) ?: JsonNull) == JsonNull
        val missingBirthYear = mapsBirthYear && (namespace?.get(MdlAge.AGE_BIRTH_YEAR) ?: JsonNull) == JsonNull
        if (!missingBirthDate && !missingBirthYear && !(mapsNamespace && namespace == null)) return credentialData

        val destination = namespace.orEmpty().toMutableMap()
        if (missingBirthDate) destination[MdlAge.BIRTH_DATE] = JsonPrimitive("")
        if (missingBirthYear) destination[MdlAge.AGE_BIRTH_YEAR] = JsonPrimitive("")
        return JsonObject(credentialData + (MdlAge.NAMESPACE to JsonObject(destination)))
    }

    companion object {
        private const val NAMESPACE_PATH = "$['${MdlAge.NAMESPACE}']"
        private const val BIRTH_DATE_PATH = "$NAMESPACE_PATH['${MdlAge.BIRTH_DATE}']"
        private const val AGE_PATH = "$NAMESPACE_PATH['${MdlAge.AGE_IN_YEARS}']"

        private const val BIRTH_YEAR_PATH = "$NAMESPACE_PATH['${MdlAge.AGE_BIRTH_YEAR}']"

        fun forDestinations(paths: Collection<String>): MdlLoginClaimMapping {
            val targets = paths.mapNotNullTo(mutableSetOf()) { path ->
                try {
                    JsonPath.compile(path).path.replace('"', '\'')
                } catch (_: InvalidPathException) {
                    null
                }
            }
            return MdlLoginClaimMapping(
                mapsBirthDate = BIRTH_DATE_PATH in targets,
                mapsAge = AGE_PATH in targets,
                mapsBirthYear = BIRTH_YEAR_PATH in targets,
                mapsNamespace = NAMESPACE_PATH in targets,
            )
        }
    }
}
