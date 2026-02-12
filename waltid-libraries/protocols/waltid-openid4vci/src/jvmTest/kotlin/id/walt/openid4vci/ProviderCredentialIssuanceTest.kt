package id.walt.openid4vci

import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.core.buildOAuth2Provider
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.offers.CredentialOfferRequest
import id.walt.openid4vci.requests.authorization.AuthorizationRequestResult
import id.walt.openid4vci.requests.token.AccessTokenRequestResult
import id.walt.openid4vci.requests.credential.CredentialRequestResult
import id.walt.openid4vci.responses.authorization.AuthorizationResponseResult
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import id.walt.openid4vci.responses.token.AccessTokenResponseResult
import id.walt.openid4vci.tokens.jwt.JwtAccessTokenService
import io.ktor.http.Url
import io.ktor.util.toMap
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProviderCredentialIssuanceTest {

    @Test
    fun `authorization flow issues signed sd-jwt credential`() = runBlocking {
        val credentialId = "test-credential"
        val issuerId = "did:example:issuer"
        val accessTokenKey = KeyManager.resolveSerializedKey(
            KeySerialization.serializeKey(JWKKey.generate(KeyType.secp256r1))
        )
        val accessTokenService = JwtAccessTokenService({ accessTokenKey })
        val config = createTestConfig(accessTokenService = accessTokenService)

        val provider = buildOAuth2Provider(config)

        // Issuer creates an IssuerState, add a validator and then
        // creates credential offer and shares it with the wallet.
        val offer = CredentialOffer.withAuthorizationCodeGrant(
            credentialIssuer = "https://issuer.example",
            credentialConfigurationIds = listOf(credentialId),
            issuerState = "issuer-state-123",
        )

        // Issuer builds an offer URL and shares it (QR code, link, etc.).
        val offerRequest = CredentialOfferRequest(credentialOffer = offer)
        val offerUrlString = offerRequest.toUrl()

        // Wallet reads the URL, extracts the offer, and reads issuer_state.
        val params = Url(offerUrlString).parameters.toMap()
        val offerPayload = params["credential_offer"]?.firstOrNull().orEmpty()
        val json = Json { encodeDefaults = false; explicitNulls = false }
        val decodedOffer = json.decodeFromString(CredentialOffer.serializer(), offerPayload)
        val offerIssuerState = decodedOffer.grants?.authorizationCode?.issuerState

        // Wallet sends an authorization request.
        val authorizationRequestWallet = buildMap {
            put("response_type", listOf("code"))
            put("client_id", listOf("demo-client"))
            put("redirect_uri", listOf("https://openid4vci.walt.id/callback"))
            put("scope", listOf("openid credential"))
            put("state", listOf("authorization-request-state"))
            offerIssuerState?.let { put("issuer_state", listOf(it)) }
        }

        // Issuer api authenticates the user and call the following:
        val authorizeResult = provider.createAuthorizationRequest(
            authorizationRequestWallet
        )

        assertTrue(authorizeResult is AuthorizationRequestResult.Success)
        val authorizeRequest = authorizeResult.request.withIssuer(issuerId)

        val session = DefaultSession(subject = "demo-subject")

        // Issuer API calls the following to issues an authorization response (code).
        val authorizeResponse = provider.createAuthorizationResponse(authorizeRequest, session)
        assertTrue(authorizeResponse is AuthorizationResponseResult.Success)
        val code = authorizeResponse.response.code

        // Issuer API provide the code to wallet via redirect
        // Wallet exchanges the code for an access token
        val accessTokenRequestWallet = mapOf(
            "grant_type" to listOf(GrantType.AuthorizationCode.value),
            "code" to listOf(code),
            "redirect_uri" to listOf("https://openid4vci.walt.id/callback"),
            "client_id" to listOf("demo-client"),
        )

        // Issuer API gets the request and call the following:
        val accessRequestResult = provider.createAccessTokenRequest(
            accessTokenRequestWallet
        )

        assertTrue(accessRequestResult is AccessTokenRequestResult.Success)
        val accessRequest = accessRequestResult.request.withIssuer(issuerId)

        // Issuer API returns the access token response.
        val accessResponse = provider.createAccessTokenResponse(accessRequest)
        assertTrue(accessResponse is AccessTokenResponseResult.Success)
        assertTrue(accessResponse.response.accessToken.isNotBlank())

        // Wallet constructs a proof JWT to bind the credential to its key.
        val holderKey = JWKKey.generate(KeyType.Ed25519)
        val proofPayload = buildJsonObject {
            put("aud", issuerId)
            put("nonce", "nonce")
        }
        val proofJwt = holderKey.signJws(
            plaintext = proofPayload.toString().toByteArray(),
            headers = mapOf("jwk" to holderKey.getPublicKey().exportJWKObject()),
        )
        val proofParam = buildJsonObject {
            put("jwt", JsonArray(listOf(JsonPrimitive(proofJwt))))
        }.toString()

        // Wallet submits a credential request; issuer parses and validates it.
        val credentialRequestResult = provider.createCredentialRequest(
            parameters = mapOf(
                "credential_configuration_id" to listOf(credentialId),
                "proofs" to listOf(proofParam),
            ),
            session = session,
        )
        assertTrue(credentialRequestResult is CredentialRequestResult.Success)
        val credentialRequest = credentialRequestResult.request.withIssuer(issuerId)

        // Issuer signs and returns the SD-JWT credential.
        val issuerKey = JWKKey.generate(KeyType.Ed25519)
        val credentialData = buildJsonObject {
            put("given_name", "Alice")
            put("family_name", "Doe")
        }

        val credentialConfigurations = mapOf(
            credentialId to CredentialConfiguration(
                format = CredentialFormat.SD_JWT_VC,
                vct = credentialId,
            )
        )
        val issuerMetadata = CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            credentialConfigurationsSupported = credentialConfigurations,
        )
        val configuration = issuerMetadata.getCredentialConfiguration(credentialId)
            ?: error("Missing credential configuration for $credentialId")
        val credentialResponse = provider.createCredentialResponse(
            request = credentialRequest,
            configuration = configuration,
            issuerKey = issuerKey,
            issuerId = issuerId,
            credentialData = credentialData,
        )

        assertTrue(credentialResponse is CredentialResponseResult.Success)
        val response = credentialResponse.response
        assertNotNull(response.credentials)
        assertEquals(1, response.credentials.size)

        // Wallet verifies the SD-JWT signature using the issuer public key.
        val credential = response.credentials.first().credential.jsonPrimitive.content
        assertTrue(credential.isNotBlank())

        val jwtPart = credential.substringBefore("~")
        val verificationResult = issuerKey.getPublicKey().verifyJws(jwtPart)
        assertTrue(verificationResult.isSuccess)
    }
}
