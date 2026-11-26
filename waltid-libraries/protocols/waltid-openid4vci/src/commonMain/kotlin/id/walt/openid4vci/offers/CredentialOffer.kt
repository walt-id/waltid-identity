package id.walt.openid4vci.offers

import id.walt.openid4vci.GrantType
import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CredentialOffer(
    @SerialName("credential_issuer")
    val credentialIssuer: String,
    @SerialName("credential_configuration_ids")
    val credentialConfigurationIds: List<String>,
    val grants: CredentialOfferGrants? = null,
) {
    init {
        require(credentialConfigurationIds.isNotEmpty()) {
            "Credential offer must include at least one credential configuration id"
        }
        require(credentialConfigurationIds.none { it.isBlank() }) {
            "Credential offer configuration ids must not be blank"
        }
        require(credentialConfigurationIds.distinct().size == credentialConfigurationIds.size) {
            "Credential offer configuration ids must be unique"
        }
        validateCredentialIssuer(credentialIssuer)
    }

    /** All explicitly offered grants in wallet preference order. An empty list requires discovery. */
    fun getGrantTypes(): List<GrantType> = buildList {
        if (grants?.authorizationCode != null) add(GrantType.AuthorizationCode)
        if (grants?.preAuthorizedCode != null) add(GrantType.PreAuthorizedCode)
    }

    /** Preferred explicitly offered grant for callers that support a single choice. */
    fun getGrantType(): GrantType? = getGrantTypes().firstOrNull()

    companion object {
        fun withAuthorizationCodeGrant(
            credentialIssuer: String,
            credentialConfigurationIds: List<String>,
            issuerState: String? = null,
            authorizationServer: String? = null,
        ): CredentialOffer =
            CredentialOffer(
                credentialIssuer = credentialIssuer,
                credentialConfigurationIds = credentialConfigurationIds,
                grants = CredentialOfferGrants(
                    authorizationCode = AuthorizationCodeGrant(
                        issuerState = issuerState,
                        authorizationServer = authorizationServer,
                    ),
                ),
            )

        fun withPreAuthorizedCodeGrant(
            credentialIssuer: String,
            credentialConfigurationIds: List<String>,
            preAuthorizedCode: String,
            txCode: TxCode? = null,
            interval: Long? = null,
            authorizationServer: String? = null,
        ): CredentialOffer =
            CredentialOffer(
                credentialIssuer = credentialIssuer,
                credentialConfigurationIds = credentialConfigurationIds,
                grants = CredentialOfferGrants(
                    preAuthorizedCode = PreAuthorizedCodeGrant(
                        preAuthorizedCode = preAuthorizedCode,
                        txCode = txCode,
                        interval = interval,
                        authorizationServer = authorizationServer,
                    ),
                ),
            )

        private fun validateCredentialIssuer(issuer: String) {
            require(issuer.isNotBlank()) {
                "Credential issuer must not be blank"
            }
            val url = Url(issuer)
            require(url.host.isNotBlank()) {
                "Credential issuer must include a host"
            }
            require(url.parameters.isEmpty()) {
                "Credential issuer must not include query parameters"
            }
            require(url.fragment.isEmpty()) {
                "Credential issuer must not include fragment components"
            }
        }
    }
}

@Serializable
data class CredentialOfferGrants(
    @SerialName(GRANT_TYPE_AUTHORIZATION_CODE)
    val authorizationCode: AuthorizationCodeGrant? = null,
    @SerialName(GRANT_TYPE_PRE_AUTHORIZED_CODE)
    val preAuthorizedCode: PreAuthorizedCodeGrant? = null,
)

@Serializable
data class AuthorizationCodeGrant(
    @SerialName("issuer_state")
    val issuerState: String? = null,
    @SerialName("authorization_server")
    val authorizationServer: String? = null,
)

@Serializable
data class PreAuthorizedCodeGrant(
    @SerialName("pre-authorized_code")
    val preAuthorizedCode: String,
    @SerialName("tx_code")
    val txCode: TxCode? = null,
    val interval: Long? = null,
    @SerialName("authorization_server")
    val authorizationServer: String? = null,
)

@Serializable
data class TxCode(
    @SerialName("input_mode")
    val inputMode: String? = null,
    val length: Int? = null,
    val description: String? = null,
)

private const val GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code"
private const val GRANT_TYPE_PRE_AUTHORIZED_CODE = "urn:ietf:params:oauth:grant-type:pre-authorized_code"

@Serializable
enum class AuthenticationMethod {
    PRE_AUTHORIZED,
    AUTHORIZED,
}

@Serializable
enum class IssuerStateMode {
    INCLUDE,
    OMIT,
}

@Serializable
enum class CredentialOfferValueMode {
    BY_REFERENCE,
    BY_VALUE,
}
