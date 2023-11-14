package id.walt.issuer.revocation.statuslist2021

import com.beust.klaxon.Json
import id.walt.common.createEncodedBitString
import id.walt.common.decodeBitSet
import id.walt.common.uncompressGzip
import id.walt.credentials.w3c.VerifiableCredential
import id.walt.credentials.w3c.W3CCredentialSubject
import id.walt.credentials.w3c.builder.W3CCredentialBuilder
import id.walt.credentials.w3c.templates.VcTemplateService
import id.walt.credentials.w3c.toVerifiableCredential
import id.walt.crypto.decBase64
import id.walt.issuer.revocation.*
import id.walt.model.credential.status.StatusList2021EntryCredentialStatus
import id.walt.signatory.ProofConfig
import id.walt.signatory.ProofType
import id.walt.signatory.Signatory
import id.walt.signatory.revocation.*
import id.walt.issuer.revocation.statuslist2021.index.StatusListIndexService
import id.walt.issuer.revocation.statuslist2021.storage.StatusListCredentialStorageService
import kotlinx.serialization.Serializable

class StatusList2021EntryClientService: CredentialStatusClientService {

    private val storageService = StatusListCredentialStorageService.getService()
    private val indexingService = StatusListIndexService.getService()
    private val templateId = "StatusList2021Credential"
    private val signatoryService = Signatory.getService()
    private val templateService = VcTemplateService.getService()

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
                credential.issuer?.let { issuer ->
                    issue(
                        credential.id ?: parameter.credentialStatus.id,
                        parameter.credentialStatus.statusPurpose,
                        parameter.credentialStatus.statusListCredential,
                        issuer.id,
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
                statusListCredential = parameter.credentialUrl
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


    private fun issue(id: String, purpose: String, url: String, issuer: String, bitString: String) = W3CCredentialSubject(
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

    private fun extractStatusListCredentialSubject(statusCredential: VerifiableCredential) =
        statusCredential.credentialSubject?.let {
            StatusListCredentialSubject(
                id = it.id,
                type = it.properties["type"] as? String ?: "",
                statusPurpose = it.properties["statusPurpose"] as? String ?: "",
                encodedList = it.properties["encodedList"] as? String ?: "",
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
        @Json(serializeNull = false)
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
