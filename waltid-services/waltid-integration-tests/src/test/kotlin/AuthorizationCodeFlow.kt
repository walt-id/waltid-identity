import id.walt.commons.testing.E2ETest
import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.data.*
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.test.integration.environment.api.issuer.IssuerApi
import id.walt.test.integration.expectRedirect
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

//TODO: needs to be ported to JUnit test
class AuthorizationCodeFlow(private val e2e: E2ETest, private val client: HttpClient) {

    fun testIssuerAPI() = runBlocking {
        lateinit var offerUrl: String
        lateinit var issuerState: String
        val issuerApi = IssuerApi(e2e, client)
        val authorizeEndpoint = "draft13/authorize"

        //
        // Issue credential with Authorized Code Flow and Id Token request
        //
        val issuanceRequestIdToken =
            Json.decodeFromString<IssuanceRequest>(loadResource("issuance/openbadgecredential-issuance-request-with-authorization-code-flow-and-id-token.json"))
        offerUrl = issuerApi.issueJwtCredential(issuanceRequestIdToken)

        var offerUrlParams = Url(offerUrl).parameters.toMap()
        var offerObj = CredentialOfferRequest.fromHttpParameters(offerUrlParams)
        var credOffer = client.get(offerObj.credentialOfferUri!!).body<JsonObject>()
        issuerState =
            credOffer["grants"]!!.jsonObject["authorization_code"]!!.jsonObject["issuer_state"]!!.jsonPrimitive.content
        var authorizationRequest = AuthorizationRequest(
            issuerState = issuerState,
            clientId = "did:key:xzy",
            scope = setOf("openid"),
            clientMetadata = OpenIDClientMetadata(
                requestUris = listOf("openid://redirect"),
                jwksUri = "myuri.com/jwks",
                customParameters = mapOf("authorization_endpoint" to "openid://".toJsonElement()),
            ),
            requestUri = "openid://redirect",
            responseType = setOf(ResponseType.Code),
            state = "UPD4Qjo2gzBNv641YQf19BamZets1xQpkY8jYTxvqq8",
            codeChallenge = "UPD4Qjo2gzBNv641YQf19BamZets1xQpkY8jYTxvqq8",
            codeChallengeMethod = "S256",
            authorizationDetails = listOf(
                AuthorizationDetails(
                    type = "openid_credential",
                    locations = listOf(credOffer["credential_issuer"]!!.jsonPrimitive.content),
                    format = CredentialFormat.jwt_vc,
                    credentialDefinition = CredentialDefinition(
                        type = listOf(
                            "VerifiableCredential",
                            "OpenBadgeCredential"
                        )
                    )
                )
            )
        )

        client.get("$authorizeEndpoint?${authorizationRequest.toHttpQueryString()}") {}
            .expectRedirect().apply {
                val idTokenRequest = AuthorizationRequest.fromHttpQueryString(headers["location"]!!)
                assertEquals(
                    setOf(ResponseType.IdToken),
                    idTokenRequest.responseType,
                    "response type should be id_token"
                )
                assertEquals(
                    ResponseMode.direct_post,
                    idTokenRequest.responseMode,
                    "response mode should be direct post"
                )
            }

        //
        // Issue credential with Authorized Code Flow and Vp Token request
        //
        val issuanceRequestVpToken =
            Json.decodeFromString<IssuanceRequest>(loadResource("issuance/openbadgecredential-issuance-request-with-authorization-code-flow-and-vp-token.json"))
        offerUrl = issuerApi.issueJwtCredential(issuanceRequestVpToken)

        offerUrlParams = Url(offerUrl).parameters.toMap()
        offerObj = CredentialOfferRequest.fromHttpParameters(offerUrlParams)
        credOffer = client.get(offerObj.credentialOfferUri!!).body<JsonObject>()
        issuerState =
            credOffer["grants"]!!.jsonObject["authorization_code"]!!.jsonObject["issuer_state"]!!.jsonPrimitive.content
        authorizationRequest = authorizationRequest.copy(
            issuerState = issuerState,
            authorizationDetails = listOf(
                AuthorizationDetails(
                    type = "openid_credential",
                    locations = listOf(credOffer["credential_issuer"]!!.jsonPrimitive.content),
                    format = CredentialFormat.jwt_vc,
                    credentialDefinition = CredentialDefinition(
                        type = listOf(
                            "VerifiableCredential",
                            "OpenBadgeCredential"
                        )
                    )
                )
            )
        )

        client.get("$authorizeEndpoint?${authorizationRequest.toHttpQueryString()}") {
        }.expectRedirect().apply {
            val vpTokenRequest = AuthorizationRequest.fromHttpQueryString(headers["location"]!!)
            assertEquals(setOf(ResponseType.VpToken), vpTokenRequest.responseType, "response type should be vp_token")
            assertEquals(ResponseMode.direct_post, vpTokenRequest.responseMode, "response mode should be direct post")
            assertNotNull(vpTokenRequest.presentationDefinition, "presentation definition should exists")
        }

        //
        // Issue credential with Authorized Code Flow and username/password request
        //
        val issuanceRequestPwd =
            Json.decodeFromString<IssuanceRequest>(loadResource("issuance/openbadgecredential-issuance-request-with-authorization-code-flow-and-pwd.json"))
        offerUrl = issuerApi.issueJwtCredential(issuanceRequestPwd)

        offerUrlParams = Url(offerUrl).parameters.toMap()
        offerObj = CredentialOfferRequest.fromHttpParameters(offerUrlParams)
        credOffer = client.get(offerObj.credentialOfferUri!!).body<JsonObject>()
        issuerState =
            credOffer["grants"]!!.jsonObject["authorization_code"]!!.jsonObject["issuer_state"]!!.jsonPrimitive.content
        authorizationRequest = authorizationRequest.copy(
            issuerState = issuerState,
            authorizationDetails = listOf(
                AuthorizationDetails(
                    type = "openid_credential",
                    locations = listOf(credOffer["credential_issuer"]!!.jsonPrimitive.content),
                    format = CredentialFormat.jwt_vc,
                    credentialDefinition = CredentialDefinition(
                        type = listOf(
                            "VerifiableCredential",
                            "OpenBadgeCredential"
                        )
                    )
                )
            )
        )

        client.get("$authorizeEndpoint?${authorizationRequest.toHttpQueryString()}") {
        }.expectRedirect().apply {
            assertEquals(true, headers["location"]!!.contains("external_login"))
        }
    }


}
