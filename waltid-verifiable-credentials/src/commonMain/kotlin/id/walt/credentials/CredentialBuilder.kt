package id.walt.credentials

import id.walt.credentials.vc.vcs.W3CV2DataModel
import id.walt.credentials.vc.vcs.W3CVC
import id.walt.did.utils.randomUUID
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

enum class CredentialBuilderType {
    W3CV2CredentialBuilder,
    MdocsCredentialBuilder
}

class CredentialBuilder(
    builderType: CredentialBuilderType
) {

    var context: List<String> = listOf("https://www.w3.org/ns/credentials/v2")
    var type: List<String> = listOf("VerifiableCredential")
    fun addType(addType: String) {
        type = type.toMutableList().apply { add(addType) }
    }

    fun addContext(addContext: String) {
        context = context.toMutableList().apply { add(addContext) }
    }

    var credentialId: String? = "urn:uuid:${randomUUID()}"
    fun randomCredentialSubjectUUID() {
        credentialId = "urn:uuid:${randomUUID()}"
    }

    var issuerDid: String? = null
    var subjectDid: String? = null
    var validFrom: Instant? = Clock.System.now()
    fun validFromNow() {
        validFrom = Clock.System.now() - (1.5.minutes)
    }

    var validUntil: Instant? = null
    fun validFor(duration: Duration) {
        validUntil = Clock.System.now() + duration
    }

    var credentialStatus: W3CV2DataModel.CredentialStatus? = null

    fun useStatusList2021Revocation(statusListCredential: String, listIndex: Int) {
        credentialStatus = W3CV2DataModel.CredentialStatus(
            id = "$statusListCredential#$listIndex",
            type = "StatusList2021Entry",
            statusPurpose = "revocation",
            statusListIndex = listIndex.toString(),
            statusListCredential = statusListCredential //
        )
    }

    var termsOfUse: W3CV2DataModel.TermsOfUse? = null

    var _customCredentialSubjectData: JsonObject? = null
    fun useCredentialSubject(data: JsonObject) {
        _customCredentialSubjectData = data
    }

    fun buildW3CV2DataModel(): W3CV2DataModel {

        val buildSubject = _customCredentialSubjectData?.let {
            JsonObject(
                _customCredentialSubjectData!!.toMutableMap()
                    .apply {
                        subjectDid?.let {
                            put("id", JsonPrimitive(subjectDid))
                        }
                    })
        }

        //       val buildSubject = JsonObject(mapOf())
        return W3CV2DataModel(
            context = context,
            type = type,
            credentialSubject = buildSubject ?: JsonObject(mapOf("id" to JsonPrimitive(subjectDid))),
            id = credentialId,
            issuer = issuerDid,
            validFrom = validFrom.toString(),
            validUntil = validUntil.toString(),
            credentialStatus = credentialStatus,
            termsOfUse = termsOfUse
        )
    }

    fun buildW3C(): W3CVC {
        return W3CVC(Json.encodeToJsonElement(buildW3CV2DataModel()).jsonObject)
    }
}
