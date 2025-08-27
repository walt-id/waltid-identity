import id.walt.commons.testing.E2ETest
import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.OpenID4VCI.parseAndResolveCredentialOfferRequestUrl
import id.walt.oid4vc.OpenID4VCI.resolveCIProviderMetadata
import id.walt.oid4vc.data.*
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialOfferRequest
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
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull


class AuthorizationCodeFlow(private val e2e: E2ETest, private val client: HttpClient) {

    fun testIssuerAPI() = runBlocking {
        lateinit var credentialOfferUrl: String
        lateinit var issuerState: String
        val issuerApi = IssuerApi(e2e, client)
        val authorizeEndpoint = "draft13/authorize"

        //
        // Issue credential with Authorized Code Flow and Id Token request
        //
        val issuanceRequestIdToken =
            Json.decodeFromString<IssuanceRequest>(loadResource("issuance/openbadgecredential-issuance-request-with-authorization-code-flow-and-id-token.json"))
        issuerApi.jwt(issuanceRequestIdToken) {
            credentialOfferUrl = it
            println("offer: $credentialOfferUrl")
        }


        var credentialOfferUrlParams = Url(credentialOfferUrl).parameters.toMap()
        var credentialOfferRequest = CredentialOfferRequest.fromHttpParameters(credentialOfferUrlParams)
        var credentialOfferJsonObject = client.get(credentialOfferRequest.credentialOfferUri!!).body<JsonObject>()


        val credentialOffer = parseAndResolveCredentialOfferRequestUrl(credentialOfferUrl)
        val issuerMetadata = resolveCIProviderMetadata(credentialOffer) as OpenIDProviderMetadata.Draft13

        assertNull(issuerMetadata.pushedAuthorizationRequestEndpoint)

        issuerState =
            credentialOfferJsonObject["grants"]!!.jsonObject["authorization_code"]!!.jsonObject["issuer_state"]!!.jsonPrimitive.content

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
                    locations = listOf(credentialOfferJsonObject["credential_issuer"]!!.jsonPrimitive.content),
                    format = CredentialFormat.jwt_vc,
                    credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "OpenBadgeCredential"))
                )
            )
        )

        client.get("$authorizeEndpoint?${authorizationRequest.toHttpQueryString()}") {}
            .expectRedirect().apply {
                val idTokenRequest = AuthorizationRequest.fromHttpQueryString(headers["location"]!!)
                kotlin.test.assertTrue(idTokenRequest.responseType.size == 1, "response type should be id_token")
                kotlin.test.assertTrue(idTokenRequest.responseType.first() == ResponseType.IdToken, "response type should be id_token")

                kotlin.test.assertTrue(idTokenRequest.responseMode == ResponseMode.direct_post, "response mode should be direct post")
            }

        //
        // Issue credential with Authorized Code Flow and Vp Token request
        //
        val issuanceRequestVpToken =
            Json.decodeFromString<IssuanceRequest>(loadResource("issuance/openbadgecredential-issuance-request-with-authorization-code-flow-and-vp-token.json"))
        issuerApi.jwt(issuanceRequestVpToken) {
            credentialOfferUrl = it
            println("offer: $credentialOfferUrl")
        }

        credentialOfferUrlParams = Url(credentialOfferUrl).parameters.toMap()
        credentialOfferRequest = CredentialOfferRequest.fromHttpParameters(credentialOfferUrlParams)
        credentialOfferJsonObject = client.get(credentialOfferRequest.credentialOfferUri!!).body<JsonObject>()
        issuerState =
            credentialOfferJsonObject["grants"]!!.jsonObject["authorization_code"]!!.jsonObject["issuer_state"]!!.jsonPrimitive.content
        authorizationRequest = authorizationRequest.copy(
            issuerState = issuerState,
            authorizationDetails = listOf(
                AuthorizationDetails(
                    type = "openid_credential",
                    locations = listOf(credentialOfferJsonObject["credential_issuer"]!!.jsonPrimitive.content),
                    format = CredentialFormat.jwt_vc,
                    credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "OpenBadgeCredential"))
                )
            )
        )

        client.get("$authorizeEndpoint?${authorizationRequest.toHttpQueryString()}") {
        }.expectRedirect().apply {
            val vpTokenRequest = AuthorizationRequest.fromHttpQueryString(headers["location"]!!)
            assertTrue(vpTokenRequest.responseType == setOf(ResponseType.VpToken)) { "response type should be vp_token" }
            assertTrue(vpTokenRequest.responseMode == ResponseMode.direct_post) { "response mode should be direct post" }
            assertTrue(vpTokenRequest.presentationDefinition != null) { "presentation definition should exists" }
        }

        //
        // Issue credential with Authorized Code Flow and username/password request
        //
        val issuanceRequestPwd =
            Json.decodeFromString<IssuanceRequest>(loadResource("issuance/openbadgecredential-issuance-request-with-authorization-code-flow-and-pwd.json"))
        issuerApi.jwt(issuanceRequestPwd) {
            credentialOfferUrl = it
            println("offer: $credentialOfferUrl")
        }

        credentialOfferUrlParams = Url(credentialOfferUrl).parameters.toMap()
        credentialOfferRequest = CredentialOfferRequest.fromHttpParameters(credentialOfferUrlParams)
        credentialOfferJsonObject = client.get(credentialOfferRequest.credentialOfferUri!!).body<JsonObject>()
        issuerState =
            credentialOfferJsonObject["grants"]!!.jsonObject["authorization_code"]!!.jsonObject["issuer_state"]!!.jsonPrimitive.content
        authorizationRequest = authorizationRequest.copy(
            issuerState = issuerState,
            authorizationDetails = listOf(
                AuthorizationDetails(
                    type = "openid_credential",
                    locations = listOf(credentialOfferJsonObject["credential_issuer"]!!.jsonPrimitive.content),
                    format = CredentialFormat.jwt_vc,
                    credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "OpenBadgeCredential"))
                )
            )
        )

        client.get("$authorizeEndpoint?${authorizationRequest.toHttpQueryString()}") {
        }.expectRedirect().apply {
            assertEquals(true, headers["location"]!!.contains("external_login"))
        }
    }


}
