package id.walt.issuer.revocation

import id.walt.credentials.vc.vcs.W3CVC
import id.walt.issuer.revocation.models.CredentialStatus
import id.walt.issuer.revocation.models.StatusList2021EntryCredentialStatus
import id.walt.issuer.revocation.statuslist2021.StatusList2021EntryClientService
import id.walt.issuer.revocation.statuslist2021.index.IncrementalIndexingStrategy
import id.walt.issuer.revocation.statuslist2021.index.WaltIdStatusListIndexService
import id.walt.issuer.revocation.statuslist2021.storage.WaltIdStatusListCredentialStorageService
import id.walt.issuer.utils.createJsonBuilder
import kotlinx.serialization.Serializable

interface CredentialStatusClientService {
    fun checkRevocation(parameter: RevocationCheckParameter): RevocationStatus
    fun revoke(parameter: RevocationConfig)
    fun create(parameter: CredentialStatusFactoryParameter): CredentialStatus

    companion object {
        private val json = createJsonBuilder()
        private val storageService = WaltIdStatusListCredentialStorageService()
        private val indexingService = WaltIdStatusListIndexService(IncrementalIndexingStrategy())
        private val signatoryService = Signatory()
        private val templateService = VcTemplateService()
        private val statusListClientService = StatusList2021EntryClientService(storageService, indexingService, signatoryService, templateService)
        fun revoke(vc: W3CVC): RevocationResult =
            (json.decodeFromString<CredentialStatusCredential>(vc.toJson()).credentialStatus)?.let {
                runCatching { getClient(it).revoke(getConfig(it)) }.fold(onSuccess = {
                    RevocationResult(succeed = true)
                }, onFailure = {
                    RevocationResult(succeed = false, message = it.localizedMessage)
                })
            } ?: RevocationResult(succeed = false, message = "Verifiable credential has no credential-status property")

        fun check(vc: W3CVC): RevocationStatus =
            (json.decodeFromString<CredentialStatusCredential>(vc.toJson()).credentialStatus)?.let {
                getClient(it).checkRevocation(getParameter(it))
            } ?: throw IllegalArgumentException("Verifiable credential has no credential-status property")

        private fun getConfig(credentialStatus: CredentialStatus): RevocationConfig = when (credentialStatus) {
            is StatusList2021EntryCredentialStatus -> StatusListRevocationConfig(credentialStatus = credentialStatus)
            else -> throw IllegalArgumentException("Credential status type not supported: ${credentialStatus.type}")
        }

        private fun getParameter(credentialStatus: CredentialStatus): RevocationCheckParameter = when (credentialStatus) {
            is StatusList2021EntryCredentialStatus -> StatusListRevocationCheckParameter(credentialStatus = credentialStatus)
            else -> throw IllegalArgumentException("Credential status type not supported: ${credentialStatus.type}")
        }

        private fun getClient(credentialStatus: CredentialStatus): CredentialStatusClientService = when (credentialStatus) {
            is StatusList2021EntryCredentialStatus -> statusListClientService
            else -> throw IllegalArgumentException("Credential status type not supported: ${credentialStatus.type}")
        }
    }
}

/*
Revocation status
 */
interface RevocationStatus {
    val isRevoked: Boolean
}

@Serializable
data class StatusListRevocationStatus(
    override val isRevoked: Boolean
) : RevocationStatus

/*
Revocation check parameters
 */
interface RevocationCheckParameter

data class StatusListRevocationCheckParameter(
    val credentialStatus: StatusList2021EntryCredentialStatus,
) : RevocationCheckParameter

/*
Revocation config
 */
interface RevocationConfig

data class StatusListRevocationConfig(
    val credentialStatus: StatusList2021EntryCredentialStatus,
) : RevocationConfig

/*
Revocation results
 */
@Serializable
data class RevocationResult(
    val succeed: Boolean,
    val message: String = ""
)

@Serializable
data class CredentialStatusCredential(
    var credentialStatus: CredentialStatus? = null
)
