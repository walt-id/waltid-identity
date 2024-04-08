package id.walt.webwallet.service.credentials.status

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.webwallet.service.BitStringValueParser
import id.walt.webwallet.service.credentials.CredentialValidator
import id.walt.webwallet.service.credentials.status.fetch.StatusListCredentialFetchFactory
import id.walt.webwallet.usecase.credential.CredentialStatusResult
import id.walt.webwallet.utils.hexToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

class StatusListCredentialStatusService(
    private val credentialFetchFactory: StatusListCredentialFetchFactory,
    private val credentialValidator: CredentialValidator,
    private val bitStringValueParser: BitStringValueParser,
) : CredentialStatusService {
    private val json = Json { ignoreUnknownKeys = true }
    override suspend fun get(statusEntry: CredentialStatusEntry): CredentialStatusResult =
        (statusEntry as? StatusListEntry)?.let { entry ->
            val credential = credentialFetchFactory.new(entry.statusListCredential).fetch(entry.statusListCredential)
            val subject =
                extractCredentialSubject(credential) ?: error("Failed to prase status list credential subject")
            credentialValidator.validate(entry.statusPurpose, subject.statusPurpose, subject.type, credential)
                .takeIf { it }?.let {
                    getStatusBit(subject.encodedList, entry.statusListIndex, subject.statusSize)?.let {
                        val bit = it.joinToString("")
                        CredentialStatusResult(
                            type = entry.statusPurpose,
                            result = getStatusResult(bit),
                            message = getStatusMessage(bit, subject.statusMessage)
                        )
                    } ?: error("Failed to retrieve bit value")
                } ?: error("Failed to validate status list credential")
        } ?: error("Error parsing status list entry")

    private fun extractCredentialSubject(credential: JsonObject): StatusListCredentialSubject? =
        credential.jsonObject["credentialSubject"]?.toJsonElement()?.let {
            json.decodeFromJsonElement(it)
        }

    private fun getStatusBit(bitstring: String, idx: ULong, bitSize: Int) =
        bitStringValueParser.get(bitstring, idx, bitSize)

    private fun getStatusResult(bit: String) = hexToInt(bit) != 0

    private fun getStatusMessage(bit: String, statusMessages: List<StatusMessage>?) = statusMessages?.firstOrNull {
        hexToInt(it.status) == hexToInt(bit)
    }?.message

    @Serializable
    private data class StatusListCredentialSubject(
        val id: String,
        val type: String,
        @SerialName("statusPurpose")
        val statusPurposeOptional: String? = RevocationStatusPurpose,
        val encodedList: String,
        val statusSize: Int = 1,
        val statusMessage: List<StatusMessage>? = null,
        val statusReference: String? = null,//TODO: fetch status reference for message, when supplied
        val ttl: Int? = null,
    ) {
        @Transient
        val statusPurpose = statusPurposeOptional!!//workaround for optional json field (but required for app logic)
    }

    @Serializable
    private data class StatusMessage(
        val status: String,
        val message: String,
    )
}