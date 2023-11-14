package id.walt.issuer.revocation

import com.beust.klaxon.Json
import com.beust.klaxon.Klaxon
import id.walt.credentials.w3c.VerifiableCredential
import id.walt.issuer.revocation.statuslist2021.StatusList2021EntryClientService
import id.walt.model.credential.status.CredentialStatus
import id.walt.model.credential.status.SimpleCredentialStatus2022
import id.walt.model.credential.status.StatusList2021EntryCredentialStatus
import kotlinx.serialization.Serializable

interface CredentialStatusClientService {
    fun checkRevocation(parameter: RevocationCheckParameter): RevocationStatus
    fun revoke(parameter: RevocationConfig)
    fun create(parameter: CredentialStatusFactoryParameter): CredentialStatus

    companion object {
        fun revoke(vc: VerifiableCredential): RevocationResult =
            (Klaxon().parse<CredentialStatusCredential>(vc.toJson())?.credentialStatus)?.let {
                runCatching { getClient(it).revoke(getConfig(it)) }.fold(onSuccess = {
                    RevocationResult(succeed = true)
                }, onFailure = {
                    RevocationResult(succeed = false, message = it.localizedMessage)
                })
            } ?: RevocationResult(succeed = false, message = "Verifiable credential has no credential-status property")

        fun check(vc: VerifiableCredential): RevocationStatus =
            (Klaxon().parse<CredentialStatusCredential>(vc.toJson())?.credentialStatus)?.let {
                getClient(it).checkRevocation(getParameter(it))
            } ?: throw IllegalArgumentException("Verifiable credential has no credential-status property")

        private fun getConfig(credentialStatus: CredentialStatus): RevocationConfig = when (credentialStatus) {
            is SimpleCredentialStatus2022 -> TokenRevocationConfig(baseTokenUrl = credentialStatus.id)
            is StatusList2021EntryCredentialStatus -> StatusListRevocationConfig(credentialStatus = credentialStatus)
        }

        private fun getParameter(credentialStatus: CredentialStatus): RevocationCheckParameter = when (credentialStatus) {
            is SimpleCredentialStatus2022 -> TokenRevocationCheckParameter(revocationCheckUrl = credentialStatus.id)
            is StatusList2021EntryCredentialStatus -> StatusListRevocationCheckParameter(credentialStatus = credentialStatus)
        }

        private fun getClient(credentialStatus: CredentialStatus): CredentialStatusClientService = when (credentialStatus) {
            is SimpleCredentialStatus2022 -> SimpleCredentialClientService()
            is StatusList2021EntryCredentialStatus -> StatusList2021EntryClientService()
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
data class TokenRevocationStatus(
    val token: String,
    override val isRevoked: Boolean,
    @Json(serializeNull = false)
    val timeOfRevocation: Long? = null
) : RevocationStatus
@Serializable
data class StatusListRevocationStatus(
    override val isRevoked: Boolean
) : RevocationStatus

/*
Revocation check parameters
 */
interface RevocationCheckParameter
data class TokenRevocationCheckParameter(
    val revocationCheckUrl: String,
) : RevocationCheckParameter

data class StatusListRevocationCheckParameter(
    val credentialStatus: StatusList2021EntryCredentialStatus,
) : RevocationCheckParameter

/*
Revocation config
 */
interface RevocationConfig

data class TokenRevocationConfig(
    val baseTokenUrl: String,
) : RevocationConfig

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
    @Json(serializeNull = false) var credentialStatus: CredentialStatus? = null
)
