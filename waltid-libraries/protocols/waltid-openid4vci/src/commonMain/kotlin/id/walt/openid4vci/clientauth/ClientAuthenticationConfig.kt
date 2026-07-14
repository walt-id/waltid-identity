package id.walt.openid4vci.clientauth

import id.walt.openid4vci.GrantType
import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationKeyReferenceResolver
import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerifierConfig
import id.walt.openid4vci.clientauth.attestation.verifier.toClientAttestationConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientAuthenticationConfig(
    val supportedMethods: List<ClientAuthenticationMethodConfig> = emptyList(),
) {
    init {
        require(supportedMethods.filterIsInstance<ClientAuthenticationMethodConfig.ClientAttestation>().size <= 1) {
            "Only one client-attestation method configuration is supported"
        }
        require(supportedMethods.filterIsInstance<ClientAuthenticationMethodConfig.PreAuthAnonymous>().size <= 1) {
            "Only one preauth-anonymous method configuration is supported"
        }
    }

    fun clientAttestationMethod(): ClientAuthenticationMethodConfig.ClientAttestation? =
        supportedMethods.filterIsInstance<ClientAuthenticationMethodConfig.ClientAttestation>().singleOrNull()

    fun supportsPreAuthAnonymous(): Boolean =
        supportedMethods.any { it is ClientAuthenticationMethodConfig.PreAuthAnonymous }

    fun allowsAnonymousPreAuthorizedCodeTokenRequest(
        parameters: Map<String, List<String>>,
        headers: Map<String, List<String>>,
    ): Boolean =
        supportsPreAuthAnonymous() &&
            isAnonymousPreAuthorizedCodeTokenRequest(parameters, headers)

    suspend fun toClientAuthenticationServiceConfig(
        keyReferenceResolver: ClientAttestationKeyReferenceResolver? = null,
    ): ClientAuthenticationServiceConfig {
        val attestationMethod = clientAttestationMethod()
            ?.config
            ?.toClientAttestationConfig(keyReferenceResolver)
            ?.toAuthenticationMethod()
            ?: return ClientAuthenticationServiceConfig()

        return ClientAuthenticationServiceConfig(
            methods = listOf(attestationMethod),
        ).withDefaultMethodsByEndpoint(
            mapOf(
                ClientAuthenticationEndpoint.PUSHED_AUTHORIZATION to setOf(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH),
                ClientAuthenticationEndpoint.TOKEN to setOf(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH),
            )
        )
    }
}

@Serializable
sealed class ClientAuthenticationMethodConfig {
    @Serializable
    @SerialName("preauth-anonymous")
    data object PreAuthAnonymous : ClientAuthenticationMethodConfig()

    @Serializable
    @SerialName("client-attestation")
    data class ClientAttestation(
        val config: ClientAttestationVerifierConfig,
    ) : ClientAuthenticationMethodConfig()
}

fun isAnonymousPreAuthorizedCodeTokenRequest(
    parameters: Map<String, List<String>>,
    headers: Map<String, List<String>>,
): Boolean =
    parameters.singleNonBlankValue("grant_type") == GrantType.PreAuthorizedCode.value &&
        parameters.hasNoClientId() &&
        !ClientAuthenticationMethodDetector.hasClientAuthenticationInput(parameters, headers)

private fun Map<String, List<String>>.singleNonBlankValue(name: String): String? =
    this[name].orEmpty()
        .filter { it.isNotBlank() }
        .singleOrNull()

private fun Map<String, List<String>>.hasNoClientId(): Boolean =
    this["client_id"].orEmpty().none { it.isNotBlank() }