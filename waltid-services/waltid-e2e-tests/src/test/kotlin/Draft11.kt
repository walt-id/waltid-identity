import com.nimbusds.jose.JWSAlgorithm
import id.walt.commons.testing.E2ETest
import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.w3c.utils.VCFormat
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.OpenID4VCI.getCIProviderMetadataUrl
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.oid4vc.util.JwtUtils
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class Draft11(private val e2e: E2ETest, private val client: HttpClient)  {

    fun testIssuerAPIDraft11AuthFlowWithJar(issuanceReq: IssuanceRequest) = runBlocking {
        lateinit var offerUrl: String
        lateinit var issuerState: String
        lateinit var authJarTokenRequest: AuthorizationRequest

        val issuerApi = IssuerApi(e2e, client)

        issuerApi.jwt(issuanceReq) {
            offerUrl = it
        }

        val offerUrlParams = Url(offerUrl).parameters.toMap()
        val offerObj = CredentialOfferRequest.fromHttpParameters(offerUrlParams)
        assertTrue(offerObj.credentialOfferUri!!.contains("draft11"))
        assertFalse(offerObj.credentialOfferUri!!.contains("draft13"))

        val credOffer = client.get(offerObj.credentialOfferUri!!).body<CredentialOffer.Draft11>()

        assertNotNull(credOffer.credentialIssuer)
        assertNotNull(credOffer.credentials)
        assertNotNull(credOffer.grants)


        val issuerMetadataUrl = getCIProviderMetadataUrl(credOffer.credentialIssuer)
        val rawJsonMetadata = client.get(issuerMetadataUrl).bodyAsText()
        val jsonElementMetadata = Json.parseToJsonElement(rawJsonMetadata)
        assertTrue(jsonElementMetadata.jsonObject["credentials_supported"] is JsonArray, "Expected credentials_supported in Open ID Provider Metadata to be a JsonArray")

        val issuerMetadata = OpenIDProviderMetadata.fromJSONString(rawJsonMetadata) as OpenIDProviderMetadata.Draft11
        assertContains(issuerMetadata.grantTypesSupported, GrantType.authorization_code)
        assertContains(issuerMetadata.grantTypesSupported, GrantType.pre_authorized_code)
        assertNotNull(issuerMetadata.jwksUri)
        assertTrue(issuerMetadata.credentialSupported!!.keys.all { it.toIntOrNull() != null }, "Expected credentials_supported keys to be array indices (e.g., '0', '1')")

        assertEquals(issuerMetadata.issuer, credOffer.credentialIssuer)
        assertEquals(issuerMetadata.credentialIssuer, credOffer.credentialIssuer)
        assertEquals(issuerMetadata.credentialIssuer, credOffer.credentialIssuer)

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


        issuerState = credOffer.grants[GrantType.authorization_code.name]!!.issuerState!!

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

        // Verify Authorization Request and JAR Token
        assertNotNull(authJarTokenRequest.request)
        val requestJwt = authJarTokenRequest.request!!.decodeJws()

        val keyId = requestJwt.header[JWTClaims.Header.keyID]!!.jsonPrimitive.content
        assertNotNull(keyId)

        val jwksResponse = client.get(issuerMetadata.jwksUri!!).bodyAsText()

        val jwks = Json.parseToJsonElement(jwksResponse).jsonObject

        val matchingKey = jwks["keys"]?.jsonArray?.firstOrNull { key ->
            key.jsonObject[JWTClaims.Header.keyID]?.jsonPrimitive?.content == keyId
        }

        assertNotNull(matchingKey)

        val signingKey = JWKKey.importJWK(matchingKey.toString()).getOrThrow()

        assertTrue (signingKey.verifyJws(authJarTokenRequest.request!!).isSuccess)

        val jarPayload = requestJwt.payload
        assertNotNull(jarPayload)

        validateAuthorizationData(
            issuerMetadata = issuerMetadata,
            holderAuthorizationRequest = authorizationRequest,
            jarPayload = jarPayload,
            issuerAuthorizationRequest = authJarTokenRequest
        )

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

                assertTrue(theInputDescriptor.format!![VCFormat.jwt_vc]?.alg?.contains(JWSAlgorithm.ES256.name) == true, "theInputDescriptor alg should be ES256")
            }

            else -> throw AssertionError("Unexpected authentication method ${issuanceReq.authenticationMethod}")
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun testIssuanceDraft11PreAuthFlow(issuanceReq: IssuanceRequest, wallet: Uuid) = runBlocking {
        lateinit var offerUrl: String

        val issuerApi = IssuerApi(e2e, client)

        issuerApi.jwt(issuanceReq) {
            offerUrl = it
        }

        val offerUrlParams = Url(offerUrl).parameters.toMap()
        val offerObj = CredentialOfferRequest.fromHttpParameters(offerUrlParams)
        val credOffer = client.get(offerObj.credentialOfferUri!!).body<CredentialOffer.Draft11>()

        assertNotNull(credOffer.credentialIssuer)
        assertNotNull(credOffer.credentials)
        assertNotNull(credOffer.grants)

        val issuerMetadataUrl = getCIProviderMetadataUrl(credOffer.credentialIssuer)
        val rawJsonMetadata = client.get(issuerMetadataUrl).bodyAsText()
        val jsonElementMetadata = Json.parseToJsonElement(rawJsonMetadata)
        assertTrue(jsonElementMetadata.jsonObject["credentials_supported"] is JsonArray, "Expected credentials_supported in Open ID Provider Metadata to be a JsonArray")

        val issuerMetadata = OpenIDProviderMetadata.fromJSONString(rawJsonMetadata) as OpenIDProviderMetadata.Draft11
        assertContains(issuerMetadata.grantTypesSupported, GrantType.authorization_code)
        assertContains(issuerMetadata.grantTypesSupported, GrantType.pre_authorized_code)
        assertNotNull(issuerMetadata.jwksUri)
        assertTrue(issuerMetadata.credentialSupported!!.keys.all { it.toIntOrNull() != null }, "Expected credentials_supported keys to be array indices (e.g., '0', '1')")

        assertEquals(issuerMetadata.issuer, credOffer.credentialIssuer)
        assertEquals(issuerMetadata.credentialIssuer, credOffer.credentialIssuer)
        assertEquals(issuerMetadata.credentialIssuer, credOffer.credentialIssuer)

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


        val matchingCredential = issuerMetadata.credentialSupported
            ?.values
            ?.find { it.id == issuanceReq.credentialConfigurationId }
            ?: throw AssertionError("No matching credential found for the credentialConfigurationId '${issuanceReq.credentialConfigurationId}'.")

        assertEquals(
            matchingCredential.id,
            issuanceReq.credentialConfigurationId
        )

        assertEquals(
            CredentialFormat.jwt_vc_json,
            matchingCredential.format
        )

        val exchangeApi = ExchangeApi(e2e, client)
        lateinit var newCredentialId: String
        exchangeApi.resolveCredentialOffer(wallet, offerUrl)
        exchangeApi.useOfferRequest(wallet, offerUrl, 1) {
            assertNotNull(it)
            val cred = it.first()
            assertContains(JwtUtils.parseJWTPayload(cred.document).keys, JwsSignatureScheme.JwsOption.VC)
            newCredentialId = cred.id
        }

        assertNotNull(newCredentialId)

    }

    private fun validateAuthorizationData(
        issuerMetadata: OpenIDProviderMetadata. Draft11,
        holderAuthorizationRequest: AuthorizationRequest,
        jarPayload: Map<String, Any?>? = null,
        issuerAuthorizationRequest: AuthorizationRequest? = null
    ) {

        val commonData = mapOf(
            "client_id" to issuerMetadata.issuer,
            "redirect_uri" to "${issuerMetadata.issuer}/direct_post",
            "response_mode" to ResponseMode.direct_post.name
        )

        val jarSpecificData = mapOf(
            JWTClaims.Payload.issuer to issuerMetadata.issuer,
            JWTClaims.Payload.audience to holderAuthorizationRequest.clientId
        )

        jarPayload?.let {
            validateData(
                expectedData = commonData + jarSpecificData,
                actualData = it
            )
        }

        issuerAuthorizationRequest?.let {
            validateData(
                expectedData =commonData,
                actualData = mapOf(
                    "client_id" to it.clientId,
                    "redirect_uri" to it.redirectUri,
                    "response_mode" to it.responseMode
                ),

            )
        }
    }

    private fun validateData(expectedData: Map<String, String?>, actualData: Map<String, Any?>) {
        expectedData.forEach { (key, expectedValue) ->
            val actualValue = actualData[key]?.toJsonElement()?.jsonPrimitive?.content
            assertEquals(expectedValue, actualValue, "Validation failed for key: $key")
        }
    }

}
