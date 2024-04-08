package id.walt.webwallet.service.events

import kotlinx.serialization.SerialName
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
    val credentialId: String,
    val ecosystem: String,
    val logo: String?,
    @SerialName("credentialType")
    val type: String,
    val format: String,
    val proofType: String,
    val protocol: String,
    val subject: CredentialEventDataActor.Subject? = null,
    val organization: CredentialEventDataActor.Organization? = null,
) : EventData()

@Serializable
sealed interface CredentialEventDataActor {
    @Serializable
    data class Subject(
        val subjectId: String,
        val subjectKeyType: String,
    ) : CredentialEventDataActor

    //TODO better naming
    @Serializable
    sealed interface Organization : CredentialEventDataActor {
        @Serializable
        data class Issuer(
            val did: String,
            val name: String? = null,
            val keyId: String,
            val keyType: String,
        ) : Organization

        @Serializable
        data class Verifier(
            val did: String,
            val name: String? = null,
            val policies: List<String>,
        ) : Organization
    }
}