package id.walt.issuer.revocation.statuslist2021

import id.walt.credentials.vc.vcs.Credential
import id.walt.credentials.w3c.W3CCredentialSubject
import id.walt.credentials.w3c.builder.W3CCredentialBuilder
import id.walt.credentials.w3c.templates.VcTemplateService
import id.walt.credentials.w3c.toVerifiableCredential
import id.walt.issuer.revocation.*
import id.walt.issuer.revocation.models.CredentialStatus
import id.walt.issuer.revocation.models.StatusList2021EntryCredentialStatus
import id.walt.issuer.revocation.models.statusSerializerModule
import id.walt.issuer.revocation.statuslist2021.index.StatusListIndexService
import id.walt.issuer.revocation.statuslist2021.storage.StatusListCredentialStorageService
import id.walt.issuer.utils.createEncodedBitString
import id.walt.issuer.utils.decBase64
import id.walt.issuer.utils.decodeBitSet
import id.walt.issuer.utils.uncompressGzip
import id.walt.signatory.ProofConfig
import id.walt.signatory.ProofType
import id.walt.signatory.Signatory
import id.walt.signatory.revocation.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class StatusList2021EntryClientService(
    private val storageService: StatusListCredentialStorageService,
    private val indexingService: StatusListIndexService,
    private val signatoryService: Signatory,
    private val templateService: VcTemplateService,
): CredentialStatusClientService {

    private val templateId = "StatusList2021Credential"
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        serializersModule = statusSerializerModule
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
        explicitNulls = false
    }

    override fun checkRevocation(parameter: RevocationCheckParameter): RevocationStatus = let {
        val credentialStatus = (parameter as StatusListRevocationCheckParameter).credentialStatus
        val credentialSubject = extractStatusListCredentialSubject(credentialStatus.statusListCredential) ?: throw IllegalArgumentException("Couldn't parse credential subject")
        val credentialIndex = credentialStatus.statusListIndex.toULongOrNull()?: throw IllegalArgumentException("Couldn't parse status list index")
        if(!verifyStatusPurpose(credentialStatus.statusPurpose, credentialSubject.statusPurpose)) throw IllegalArgumentException("Status purposes don't match")
        verifyStatusCredential()

        StatusListRevocationStatus(verifyBitStringStatus(credentialIndex, credentialSubject.encodedList))
    }

    override fun revoke(parameter: RevocationConfig): Unit = (parameter as StatusListRevocationConfig).run {
        storageService.fetch(this.credentialStatus.statusListCredential)?.let { credential ->
            extractStatusListCredentialSubject(credential)?.encodedList?.let { bitString ->
                credential.jsonObject["issuer"]?.jsonPrimitive?.content?.let { issuer ->
                    issue(
                        credential.jsonObject["id"]?.jsonPrimitive?.content ?: parameter.credentialStatus.id,
                        parameter.credentialStatus.statusPurpose,
                        parameter.credentialStatus.statusListCredential,
                        issuer,
                        updateBitString(bitString, parameter.credentialStatus.statusListIndex, 1)
                    )
                }
            }?.run {
                storageService.store(this, parameter.credentialStatus.statusListCredential)
            }
        }
    }

    override fun create(parameter: CredentialStatusFactoryParameter): StatusList2021EntryCredentialStatus =
        (parameter as StatusListEntryFactoryParameter).let {
            val idx = indexingService.index(parameter.credentialUrl)
            StatusList2021EntryCredentialStatus(
                id = "${parameter.credentialUrl}#$idx",
                statusPurpose = parameter.purpose,
                statusListIndex = idx,
                statusListCredential = parameter.credentialUrl,
                type = CredentialStatus.Types.StatusList2021Entry.value,
            )
        }.let {
            val bitString = storageService.fetch(parameter.credentialUrl)?.let {
                extractStatusListCredentialSubject(it)
            }?.encodedList ?: String(createEncodedBitString())
            val credential = issue(
                it.id,
                it.statusPurpose,
                it.statusListCredential,
                parameter.issuer,
                updateBitString(bitString, it.statusListIndex, 0)
            )
            // create / update the status list credential
            storageService.store(credential, parameter.credentialUrl)
            it
        }


    private fun issue(id: String, purpose: String, url: String, issuer: String, bitString: String): Credential = W3CCredentialSubject(
        id, mapOf("type" to "StatusList2021Credential", "statusPurpose" to purpose, "encodedList" to bitString)
    ).let {
        W3CCredentialBuilder.fromPartial(templateService.getTemplate(templateId).template!!).apply {
            setId(it.id ?: url)
            buildSubject {
                setFromJson(it.toJson())
            }
        }
    }.let {
        signatoryService.issue(
            credentialBuilder = it, config = ProofConfig(
                credentialId = url,
                issuerDid = issuer,
                subjectDid = issuer,
                proofType = ProofType.LD_PROOF,
            )
        ).toVerifiableCredential()
    }

    private fun updateBitString(encodedBitString: String, index: String, value: Int) = let {
        // get credential index
        val idx = index.toIntOrNull() ?: throw IllegalArgumentException("Couldn't parse credential index")
        // get bitString
        val bitSet = decodeBitSet(encodedBitString)
        // update the respective bit
        value.takeIf { it == 0 }?.let {
            bitSet.clear(idx)
        } ?: bitSet.set(idx)
        String(createEncodedBitString(bitSet))
    }

    private fun extractStatusListCredentialSubject(statusCredentialUrl: String): StatusListCredentialSubject? =
        storageService.fetch(statusCredentialUrl)?.let { extractStatusListCredentialSubject(it) }

    private fun extractStatusListCredentialSubject(statusCredential: JsonObject) =
        statusCredential.jsonObject["credentialSubject"]?.let {
            StatusListCredentialSubject(
                id = it.jsonObject["id"]?.jsonPrimitive?.content,
                type = it.jsonObject["type"]?.jsonPrimitive?.content ?: "",
                statusPurpose = it.jsonObject["statusPurpose"]?.jsonPrimitive?.content ?: "",
                encodedList = it.jsonObject["encodedList"]?.jsonPrimitive?.content ?: "",
            )
        }

    /* TODO:
    - proofs
    - matching issuers
     */
    private fun verifyStatusCredential() = true

    private fun verifyStatusPurpose(entryPurpose: String, credentialPurpose: String) =
        entryPurpose.equals(credentialPurpose, ignoreCase = true)

    private fun verifyBitStringStatus(idx: ULong, encodedList: String) = uncompressGzip(decBase64(encodedList), idx)[0] == '1'

    @Serializable
    data class StatusListCredentialSubject(
        val id: String? = null,
        val type: String,
        val statusPurpose: String,
        val encodedList: String,
    )

    data class StatusListCreateData(
        val statusEntry: StatusList2021EntryCredentialStatus,
        val bitString: String,
    )
}
