package id.walt.issuer2.testsupport

import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.CredentialOfferValueMode
import id.walt.openid4vci.offers.IssuerStateMode

enum class Issuer2TxCodeMode {
    NONE,
    GENERATED,
    PROVIDED,
}

enum class Issuer2AuthorizationRequestMode {
    SCOPE,
    AUTHORIZATION_DETAILS,
}

data class Issuer2FlowVariant(
    val id: String,
    val authMethod: AuthenticationMethod,
    val valueMode: CredentialOfferValueMode? = null,
    val txCodeMode: Issuer2TxCodeMode? = null,
    val issuerStateMode: IssuerStateMode? = null,
    val authorizationRequestMode: Issuer2AuthorizationRequestMode? = null,
    val offerless: Boolean = false,
) {
    init {
        // The matrix is intentionally strict: invalid combinations should fail
        // in the test setup instead of producing unclear protocol assertions later.
        when {
            authMethod == AuthenticationMethod.PRE_AUTHORIZED -> {
                require(!offerless) { "Pre-authorized variants require an offer" }
                require(valueMode != null) { "Pre-authorized variants require an offer value mode" }
                require(txCodeMode != null) { "Pre-authorized variants require a txCode mode" }
                require(issuerStateMode == null) { "Pre-authorized variants must not use issuer_state" }
                require(authorizationRequestMode == null) { "Pre-authorized variants must not use authorization requests" }
            }

            offerless -> {
                require(valueMode == null) { "Offerless variants must not use an offer value mode" }
                require(txCodeMode == null) { "Offerless variants must not use txCode" }
                require(issuerStateMode == null) { "Offerless variants must not use issuer_state" }
                require(authorizationRequestMode != null) { "Offerless variants require an authorization request mode" }
            }

            else -> {
                require(valueMode != null) { "Authorization-code offer variants require an offer value mode" }
                require(txCodeMode == null) { "Authorization-code offer variants must not use txCode" }
                require(issuerStateMode != null) { "Authorization-code offer variants require issuerStateMode" }
                require(authorizationRequestMode != null) { "Authorization-code offer variants require an authorization request mode" }
            }
        }
    }
}

object Issuer2FlowVariants {
    const val PROVIDED_TX_CODE_VALUE = "123456"

    val preAuthorized: List<Issuer2FlowVariant> =
        listOf(
            CredentialOfferValueMode.BY_REFERENCE,
            CredentialOfferValueMode.BY_VALUE
        ).flatMap { valueMode ->
            listOf(
                Issuer2TxCodeMode.NONE,
                Issuer2TxCodeMode.GENERATED,
                Issuer2TxCodeMode.PROVIDED
            ).map { txCodeMode ->
                Issuer2FlowVariant(
                    id = "preauth-${valueMode.idPart()}-${txCodeMode.idPart()}",
                    authMethod = AuthenticationMethod.PRE_AUTHORIZED,
                    valueMode = valueMode,
                    txCodeMode = txCodeMode,
                )
            }
        }

    val authorizedOffers: List<Issuer2FlowVariant> =
        listOf(
            CredentialOfferValueMode.BY_REFERENCE,
            CredentialOfferValueMode.BY_VALUE
        ).flatMap { valueMode ->
            listOf(
                Issuer2AuthorizationRequestMode.SCOPE,
                Issuer2AuthorizationRequestMode.AUTHORIZATION_DETAILS
            ).flatMap { requestMode ->
                listOf(
                    IssuerStateMode.INCLUDE,
                    IssuerStateMode.OMIT
                ).map { issuerStateMode ->
                    Issuer2FlowVariant(
                        id = "auth-${valueMode.idPart()}-${requestMode.idPart()}-${issuerStateMode.idPart()}",
                        authMethod = AuthenticationMethod.AUTHORIZED,
                        valueMode = valueMode,
                        issuerStateMode = issuerStateMode,
                        authorizationRequestMode = requestMode,
                    )
                }
            }
        }

    val offerlessAuthorized: List<Issuer2FlowVariant> =
        listOf(
            Issuer2AuthorizationRequestMode.SCOPE,
            Issuer2AuthorizationRequestMode.AUTHORIZATION_DETAILS
        ).map { requestMode ->
            Issuer2FlowVariant(
                id = "offerless-auth-${requestMode.idPart()}",
                authMethod = AuthenticationMethod.AUTHORIZED,
                authorizationRequestMode = requestMode,
                offerless = true,
            )
        }

    val all: List<Issuer2FlowVariant> =
        preAuthorized + authorizedOffers + offerlessAuthorized

    fun preAuthorizedVariant(
        valueMode: CredentialOfferValueMode,
        txCodeMode: Issuer2TxCodeMode,
    ): Issuer2FlowVariant =
        preAuthorized.single {
            it.valueMode == valueMode && it.txCodeMode == txCodeMode
        }

    private fun CredentialOfferValueMode.idPart(): String =
        name.lowercase().replace('_', '-')

    private fun Issuer2TxCodeMode.idPart(): String =
        when (this) {
            Issuer2TxCodeMode.NONE -> "without-tx-code"
            Issuer2TxCodeMode.GENERATED -> "generated-tx-code"
            Issuer2TxCodeMode.PROVIDED -> "provided-tx-code"
        }

    private fun Issuer2AuthorizationRequestMode.idPart(): String =
        when (this) {
            Issuer2AuthorizationRequestMode.SCOPE -> "scope"
            Issuer2AuthorizationRequestMode.AUTHORIZATION_DETAILS -> "authorization-details"
        }

    private fun IssuerStateMode.idPart(): String =
        when (this) {
            IssuerStateMode.INCLUDE -> "issuer-state-included"
            IssuerStateMode.OMIT -> "issuer-state-omitted"
        }
}
