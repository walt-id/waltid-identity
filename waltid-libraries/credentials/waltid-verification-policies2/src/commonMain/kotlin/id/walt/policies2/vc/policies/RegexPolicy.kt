package id.walt.policies2.vc.policies

import com.nfeld.jsonpathkt.kotlinx.resolvePathAsStringOrNull
import id.walt.credentials.formats.DigitalCredential
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
@SerialName("regex")
data class RegexPolicy(
    val path: String,
    val regex: String,
    @SerialName("regex_options")
    val regexOptions: Set<RegexOption>? = null,
    val allowNull: Boolean = false
) : CredentialVerificationPolicy2() {
    override val id = "regex"

    @Serializable
    data class CredentialDataMatcherResult(
        val value: String?,
        val groups: List<String>? = null
    ) {
        fun toJson() = Json.encodeToJsonElement(this)
    }

    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> {
        val regex = when {
            regexOptions != null -> Regex(regex, regexOptions)
            else -> Regex(regex)
        }

        val value = credential.credentialData.resolvePathAsStringOrNull(path) ?: return when {
            allowNull -> Result.success(CredentialDataMatcherResult(value = null).toJson())
            else -> Result.failure(IllegalArgumentException("Could not resolve path in credential data: $path"))
        }

        val result = regex.matchEntire(value)
            ?: return Result.failure(IllegalArgumentException("Credential data value does not match the specified regex: $value"))

        return Result.success(
            CredentialDataMatcherResult(
                value = result.value,
                groups = result.groupValues
            ).toJson()
        )
    }

}
