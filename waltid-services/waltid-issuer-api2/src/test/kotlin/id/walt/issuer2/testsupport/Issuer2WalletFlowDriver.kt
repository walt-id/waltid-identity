package id.walt.issuer2.testsupport

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.did.dids.registrar.local.jwk.DidJwkRegistrar
import id.walt.issuer2.models.CredentialOfferCreateResponse
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.offers.IssuerStateMode
import id.walt.openid4vci.prooftypes.Proofs
import id.waltid.openid4vci.wallet.authorization.AuthorizationRequestBuilder
import id.waltid.openid4vci.wallet.metadata.IssuerMetadataResolver
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import id.waltid.openid4vci.wallet.offer.CredentialOfferParser
import id.waltid.openid4vci.wallet.offer.CredentialOfferResolver
import id.waltid.openid4vci.wallet.proof.JwtProofBuilder
import id.waltid.openid4vci.wallet.token.TokenRequestBuilder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.parseQueryString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class Issuer2WalletFlowDriver(
    private val client: HttpClient,
    private val walletClientConfig: ClientConfiguration = ClientConfiguration(
        clientId = "issuer2-wallet-test",
        redirectUris = listOf("https://wallet.example/callback"),
    ),
) {
    suspend fun resolve(createdOffer: CredentialOfferCreateResponse): ResolvedCredentialOffer {
        val offerRequest = CredentialOfferParser.parseCredentialOfferUrl(createdOffer.credentialOffer)
        val offer = CredentialOfferResolver(client).resolveCredentialOffer(
            credentialOffer = offerRequest.credentialOffer,
            credentialOfferUri = offerRequest.credentialOfferUri,
        )
        val issuerMetadata = IssuerMetadataResolver(client).resolveCredentialIssuerMetadata(offer.credentialIssuer)
        val authorizationServerMetadata = IssuerMetadataResolver(client)
            .resolveAuthorizationServerMetadataWithFallback(issuerMetadata)

        return ResolvedCredentialOffer(
            offer = offer,
            issuerMetadata = issuerMetadata,
            authorizationServerMetadata = authorizationServerMetadata,
        )
    }

    suspend fun exchangePreAuthorizedCode(
        resolvedOffer: ResolvedCredentialOffer,
        txCode: String?,
    ): TokenRequestBuilder.TokenResponse {
        val preAuthorizedCode = requireNotNull(resolvedOffer.offer.grants?.preAuthorizedCode?.preAuthorizedCode)
        return TokenRequestBuilder(walletClientConfig, client).exchangePreAuthorizedCode(
            tokenEndpoint = requireNotNull(resolvedOffer.authorizationServerMetadata.tokenEndpoint),
            preAuthorizedCode = preAuthorizedCode,
            txCode = txCode,
        )
    }

    suspend fun exchangePreAuthorizedCode(
        createdOffer: CredentialOfferCreateResponse,
        resolvedOffer: ResolvedCredentialOffer,
        variant: Issuer2FlowVariant,
    ): TokenRequestBuilder.TokenResponse {
        require(variant.authMethod == AuthenticationMethod.PRE_AUTHORIZED) {
            "Only PRE_AUTHORIZED variants use the pre-authorized code grant"
        }
        val txCode = when (variant.txCodeMode) {
            Issuer2TxCodeMode.NONE -> null
            Issuer2TxCodeMode.GENERATED -> requireNotNull(createdOffer.txCodeValue)
            Issuer2TxCodeMode.PROVIDED -> Issuer2FlowVariants.PROVIDED_TX_CODE_VALUE
            null -> null
        }
        return exchangePreAuthorizedCode(resolvedOffer, txCode)
    }

    suspend fun startAuthorizationCodeFlowWithIssuerState(
        createdOffer: CredentialOfferCreateResponse,
        resolvedOffer: ResolvedCredentialOffer,
        scenario: Issuer2CredentialScenario = Issuer2CredentialScenarios.universityDegree,
        requestMode: Issuer2AuthorizationRequestMode = Issuer2AuthorizationRequestMode.AUTHORIZATION_DETAILS,
    ): String {
        val issuerState = resolvedOffer.offer.grants?.authorizationCode?.issuerState
        assertEquals(createdOffer.offerId, issuerState)

        val redirectUri = startAuthorizationCodeFlow(
            resolvedOffer = resolvedOffer,
            scenario = scenario,
            issuerState = issuerState,
            requestMode = requestMode,
        )
        val internalAuthorizationRequest = redirectUri.substringAfter("/external_login/")
        assertEquals(issuerState, parseQueryString(internalAuthorizationRequest)["issuer_state"])
        assertAuthorizationRequestMode(internalAuthorizationRequest, scenario, requestMode)
        return redirectUri
    }

    suspend fun startAuthorizationCodeFlowWithoutIssuerState(
        resolvedOffer: ResolvedCredentialOffer,
        scenario: Issuer2CredentialScenario = Issuer2CredentialScenarios.universityDegree,
        requestMode: Issuer2AuthorizationRequestMode = Issuer2AuthorizationRequestMode.AUTHORIZATION_DETAILS,
    ): String {
        val redirectUri = startAuthorizationCodeFlow(
            resolvedOffer = resolvedOffer,
            scenario = scenario,
            issuerState = null,
            requestMode = requestMode,
        )
        val internalAuthorizationRequest = redirectUri.substringAfter("/external_login/")
        assertEquals(null, parseQueryString(internalAuthorizationRequest)["issuer_state"])
        assertAuthorizationRequestMode(internalAuthorizationRequest, scenario, requestMode)
        return redirectUri
    }

    suspend fun startAuthorizationCodeFlow(
        resolvedOffer: ResolvedCredentialOffer,
        scenario: Issuer2CredentialScenario,
        variant: Issuer2FlowVariant,
    ): String {
        require(!variant.offerless) { "Use startOfferlessAuthorizationCodeFlow for offerless variants" }
        require(variant.authMethod == AuthenticationMethod.AUTHORIZED) {
            "Only AUTHORIZED variants use the authorization endpoint"
        }
        val issuerState = when (variant.issuerStateMode) {
            IssuerStateMode.INCLUDE -> resolvedOffer.offer.grants?.authorizationCode?.issuerState
            IssuerStateMode.OMIT -> null
            null -> null
        }
        val redirectUri = startAuthorizationCodeFlow(
            resolvedOffer = resolvedOffer,
            scenario = scenario,
            issuerState = issuerState,
            requestMode = requireNotNull(variant.authorizationRequestMode),
        )
        val internalAuthorizationRequest = redirectUri.substringAfter("/external_login/")
        assertEquals(issuerState, parseQueryString(internalAuthorizationRequest)["issuer_state"])
        assertAuthorizationRequestMode(internalAuthorizationRequest, scenario, variant.authorizationRequestMode)
        return redirectUri
    }

    suspend fun startOfferlessAuthorizationCodeFlow(
        scenario: Issuer2CredentialScenario,
        requestMode: Issuer2AuthorizationRequestMode,
        credentialIssuer: String = DEFAULT_CREDENTIAL_ISSUER,
    ): String {
        val issuerMetadata = IssuerMetadataResolver(client).resolveCredentialIssuerMetadata(credentialIssuer)
        val authorizationServerMetadata = IssuerMetadataResolver(client)
            .resolveAuthorizationServerMetadataWithFallback(issuerMetadata)
        val authorizationUrl = buildAuthorizationRequestUrl(
            authorizationEndpoint = requireNotNull(authorizationServerMetadata.authorizationEndpoint),
            scenario = scenario,
            issuerState = null,
            requestMode = requestMode,
        )
        val redirectUri = sendAuthorizationRequest(authorizationUrl)
        val internalAuthorizationRequest = redirectUri.substringAfter("/external_login/")
        assertEquals(null, parseQueryString(internalAuthorizationRequest)["issuer_state"])
        assertAuthorizationRequestMode(internalAuthorizationRequest, scenario, requestMode)
        return redirectUri
    }

    suspend fun startOfferlessAuthorizationCodeFlow(
        scenario: Issuer2CredentialScenario,
        variant: Issuer2FlowVariant,
        credentialIssuer: String = DEFAULT_CREDENTIAL_ISSUER,
    ): String {
        require(variant.offerless) { "Only offerless variants can start without a credential offer" }
        return startOfferlessAuthorizationCodeFlow(
            scenario = scenario,
            requestMode = requireNotNull(variant.authorizationRequestMode),
            credentialIssuer = credentialIssuer,
        )
    }

    fun buildAuthorizationRequestUrl(
        resolvedOffer: ResolvedCredentialOffer,
        scenario: Issuer2CredentialScenario,
        issuerState: String?,
        requestMode: Issuer2AuthorizationRequestMode = Issuer2AuthorizationRequestMode.AUTHORIZATION_DETAILS,
    ): String =
        buildAuthorizationRequestUrl(
            authorizationEndpoint = requireNotNull(resolvedOffer.authorizationServerMetadata.authorizationEndpoint),
            scenario = scenario,
            issuerState = issuerState,
            requestMode = requestMode,
        )

    suspend fun exchangeAuthorizationCode(
        resolvedOffer: ResolvedCredentialOffer,
        code: String,
    ): TokenRequestBuilder.TokenResponse =
        TokenRequestBuilder(walletClientConfig, client).exchangeAuthorizationCode(
            tokenEndpoint = requireNotNull(resolvedOffer.authorizationServerMetadata.tokenEndpoint),
            code = code,
        )

    suspend fun requestCredential(
        resolvedOffer: ResolvedCredentialOffer,
        accessToken: String,
        credentialConfigurationId: String = resolvedOffer.offer.credentialConfigurationIds.single(),
    ): JsonObject {
        val proofs = buildJwtProofs(resolvedOffer.issuerMetadata)
        val response = client.post(resolvedOffer.issuerMetadata.credentialEndpoint) {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                credentialRequest(
                    credentialConfigurationId = credentialConfigurationId,
                    proofs = proofs,
                )
            )
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return response.body()
    }

    private suspend fun startAuthorizationCodeFlow(
        resolvedOffer: ResolvedCredentialOffer,
        scenario: Issuer2CredentialScenario,
        issuerState: String?,
        requestMode: Issuer2AuthorizationRequestMode,
    ): String {
        val authorizationUrl = buildAuthorizationRequestUrl(
            authorizationEndpoint = requireNotNull(resolvedOffer.authorizationServerMetadata.authorizationEndpoint),
            scenario = scenario,
            issuerState = issuerState,
            requestMode = requestMode,
        )
        return sendAuthorizationRequest(authorizationUrl)
    }

    private suspend fun sendAuthorizationRequest(authorizationUrl: String): String {
        val authorizationResponse = client.get(authorizationUrl)
        assertEquals(HttpStatusCode.Found, authorizationResponse.status, authorizationResponse.bodyAsText())
        return assertNotNull(authorizationResponse.headers[HttpHeaders.Location])
    }

    private fun buildAuthorizationRequestUrl(
        authorizationEndpoint: String,
        scenario: Issuer2CredentialScenario,
        issuerState: String?,
        requestMode: Issuer2AuthorizationRequestMode,
    ): String =
        when (requestMode) {
            Issuer2AuthorizationRequestMode.AUTHORIZATION_DETAILS ->
                AuthorizationRequestBuilder(walletClientConfig).buildAuthorizationRequest(
                    authorizationEndpoint = authorizationEndpoint,
                    credentialConfigurationId = scenario.credentialConfigurationId,
                    issuerState = issuerState,
                    usePKCE = false,
                ).url

            // The wallet helper currently builds authorization_details requests.
            // Scope-based tests build the URL directly so we can assert the server
            // supports both OpenID4VCI authorization request styles.
            Issuer2AuthorizationRequestMode.SCOPE ->
                URLBuilder(authorizationEndpoint).apply {
                    parameters.append("response_type", "code")
                    parameters.append("client_id", walletClientConfig.clientId)
                    parameters.append("redirect_uri", walletClientConfig.redirectUris.first())
                    parameters.append("state", "issuer2-wallet-test-${UUID.randomUUID()}")
                    parameters.append("scope", scenario.authorizationScope)
                    issuerState?.let { parameters.append("issuer_state", it) }
                }.buildString()
        }

    private fun assertAuthorizationRequestMode(
        internalAuthorizationRequest: String,
        scenario: Issuer2CredentialScenario,
        requestMode: Issuer2AuthorizationRequestMode,
    ) {
        val parameters = parseQueryString(internalAuthorizationRequest)
        when (requestMode) {
            Issuer2AuthorizationRequestMode.SCOPE -> {
                assertEquals(scenario.authorizationScope, parameters["scope"])
                assertEquals(null, parameters["authorization_details"])
            }

            Issuer2AuthorizationRequestMode.AUTHORIZATION_DETAILS -> {
                assertNotNull(parameters["authorization_details"])
                assertEquals(null, parameters["scope"])
            }
        }
    }

    suspend fun buildJwtProofs(issuerMetadata: CredentialIssuerMetadata): Proofs {
        val nonceResponse = client.post(requireNotNull(issuerMetadata.nonceEndpoint)).body<JsonObject>()
        val proofKey = JWKKey.generate(KeyType.secp256r1)
        val holderDid = DidJwkRegistrar()
            .registerByKey(proofKey, DidJwkCreateOptions(KeyType.secp256r1))
            .did

        return JwtProofBuilder().buildJwtProof(
            key = proofKey,
            audience = issuerMetadata.credentialIssuer,
            nonce = requireNotNull(nonceResponse["c_nonce"]?.jsonPrimitive?.contentOrNull),
            keyId = "$holderDid#0",
        )
    }
}

data class ResolvedCredentialOffer(
    val offer: CredentialOffer,
    val issuerMetadata: CredentialIssuerMetadata,
    val authorizationServerMetadata: AuthorizationServerMetadata,
)

private const val DEFAULT_CREDENTIAL_ISSUER = "$KTOR_TEST_APPLICATION_BASE_URL/openid4vci"