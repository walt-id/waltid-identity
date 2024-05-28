package id.walt.webwallet.service.credentials

import id.walt.webwallet.utils.JsonUtils
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CredentialValidator {
    fun validate(entryPurpose: String, subjectPurpose: String, subjectType: String, credential: JsonObject) = let {
        //TODO: should call verifier policy
        val now = Clock.System.now()
        val validFrom =
            JsonUtils.tryGetData(credential, "validFrom")?.jsonObject?.jsonPrimitive?.content?.let { Instant.parse(it) } ?: now
        val validUntil =
            JsonUtils.tryGetData(credential, "validUntil")?.jsonObject?.jsonPrimitive?.content?.let { Instant.parse(it) } ?: now
        //TODO: signature
        now in (validFrom..validUntil) && validateStatusPurpose(entryPurpose, subjectPurpose) && validateSubjectType(subjectType)
    }

    private fun validateSubjectType(type: String) = type in listOf(
        "BitstringStatusList",
        "StatusList2021",
        "RevocationList2021",
    )

    private fun validateStatusPurpose(entryPurpose: String, subjectPurpose: String) =
        entryPurpose == subjectPurpose
}