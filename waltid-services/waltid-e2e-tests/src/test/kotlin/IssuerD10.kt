import id.walt.credentials.utils.VCFormat
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.OpenID4VCI.getCIProviderMetadataUrl
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialOfferRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class IssuerDraft10(private val client: HttpClient)  {

    fun testIssuerAPIDraft10AuthFlowWithJar(issuanceReq: IssuanceRequest) = runBlocking {
        lateinit var offerUrl: String
        lateinit var issuerState: String
        lateinit var authJarTokenRequest: AuthorizationRequest

        val issuerApi = IssuerApi(client)

        issuerApi.jwt(issuanceReq) {
            offerUrl = it
        }

        val offerUrlParams = Url(offerUrl).parameters.toMap()
        val offerObj = CredentialOfferRequest.fromHttpParameters(offerUrlParams)
        val credOffer = client.get(offerObj.credentialOfferUri!!).body<CredentialOffer.Draft10>()

        assertNotNull(credOffer.credentialIssuer)
        assertNotNull(credOffer.credentials)
        assertNotNull(credOffer.grants)

        val issuerMetadataUrl = getCIProviderMetadataUrl(credOffer.credentialIssuer)

        val rawJsonMetadata = client.get(issuerMetadataUrl).bodyAsText()
        val jsonElementMetadata = Json.parseToJsonElement(rawJsonMetadata)
        assertTrue(jsonElementMetadata.jsonObject["credentials_supported"] is JsonArray, "Expected credentials_supported to be a JsonArray")

        val issuerMetadata = OpenIDProviderMetadata.fromJSONString(rawJsonMetadata) as OpenIDProviderMetadata.Draft10

        assertTrue(issuerMetadata.credentialSupported!!.keys.all { it.toIntOrNull() != null }, "Expected credentials_supported keys to be array indices (e.g., '0', '1')")

        assertEquals(issuerMetadata.issuer, credOffer.credentialIssuer)
        assertEquals(issuerMetadata.credentialIssuer, credOffer.credentialIssuer)
        assertEquals(issuerMetadata.credentialIssuer, credOffer.credentialIssuer)

        val matchingCredential = issuerMetadata.credentialSupported
            ?.values
            ?.find { it.id == issuanceReq.credentialConfigurationId }
            ?: throw AssertionError("No matching credential found for the credentialConfigurationId '${issuanceReq.credentialConfigurationId}'.")

        assertEquals(
            matchingCredential.id,
            issuanceReq.credentialConfigurationId
        )

        assertEquals(
            CredentialFormat.jwt_vc,
            matchingCredential.format
        )

        assertNull(issuerMetadata.authorizationServer)

        assertContains(issuerMetadata.grantTypesSupported, GrantType.authorization_code)
        assertContains(issuerMetadata.grantTypesSupported, GrantType.pre_authorized_code)

        assertNotNull(issuerMetadata.jwksUri)

        val rawJsonJwks = client.get(issuerMetadata.jwksUri!!).bodyAsText()

        val keysArray = Json.parseToJsonElement(rawJsonJwks).jsonObject["keys"]?.jsonArray
            ?: throw AssertionError("JWKS response must contain a 'keys' array")

        assertTrue(
            keysArray.any { key ->
                key.jsonObject.run {
                    this["kty"]?.jsonPrimitive?.content == "EC" &&
                            this["crv"]?.jsonPrimitive?.content == "P-256"
                }
            },
            "JWKS must contain at least one key with 'kty': 'EC' and 'crv': 'P-256'"
        )

        issuerState = credOffer.grants["authorization_code"]!!.issuerState!!

        println(issuerState)
        val authorizationRequest = AuthorizationRequest(
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
                    locations = listOf(credOffer.credentialIssuer),
                    format = CredentialFormat.jwt_vc,
                    credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "OpenBadgeCredential"))
                )
            )
        )

        client.get("${issuerMetadata.authorizationEndpoint}?${authorizationRequest.toHttpQueryString()}") {}
            .expectRedirect().apply {
                authJarTokenRequest = AuthorizationRequest.fromHttpQueryString(headers["location"]!!)
            }

        assert(authJarTokenRequest.responseMode == ResponseMode.direct_post) { "response mode should be direct post" }
        assertNotNull(authJarTokenRequest.request)

        // Verify Token
        val requestJwt = authJarTokenRequest.request!!.decodeJws()

        println(requestJwt)

        val keyId = requestJwt.header["kid"]!!.jsonPrimitive.content
        assertNotNull(keyId)

        val jwksResponse = client.get(issuerMetadata.jwksUri!!).bodyAsText()

        val jwks = Json.parseToJsonElement(jwksResponse).jsonObject

        val matchingKey = jwks["keys"]?.jsonArray?.firstOrNull { key ->
            key.jsonObject["kid"]?.jsonPrimitive?.content == keyId
        }

        assertNotNull(matchingKey)

        val signingKey = JWKKey.importJWK(matchingKey.toString()).getOrThrow()

        assertTrue {signingKey.verifyJws(authJarTokenRequest.request!!).isSuccess}

        when (issuanceReq.authenticationMethod) {
            AuthenticationMethod.ID_TOKEN -> {
                assert(authJarTokenRequest.responseType == setOf(ResponseType.IdToken)) { "response type should be id_token" }
            }

            AuthenticationMethod.VP_TOKEN -> {
                assert(authJarTokenRequest.responseType == setOf(ResponseType.VpToken)) { "response type should be vp_token" }

                val presentationDefinitionJ = requestJwt.payload["presentation_definition"]
                assertNotNull(presentationDefinitionJ)

                val presentationDefinition = PresentationDefinition.fromJSON(
                    presentationDefinitionJ.jsonObject
                )

                val inputDescriptors = presentationDefinition.inputDescriptors

                assertEquals(1, inputDescriptors.size)

                val theInputDescriptor = inputDescriptors.first()

                assertNotNull(theInputDescriptor.format, "theInputDescriptor format should not be null")
                assertTrue(theInputDescriptor.format!!.containsKey(VCFormat.jwt_vc), "theInputDescriptor should be jwt_vc")
                assertTrue(theInputDescriptor.format!![VCFormat.jwt_vc]?.alg?.contains("ES256") == true, "theInputDescriptor alg should be ES256")
            }

            else -> throw AssertionError("Unexpected authentication method ${issuanceReq.authenticationMethod}")
        }
    }

}