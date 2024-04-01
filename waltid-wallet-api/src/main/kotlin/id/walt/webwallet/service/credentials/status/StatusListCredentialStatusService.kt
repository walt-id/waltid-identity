package id.walt.webwallet.service.credentials.status

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.webwallet.usecase.credential.CredentialStatusResult
import id.walt.webwallet.utils.Base64Utils
import id.walt.webwallet.utils.GzipUtils
import io.ktor.client.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

class StatusListCredentialStatusService(
    private val http: HttpClient,
) : CredentialStatusService {
    private val json = Json { ignoreUnknownKeys = true }
    override fun get(statusEntry: CredentialStatusEntry): CredentialStatusResult =
        (statusEntry as? StatusListEntry)?.let { entry ->
            val credential = json.decodeFromString<JsonObject>(fetchStatusListCredential(entry.statusListCredential))
            val subject = extractCredentialSubject(credential) ?: error("")
            validateStatusListCredential(subject, credential).takeIf { it }?.let {
                getStatusBit(subject.encodedList, entry.statusListIndex, subject.statusSize)?.let {
                    CredentialStatusResult(
                        type = entry.statusPurpose,
                        result = false,
                        message = getStatusMessage(it.joinToString(), subject.statusMessage)
                    )
                } ?: error("Failed to retrieve bit value")
            } ?: error("Failed to validate status list credential")
        } ?: error("Error parsing status list entry")

    //TODO: extract in separate entity, credential-fetcher or smth.
    private fun fetchStatusListCredential(url: String): String {
        TODO()
    }

    //TODO: extract in separate entity, credential-validator or smth.
    private fun validateStatusListCredential(subject: StatusListCredentialSubject, credential: JsonObject) = let {
        //TODO: should call verifier policy
        val now = Clock.System.now()
        val validFrom =
            credential.jsonObject["validFrom"]?.jsonObject?.jsonPrimitive?.content?.let { Instant.parse(it) } ?: now
        val validUntil =
            credential.jsonObject["validUntil"]?.jsonObject?.jsonPrimitive?.content?.let { Instant.parse(it) } ?: now
        //TODO: signature
        now in (validFrom..validUntil) && validateStatusPurpose(subject.statusPurpose, subject) && validateSubjectType(subject)
    }

    private fun extractCredentialSubject(credential: JsonObject): StatusListCredentialSubject? =
        credential.jsonObject["credentialSubject"]?.toJsonElement()?.let {
            json.decodeFromJsonElement(it)
        }

    private fun validateSubjectType(subject: StatusListCredentialSubject) = subject.type in listOf(
        "BitstringStatusList",
        "StatusList2021",
    )

    private fun validateStatusPurpose(purpose: String, subject: StatusListCredentialSubject) =
        purpose == subject.statusPurpose

    private fun getStatusBit(bitstring: String, idx: ULong, bitSize: Int) =
        GzipUtils.uncompress(Base64Utils.decode(bitstring), idx, bitSize)

    private fun getStatusMessage(bit: String, statusMessages: List<StatusMessage>?) = statusMessages?.firstOrNull {
        hexToInt(it.status) == hexToInt(bit)
    }?.message

    private fun hexToInt(hex: String) = Integer.parseInt(hex.startsWith("0x").takeIf { it }?.let {
        hex.substring(2)
    } ?: hex)

    @Serializable
    private data class StatusListCredentialSubject(
        val id: String,
        val type: String,
        val statusPurpose: String,
        val encodedList: String,
        val statusSize: Int = 1,
        val statusMessage: List<StatusMessage>? = null,
        val statusReference: String? = null,
        val ttl: Int? = null,
    )

    @Serializable
    private data class StatusMessage(
        val status: String,
        val message: String,
    )
}