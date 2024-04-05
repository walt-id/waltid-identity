package id.walt.webwallet.service.events

import kotlinx.serialization.Serializable

@Serializable
sealed class EventData

@Serializable
data class AccountEventData(
    val accountId: String? = null,
    val walletId: String? = null,
) : EventData()

@Serializable
data class KeyEventData(
    val id: String,
    val algorithm: String,
    val keyManagementService: String,
) : EventData()

@Serializable
data class DidEventData(
    val did: String,
    val document: String,
) : EventData()

@Serializable
data class CredentialEventData(
    val ecosystem: String,
    val logo: String?,
    val issuerId: String,
    val issuerName: String?,
    val subjectId: String,
    val issuerKeyId: String,
    val issuerKeyType: String,
    val subjectKeyType: String,
    val credentialType: String,
    val credentialFormat: String,
    val credentialProofType: String,
    val policies: List<String>,
    val protocol: String,
    val credentialId: String,
) : EventData()