package id.walt.issuer2.config

import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerifierConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientAuthenticationConfig(
    val supportedMethods: List<ClientAuthenticationMethod> = emptyList(),
) {
    init {
        require(supportedMethods.filterIsInstance<ClientAuthenticationMethod.ClientAttestation>().size <= 1) {
            "Only one client-attestation method configuration is supported"
        }
        require(supportedMethods.filterIsInstance<ClientAuthenticationMethod.PreAuthAnonymous>().size <= 1) {
            "Only one preauth-anonymous method configuration is supported"
        }
    }

    fun clientAttestationMethod(): ClientAuthenticationMethod.ClientAttestation? =
        supportedMethods.filterIsInstance<ClientAuthenticationMethod.ClientAttestation>().singleOrNull()

    fun supportsPreAuthAnonymous(): Boolean =
        supportedMethods.any { it is ClientAuthenticationMethod.PreAuthAnonymous }
}

@Serializable
sealed class ClientAuthenticationMethod {
    @Serializable
    @SerialName("preauth-anonymous")
    data object PreAuthAnonymous : ClientAuthenticationMethod()

    @Serializable
    @SerialName("client-attestation")
    data class ClientAttestation(
        val config: ClientAttestationVerifierConfig,
    ) : ClientAuthenticationMethod()
}
