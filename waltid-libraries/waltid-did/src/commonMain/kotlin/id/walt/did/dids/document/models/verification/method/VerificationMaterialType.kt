package id.walt.did.dids.document.models.verification.method

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VerificationMaterialType {
    @SerialName("publicKeyJwk")
    PublicKeyJwk,

    @SerialName("publicKeyMultibase")
    PublicKeyMultibase,

    @SerialName("blockchainAccountId")
    BlockchainAccountId;

    override fun toString(): String {
        return when (this) {
            PublicKeyJwk -> "publicKeyJwk"
            PublicKeyMultibase -> "publicKeyMultibase"
            BlockchainAccountId -> "blockchainAccountId"
        }
    }
}
