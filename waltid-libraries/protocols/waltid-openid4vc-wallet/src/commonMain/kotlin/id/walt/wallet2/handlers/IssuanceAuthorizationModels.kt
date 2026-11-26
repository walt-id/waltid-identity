@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.handlers

import id.walt.wallet2.data.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class GenerateAuthorizationUrlRequest(
    override val offerUrl: Url? = null,
    override val offerJson: JsonObject? = null,
    val clientId: String = Wallet2DefaultClientId,
    val redirectUri: Url = Url("openid://"),
    val usePkce: Boolean = true,
    /**
     * Request the credential by OAuth `scope` instead of `authorization_details`.
     *
     * OID4VCI 1.0 Section 5.1.2 defines both, as alternatives. `authorization_details` is the default
     * because it names the credential configuration directly; scope-based authorization needs the
     * issuer to publish a `scope` on the configuration, and some profiles (HAIP among them) require it.
     */
    val useScope: Boolean = false,
) : CredentialOfferSource {
    init {
        checkOfferSource()
    }
}

@Serializable
data class GenerateAuthorizationUrlResult(
    val authorizationUrl: Url,
    val state: String,
    val codeVerifier: String? = null,
    val credentialConfigurationId: String,
    val credentialIssuerBaseUrl: String,
    val nonceEndpoint: Url? = null,
)

@Serializable
data class GenerateBatchAuthorizationUrlRequest(
    /** Wallet-initiated issuance without an offer. Mutually exclusive with offerUrl/offerJson. */
    val credentialIssuer: String? = null,
    val credentialConfigurationIds: List<String>? = null,
    override val offerUrl: Url? = null,
    override val offerJson: JsonObject? = null,
    val clientId: String = Wallet2DefaultClientId,
    val redirectUri: Url = Url("openid://"),
    val usePkce: Boolean = true,
) : CredentialOfferSource {
    init {
        require(credentialConfigurationIds == null || (credentialConfigurationIds.isNotEmpty() &&
                credentialConfigurationIds.all(String::isNotBlank) &&
                credentialConfigurationIds.distinct().size == credentialConfigurationIds.size)) {
            "Select non-empty, distinct credential configuration IDs"
        }
        if (credentialIssuer == null) checkOfferSource() else {
            require(offerUrl == null && offerJson == null)
            require(credentialIssuer.isNotBlank() && !credentialConfigurationIds.isNullOrEmpty())
        }
    }
}

@Serializable
data class GenerateBatchAuthorizationUrlResult(
    val authorizationUrl: Url,
    val state: String,
    val codeVerifier: String? = null,
    val credentialConfigurationIds: List<String>,
    val credentialIssuerBaseUrl: String,
    val nonceEndpoint: Url? = null,
) {
    init {
        require(credentialConfigurationIds.isNotEmpty() && credentialConfigurationIds.all(String::isNotBlank) &&
                credentialConfigurationIds.distinct().size == credentialConfigurationIds.size)
    }
}

@Serializable
data class ExchangeCodeRequest(
    val code: String,
    /** Used to resolve AS metadata, including token endpoint, issuer, and token auth methods. */
    val credentialIssuerBaseUrl: String,
    val codeVerifier: String? = null,
    val clientId: String = Wallet2DefaultClientId,
    val redirectUri: Url = Url("openid://"),
    /** Manual client authentication; see [ReceiveCredentialRequest.tokenRequestHeaders]. */
    val tokenRequestHeaders: Map<String, String> = emptyMap(),
)

/**
 * Thrown when an issuer's only token authentication method is a wallet instance attestation
 * and this wallet has no provider that can obtain one.
 *
 * A pre-authorized grant the issuer allows anonymously does not throw. That grant can be
 * redeemed without a wallet instance attestation.
 */
public class WalletInstanceAttestationRequiredException(
    message: String = "This issuer requires a wallet instance attestation, and no wallet provider is configured",
) : IllegalStateException(message)
