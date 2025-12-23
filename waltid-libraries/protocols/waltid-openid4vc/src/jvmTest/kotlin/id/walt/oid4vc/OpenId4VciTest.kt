@file:OptIn(ExperimentalTime::class)

package id.walt.oid4vc

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.did.dids.DidService
import id.walt.oid4vc.data.*
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.AuthorizationCodeResponse
import id.walt.oid4vc.responses.CredentialResponse
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.JwtUtils
import id.walt.policies.policies.JwtSignaturePolicy
import id.walt.sdjwt.SDJwt
import id.walt.w3c.CredentialBuilder
import id.walt.w3c.CredentialBuilderType
import id.walt.w3c.issuance.Issuer.baseIssue
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeAll
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class OpenId4VciTest {
    val CREDENTIAL_OFFER_BASE_URL = "openid-credential-offer://test"

    val DEPLOYED_ISSUER_BASE_URL = "https://issuer.portal.test.waltid.cloud"
    val ISSUER_BASE_URL = "https://test"
    val ISSUER_METADATA = (OpenID4VCI.createDefaultProviderMetadata(
        baseUrl =  ISSUER_BASE_URL,
                credentialSupported = mapOf(
                    "VerifiableId" to CredentialSupported(
                        format = CredentialFormat.jwt_vc_json,
                        cryptographicBindingMethodsSupported = setOf("did"),
                        credentialSigningAlgValuesSupported = setOf(CredSignAlgValues.Named("ES256K")),
                        credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "VerifiableId")),
                        customParameters = mapOf("foo" to JsonPrimitive("bar"))
                    ),
                    "VerifiableDiploma" to CredentialSupported(
                        format = CredentialFormat.jwt_vc_json,
                        cryptographicBindingMethodsSupported = setOf("did"),
                        credentialSigningAlgValuesSupported = setOf(CredSignAlgValues.Named("ES256K")),
                        credentialDefinition = CredentialDefinition(
                    type = listOf(
                        "VerifiableCredential",
                        "VerifiableAttestation",
                        "VerifiableDiploma"
                    )
                )
            )
        ),
        version = OpenID4VCIVersion.DRAFT13
    ) as OpenIDProviderMetadata.Draft13)


    val ISSUER_TOKEN_KEY = runBlocking { JWKKey.generate(KeyType.RSA) }
    val ISSUER_DID_KEY = runBlocking { JWKKey.generate(KeyType.Ed25519) }
    val ISSUER_DID = runBlocking { DidService.registerByKey("jwk", ISSUER_DID_KEY).did }

    val WALLET_CLIENT_ID = "test-client"
    val WALLET_REDIRECT_URI = "http://blank"
    val WALLET_KEY =
        "{\"kty\":\"EC\",\"d\":\"uD-uxub011cplvr5Bd6MrIPSEUBsgLk-C1y3tnmfetQ\",\"use\":\"sig\",\"crv\":\"secp256k1\",\"kid\":\"48d8a34263cf492aa7ff61b6183e8bcf\",\"x\":\"TKaQ6sCocTDsmuj9tTR996tFXpEcS2EJN-1gOadaBvk\",\"y\":\"0TrIYHcfC93VpEuvj-HXTnyKt0snayOMwGSJA1XiDX8\"}"
    val WALLET_DID =
        "did:jwk:eyJrdHkiOiJFQyIsInVzZSI6InNpZyIsImNydiI6InNlY3AyNTZrMSIsImtpZCI6IjQ4ZDhhMzQyNjNjZjQ5MmFhN2ZmNjFiNjE4M2U4YmNmIiwieCI6IlRLYVE2c0NvY1REc211ajl0VFI5OTZ0RlhwRWNTMkVKTi0xZ09hZGFCdmsiLCJ5IjoiMFRySVlIY2ZDOTNWcEV1dmotSFhUbnlLdDBzbmF5T013R1NKQTFYaURYOCJ9"

    companion object {
        @BeforeAll
        @JvmStatic
        fun init() = runTest {
            DidService.minimalInit()
            assertContains(DidService.registrarMethods.keys, "jwk")
        }
    }

    @Test
    fun testCredentialIssuanceIsolatedFunctions() = runTest {
        println("// -------- CREDENTIAL ISSUER ----------")
        // init credential offer for full authorization code flow
        val credOffer = CredentialOffer.Draft13.Builder(ISSUER_BASE_URL)
            .addOfferedCredentialByReference("VerifiableId")
            .addAuthorizationCodeGrant("test-state")
            .build()
        val issueReqUrl = OpenID4VCI.getCredentialOfferRequestUrl(credOffer)

        // Show credential offer request as QR code
        println(issueReqUrl)

        println("// -------- WALLET ----------")
        assertEquals(expected = credOffer.credentialIssuer, actual = ISSUER_METADATA.credentialIssuer)

        println("// resolve offered credentials")
        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credOffer, ISSUER_METADATA)
        println("offeredCredentials: $offeredCredentials")
        assertEquals(expected = 1, actual = offeredCredentials.size)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().credentialDefinition?.type?.last())
        val offeredCredential = offeredCredentials.first()
        println("offeredCredentials[0]: $offeredCredential")

        println("// go through full authorization code flow to receive offered credential")
        println("// auth request (short-cut, without pushed authorization request)")
        val authReq = AuthorizationRequest(
            setOf(ResponseType.Code), WALLET_CLIENT_ID,
            redirectUri = WALLET_REDIRECT_URI,
            issuerState = credOffer.grants[GrantType.authorization_code.value]!!.issuerState
        )
        println("authReq: $authReq")

        println("// -------- CREDENTIAL ISSUER ----------")

        // create issuance session and generate authorization code
        val authCodeResponse =
            OpenID4VC.processCodeFlowAuthorization(authReq, authReq.issuerState!!, ISSUER_METADATA, ISSUER_TOKEN_KEY)
        val redirectUri =
            authCodeResponse.toRedirectUri(authReq.redirectUri ?: TODO(), authReq.responseMode ?: ResponseMode.query)
        Url(redirectUri).let {
            assertContains(iterable = it.parameters.names(), element = ResponseType.Code.name.lowercase())
            assertEquals(
                expected = authCodeResponse.code,
                actual = it.parameters[ResponseType.Code.name.lowercase()]
            )
        }

        println("// -------- WALLET ----------")
        println("// token req")
        val tokenReq = TokenRequest.AuthorizationCode(
            clientId = WALLET_CLIENT_ID,
            code = authCodeResponse.code!!,
        )


        println("tokenReq: $tokenReq")

        println("// -------- CREDENTIAL ISSUER ----------")

        // TODO: Validate authorization code
        // TODO: generate access token
        val accessToken = ISSUER_TOKEN_KEY.signJws(
            buildJsonObject {
                put(JWTClaims.Payload.subject, "test-issuance-session")
                put(JWTClaims.Payload.issuer, ISSUER_BASE_URL)
                put(JWTClaims.Payload.audience, TokenTarget.ACCESS.name)
                put(JWTClaims.Payload.jwtID, "token-id")
            }.toString().toByteArray()
        )
        val cNonce = "pop-nonce"
        val tokenResponse: TokenResponse = TokenResponse.success(accessToken, "bearer", cNonce = cNonce)

        println("// -------- WALLET ----------")
        assertTrue(actual = tokenResponse.isSuccess)
        assertNotNull(actual = tokenResponse.accessToken)
        assertNotNull(actual = tokenResponse.cNonce)

        println("// receive credential")
        val nonce = tokenResponse.cNonce
        val holderDid = WALLET_DID
        val holderKey = JWKKey.importJWK(WALLET_KEY).getOrThrow()
        val holderKeyId = holderKey.getKeyId()
        val proofKeyId = "$holderDid#$holderKeyId"
        val proofOfPossession =
            ProofOfPossession.JWTProofBuilder(ISSUER_BASE_URL, WALLET_CLIENT_ID, nonce, proofKeyId).build(holderKey)

        val credReq = CredentialRequest.forOfferedCredential(offeredCredential, proofOfPossession)
        println("credReq: $credReq")

        println("// -------- CREDENTIAL ISSUER ----------")
        val parsedHolderKeyId =
            credReq.proof?.jwt?.let { JwtUtils.parseJWTHeader(it) }?.get("kid")?.jsonPrimitive?.content
        assertNotNull(actual = parsedHolderKeyId)
        assertTrue(actual = parsedHolderKeyId.startsWith("did:"))
        val parsedHolderDid = parsedHolderKeyId.substringBefore("#")
        val resolvedKeyForHolderDid = DidService.resolveToKey(parsedHolderDid).getOrThrow()

        val validPoP = credReq.proof.validateJwtProof(
            resolvedKeyForHolderDid,
            ISSUER_BASE_URL,
            WALLET_CLIENT_ID,
            nonce,
            parsedHolderKeyId
        )
        assertTrue(actual = validPoP)

        val generatedCredential: JsonElement = runBlocking {
            CredentialBuilder(CredentialBuilderType.W3CV2CredentialBuilder).apply {
                type = credReq.credentialDefinition?.type ?: listOf("VerifiableCredential")
                issuerDid = ISSUER_DID
                subjectDid = parsedHolderKeyId
            }.buildW3C().baseIssue(ISSUER_DID_KEY, ISSUER_DID, parsedHolderKeyId, mapOf(), mapOf(), mapOf(), mapOf())
        }.let { JsonPrimitive(it) }

        assertNotNull(generatedCredential)
        val credentialResponse: CredentialResponse = CredentialResponse.success(credReq.format, generatedCredential)

        println("// -------- WALLET ----------")
        assertTrue(actual = credentialResponse.isSuccess)
        assertFalse(actual = credentialResponse.isDeferred)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = credentialResponse.format!!)
        assertTrue(actual = credentialResponse.credential!!.instanceOf(JsonPrimitive::class))

        println("// parse and verify credential")
        val credential = credentialResponse.credential.jsonPrimitive.content
        println(">>> Issued credential: $credential")
        verifyIssuerAndSubjectId(
            SDJwt.parse(credential).fullPayload["vc"]?.jsonObject!!,
            ISSUER_DID, WALLET_DID
        )
        assertTrue(actual = JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess)

        // test jwt-vc-issuer well-known url with URL path segments
        val issuerUrl =
            "http://waltid.enterprise.localhost:3000/v1/waltid.tenant1.issuer1/issuer-service-api/openid4vc/"
        val wellKnownVCIssuerUrl =
            "http://waltid.enterprise.localhost:3000/.well-known/jwt-vc-issuer/v1/waltid.tenant1.issuer1/issuer-service-api/openid4vc"
        assertEquals(wellKnownVCIssuerUrl, OpenID4VCI.getJWTVCIssuerProviderMetadataUrl(issuerUrl))
    }

    // Test case for available authentication methods are: NONE, ID_TOKEN, VP_TOKEN, PRE_AUTHORIZED PWD(Handled by third party authorization server)
    @Test
    fun testCredentialIssuanceIsolatedFunctionsAuthCodeFlow() = runTest {
        // is it ok to generate the credential offer using the ciTestProvider (OpenIDCredentialIssuer class) ?
        val issuedCredentialId = "VerifiableId"

        println("// -------- CREDENTIAL ISSUER ----------")
        // Init credential offer for full authorization code flow

        // Issuer Client stores the authentication method in session.
        // Available authentication methods are: NONE, ID_TOKEN, VP_TOKEN, PWD(Handled by third party authorization server), PRE_AUTHORIZED. The response for each method is a redirect to the proper location.
        println("// --Authentication method is NONE--")
        var issuerState = "test-state-none-auth"
        var credOffer = CredentialOffer.Draft13.Builder(ISSUER_BASE_URL)
            .addOfferedCredentialByReference(issuedCredentialId)
            .addAuthorizationCodeGrant(issuerState)
            .build()

        // Issuer Client shows credential offer request as QR code
        println(OpenID4VCI.getCredentialOfferRequestUrl(credOffer))

        println("// -------- WALLET ----------")
        val providerMetadata = ISSUER_METADATA
        assertEquals(expected = credOffer.credentialIssuer, actual = providerMetadata.credentialIssuer)

        println("// resolve offered credentials")
        var offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credOffer, providerMetadata)
        println("offeredCredentials: $offeredCredentials")
        assertEquals(expected = 1, actual = offeredCredentials.size)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().credentialDefinition?.type?.last())
        var offeredCredential = offeredCredentials.first()
        println("offeredCredentials[0]: $offeredCredential")


        println("// go through authorization code flow to receive offered credential")
        println("// auth request (short-cut, without pushed authorization request)")
        var authReqWalletState = "secured_state"
        var authReqWallet = AuthorizationRequest(
            setOf(ResponseType.Code), WALLET_CLIENT_ID,
            redirectUri = WALLET_REDIRECT_URI,
            issuerState = credOffer.grants[GrantType.authorization_code.value]!!.issuerState,
            state = authReqWalletState
        )
        println("authReq: $authReqWallet")

        // Wallet client calls /authorize endpoint


        println("// -------- CREDENTIAL ISSUER ----------")
        var authReq = OpenID4VCI.validateAuthorizationRequestQueryString(authReqWallet.toHttpQueryString())

        // Issuer Client retrieves issuance session based on issuer state and stores the credential request, including authReqWallet state
        // Available authentication methods are: NONE, ID_TOKEN, VP_TOKEN, PWD(Handled by third party authorization server). The response for each method is a redirect to the proper location.
        // Issuer Client checks the authentication method of the session

        println("// --Authentication method is NONE--")
        // Issuer Client generates authorization code
        var authorizationCode = "secured_code"
        var authCodeResponse: AuthorizationCodeResponse =
            AuthorizationCodeResponse.success(authorizationCode, mapOf("state" to listOf(authReqWallet.state!!)))
        var redirectUri =
            testCredentialIssuanceIsolatedFunctionsAuthCodeFlowRedirectWithCode(authCodeResponse, authReqWallet)

        // Issuer client redirects the request to redirectUri

        println("// -------- WALLET ----------")
        println("// token req")

        var tokenReq = TokenRequest.AuthorizationCode(
            clientId = WALLET_CLIENT_ID,
            code = authCodeResponse.code!!,
        )

        println("tokenReq: $tokenReq")


        println("// -------- CREDENTIAL ISSUER ----------")
        // Validate token request against authorization code
        OpenID4VCI.validateTokenRequestRaw(tokenReq.toHttpParameters(), authorizationCode)

        // Generate Access Token
        var expirationTime = (Clock.System.now().epochSeconds + 864000L) // ten days in milliseconds

        var accessToken = OpenID4VCI.signToken(
            privateKey = ISSUER_TOKEN_KEY,
            payload = buildJsonObject {
                put(JWTClaims.Payload.audience, ISSUER_BASE_URL)
                put(JWTClaims.Payload.subject, authReq.clientId)
                put(JWTClaims.Payload.issuer, ISSUER_BASE_URL)
                put(JWTClaims.Payload.expirationTime, expirationTime)
                put(JWTClaims.Payload.notBeforeTime, Clock.System.now().epochSeconds)
            }
        )

        // Issuer client creates cPoPnonce
        var cPoPNonce = "secured_cPoPnonce"
        var tokenResponse: TokenResponse =
            TokenResponse.success(accessToken, "bearer", cNonce = cPoPNonce, expiresIn = expirationTime)

        // Issuer client sends successful response with tokenResponse


        println("// -------- WALLET ----------")
        assertTrue(actual = tokenResponse.isSuccess)
        assertNotNull(actual = tokenResponse.accessToken)
        assertNotNull(actual = tokenResponse.cNonce)

        println("// receive credential")
        var nonce = tokenResponse.cNonce
        val holderDid = WALLET_DID
        val holderKey = JWKKey.importJWK(WALLET_KEY).getOrThrow()
        val holderKeyId = holderKey.getKeyId()
        val proofKeyId = "$holderDid#$holderKeyId"
        var proofOfPossession =
            ProofOfPossession.JWTProofBuilder(ISSUER_BASE_URL, null, nonce, proofKeyId).build(holderKey)

        var credReq = CredentialRequest.forOfferedCredential(offeredCredential, proofOfPossession)
        println("credReq: $credReq")

        println("// -------- CREDENTIAL ISSUER ----------")
        // Issuer Client extracts Access Token from header
        OpenID4VCI.verifyToken(tokenResponse.accessToken, ISSUER_TOKEN_KEY.getPublicKey())

        //Then VC Stuff

        // ----------------------------------
        // Authentication Method is ID_TOKEN
        // ----------------------------------
        println("// --Authentication method is ID_TOKEN--")
        issuerState = "test-state-idtoken-auth"
        val credOfferUrl = testIsolatedFunctionsCreateCredentialOffer(ISSUER_BASE_URL, issuerState, issuedCredentialId)
        credOffer =
            CredentialOfferRequest.fromHttpQueryString(Url(credOfferUrl).encodedQuery).credentialOffer!! as CredentialOffer.Draft13

        // Issuer Client shows credential offer request as QR code
        println(OpenID4VCI.getCredentialOfferRequestUrl(credOffer))

        println("// -------- WALLET ----------")
        //parsedCredOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(issueReqUrl)
        //providerMetadata = OpenID4VCI.resolveCIProviderMetadata(parsedCredOffer)
        assertEquals(expected = credOffer.credentialIssuer, actual = providerMetadata.credentialIssuer)

        println("// resolve offered credentials")
        offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credOffer, providerMetadata)
        println("offeredCredentials: $offeredCredentials")
        assertEquals(expected = 1, actual = offeredCredentials.size)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().credentialDefinition?.type?.last())
        offeredCredential = offeredCredentials.first()
        println("offeredCredentials[0]: $offeredCredential")


        authReqWalletState = "secured_state_idtoken"
        authReqWallet = AuthorizationRequest(
            setOf(ResponseType.Code), WALLET_CLIENT_ID,
            redirectUri = WALLET_REDIRECT_URI,
            issuerState = credOffer.grants[GrantType.authorization_code.value]!!.issuerState,
            state = authReqWalletState
        )
        println("authReq: $authReqWallet")

        // Wallet client calls /authorize endpoint


        println("// -------- CREDENTIAL ISSUER ----------")
        authReq = OpenID4VCI.validateAuthorizationRequestQueryString(authReqWallet.toHttpQueryString())

        // Issuer Client retrieves issuance session based on issuer state and stores the credential request, including authReqWallet state
        // Available authentication methods are: NONE, ID_TOKEN, VP_TOKEN, PWD(Handled by third party authorization server). The response for each method is a redirect to the proper location.
        // Issuer Client checks the authentication method of the session

        // Issuer Client generates authorization code
        // Issuer client creates state and nonce for the id token authorization request
        var authReqIssuerState = "secured_state_issuer_idtoken"
        var authReqIssuerNonce = "secured_nonce_issue_idtoken"

        var authReqIssuer = OpenID4VCI.generateAuthorizationRequest(
            authReq,
            ISSUER_BASE_URL,
            ISSUER_TOKEN_KEY,
            ResponseType.IdToken,
            authReqIssuerState,
            authReqIssuerNonce
        )

        // Redirect uri is located in the client_metadata.authorization_endpoint or "openid://"
        var redirectUriReq = authReqIssuer.toRedirectUri(
            authReq.clientMetadata?.customParameters?.get("authorization_endpoint")?.jsonPrimitive?.content
                ?: "openid://", authReq.responseMode ?: ResponseMode.query
        )
        Url(redirectUriReq).let {
            assertContains(iterable = it.parameters.names(), element = "request")
            assertContains(iterable = it.parameters.names(), element = "redirect_uri")
            assertEquals(expected = ISSUER_BASE_URL + "/direct_post", actual = it.parameters["redirect_uri"])
        }

        // Issuer Client redirects the request to redirectUri


        println("// -------- WALLET ----------")
        // wallet creates id token
        val idToken =
            "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6ImRpZDprZXk6ejJkbXpEODFjZ1B4OFZraTdKYnV1TW1GWXJXUGdZb3l0eWtVWjNleXFodDFqOUtib2o3ZzlQZlhKeGJiczRLWWVneXI3RUxuRlZucERNemJKSkRETlpqYXZYNmp2dERtQUxNYlhBR1c2N3BkVGdGZWEyRnJHR1NGczhFanhpOTZvRkxHSGNMNFA2YmpMRFBCSkV2UlJIU3JHNExzUG5lNTJmY3p0Mk1XakhMTEpCdmhBQyN6MmRtekQ4MWNnUHg4VmtpN0pidXVNbUZZcldQZ1lveXR5a1VaM2V5cWh0MWo5S2JvajdnOVBmWEp4YmJzNEtZZWd5cjdFTG5GVm5wRE16YkpKREROWmphdlg2anZ0RG1BTE1iWEFHVzY3cGRUZ0ZlYTJGckdHU0ZzOEVqeGk5Nm9GTEdIY0w0UDZiakxEUEJKRXZSUkhTckc0THNQbmU1MmZjenQyTVdqSExMSkJ2aEFDIn0.eyJub25jZSI6ImE4YWE1NDYwLTRmN2UtNDRmNy05ZGE3LWU1NmQ0YjIxMWE1MSIsInN1YiI6ImRpZDprZXk6ejJkbXpEODFjZ1B4OFZraTdKYnV1TW1GWXJXUGdZb3l0eWtVWjNleXFodDFqOUtib2o3ZzlQZlhKeGJiczRLWWVneXI3RUxuRlZucERNemJKSkRETlpqYXZYNmp2dERtQUxNYlhBR1c2N3BkVGdGZWEyRnJHR1NGczhFanhpOTZvRkxHSGNMNFA2YmpMRFBCSkV2UlJIU3JHNExzUG5lNTJmY3p0Mk1XakhMTEpCdmhBQyIsImlzcyI6ImRpZDprZXk6ejJkbXpEODFjZ1B4OFZraTdKYnV1TW1GWXJXUGdZb3l0eWtVWjNleXFodDFqOUtib2o3ZzlQZlhKeGJiczRLWWVneXI3RUxuRlZucERNemJKSkRETlpqYXZYNmp2dERtQUxNYlhBR1c2N3BkVGdGZWEyRnJHR1NGczhFanhpOTZvRkxHSGNMNFA2YmpMRFBCSkV2UlJIU3JHNExzUG5lNTJmY3p0Mk1XakhMTEpCdmhBQyIsImF1ZCI6Imh0dHBzOi8vMDFiYi01LTIwMy0xNzQtNjcubmdyb2stZnJlZS5hcHAiLCJpYXQiOjE3MjExNDQ3MzYsImV4cCI6MTcyMTE0NTAzNn0.VPWyLkMQAlcc40WCNSRH-Vxaj4LHi-wf2P9kcEKDvcdyVec2xJIwkg0JF4INMbLCkF0Y89lT0oswALd345wdUg"
        // wallet calls POST /direct_post (e.g. redirect_uri of Issuer Auth Req) providing the id_token


        println("// -------- CREDENTIAL ISSUER ----------")
        // Create validateIdTokenResponse()
        val idTokenPayload = OpenID4VCI.validateAuthorizationRequestToken(idToken)

        // Issuer Client validates states and nonces based on idTokenPayload

        // Issuer client generates authorization code
        authorizationCode = "secured_code_idtoken"
        authCodeResponse =
            AuthorizationCodeResponse.success(authorizationCode, mapOf("state" to listOf(authReqWallet.state!!)))

        redirectUri =
            authCodeResponse.toRedirectUri(authReq.redirectUri ?: TODO(), authReq.responseMode ?: ResponseMode.query)
        Url(redirectUri).let {
            assertContains(iterable = it.parameters.names(), element = ResponseType.Code.name.lowercase())
            assertEquals(
                expected = authCodeResponse.code,
                actual = it.parameters[ResponseType.Code.name.lowercase()]
            )
        }


        println("// -------- WALLET ----------")
        println("// token req")

        tokenReq = TokenRequest.AuthorizationCode(
            clientId = WALLET_CLIENT_ID,
            code = authCodeResponse.code!!
        )


        println("tokenReq: $tokenReq")


        println("// -------- CREDENTIAL ISSUER ----------")
        // Validate token request against authorization code
        OpenID4VCI.validateTokenRequestRaw(tokenReq.toHttpParameters(), authorizationCode)

        // Generate Access Token
        expirationTime = (Clock.System.now().epochSeconds + 864000L) // ten days in milliseconds

        accessToken = OpenID4VCI.signToken(
            privateKey = ISSUER_TOKEN_KEY,
            payload = buildJsonObject {
                put(JWTClaims.Payload.audience, ISSUER_BASE_URL)
                put(JWTClaims.Payload.subject, authReq.clientId)
                put(JWTClaims.Payload.issuer, ISSUER_BASE_URL)
                put(JWTClaims.Payload.expirationTime, expirationTime)
                put(JWTClaims.Payload.notBeforeTime, Clock.System.now().epochSeconds)
            }
        )

        // Issuer client creates cPoPnonce
        cPoPNonce = "secured_cPoPnonce_idtoken"
        tokenResponse = TokenResponse.success(accessToken, "bearer", cNonce = cPoPNonce, expiresIn = expirationTime)

        // Issuer client sends successful response with tokenResponse

        println("// -------- WALLET ----------")
        assertTrue(actual = tokenResponse.isSuccess)
        assertNotNull(actual = tokenResponse.accessToken)
        assertNotNull(actual = tokenResponse.cNonce)

        println("// receive credential")
        nonce = tokenResponse.cNonce
        proofOfPossession = ProofOfPossession.JWTProofBuilder(ISSUER_BASE_URL, null, nonce, proofKeyId).build(holderKey)

        credReq = CredentialRequest.forOfferedCredential(offeredCredential, proofOfPossession)
        println("credReq: $credReq")

        println("// -------- CREDENTIAL ISSUER ----------")
        // Issuer Client extracts Access Token from header
        OpenID4VCI.verifyToken(tokenResponse.accessToken, ISSUER_TOKEN_KEY.getPublicKey())

        //Then VC Stuff


        // ----------------------------------
        // Authentication Method is VP_TOKEN
        // ----------------------------------
        println("// --Authentication method is VP_TOKEN--")
        issuerState = "test-state-vptoken-auth"
        credOffer = CredentialOffer.Draft13.Builder(ISSUER_BASE_URL)
            .addOfferedCredentialByReference(issuedCredentialId)
            .addAuthorizationCodeGrant(issuerState)
            .build()

        // Issuer Client shows credential offer request as QR code
        println(OpenID4VCI.getCredentialOfferRequestUrl(credOffer))

        println("// -------- WALLET ----------")
        //parsedCredOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(issueReqUrl)
        //providerMetadata = OpenID4VCI.resolveCIProviderMetadata(parsedCredOffer)
        assertEquals(expected = credOffer.credentialIssuer, actual = providerMetadata.credentialIssuer)

        println("// resolve offered credentials")
        offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credOffer, providerMetadata)
        println("offeredCredentials: $offeredCredentials")
        assertEquals(expected = 1, actual = offeredCredentials.size)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().credentialDefinition?.type?.last())
        offeredCredential = offeredCredentials.first()
        println("offeredCredentials[0]: $offeredCredential")


        authReqWalletState = "secured_state_vptoken"
        authReqWallet = AuthorizationRequest(
            setOf(ResponseType.Code), WALLET_CLIENT_ID,
            redirectUri = WALLET_REDIRECT_URI,
            issuerState = credOffer.grants[GrantType.authorization_code.value]!!.issuerState,
            state = authReqWalletState
        )
        println("authReq: $authReqWallet")

        // Wallet client calls /authorize endpoint


        println("// -------- CREDENTIAL ISSUER ----------")
        authReq = OpenID4VCI.validateAuthorizationRequestQueryString(authReqWallet.toHttpQueryString())

        // Issuer Client retrieves issuance session based on issuer state and stores the credential request, including authReqWallet state
        // Available authentication methods are: NONE, ID_TOKEN, VP_TOKEN, PWD(Handled by third party authorization server). The response for each method is a redirect to the proper location.
        val requestedCredentialId = "OpenBadgeCredential"
        val vpProfile = OpenId4VPProfile.EBSIV3
        val requestCredentialsArr = buildJsonArray { add(requestedCredentialId) }
        val requestedTypes = requestCredentialsArr.map {
            when (it) {
                is JsonPrimitive -> it.contentOrNull
                is JsonObject -> it["credential"]?.jsonPrimitive?.contentOrNull
                else -> throw IllegalArgumentException("Invalid JSON type for requested credential: $it")
            } ?: throw IllegalArgumentException("Invalid VC type for requested credential: $it")
        }

        val presentationDefinition = PresentationDefinition.defaultGenerationFromVcTypesForCredentialFormat(
            requestedTypes,
            CredentialFormat.jwt_vc
        )

        // Issuer Client creates state and nonce for the vp_token authorization request
        authReqIssuerState = "secured_state_issuer_vptoken"
        authReqIssuerNonce = "secured_nonce_issuer_vptoken"

        authReqIssuer = OpenID4VCI.generateAuthorizationRequest(
            authReq,
            ISSUER_BASE_URL,
            ISSUER_TOKEN_KEY,
            ResponseType.VpToken,
            authReqIssuerState,
            authReqIssuerNonce,
            true,
            presentationDefinition
        )

        // Redirect uri is located in the client_metadata.authorization_endpoint or "openid://"
        redirectUriReq = authReqIssuer.toRedirectUri(
            authReq.clientMetadata?.customParameters?.get("authorization_endpoint")?.jsonPrimitive?.content
                ?: "openid://", authReq.responseMode ?: ResponseMode.query
        )
        Url(redirectUriReq).let {
            assertContains(iterable = it.parameters.names(), element = "request")
            assertContains(iterable = it.parameters.names(), element = "redirect_uri")
            assertContains(iterable = it.parameters.names(), element = "presentation_definition")
            assertEquals(expected = ISSUER_BASE_URL + "/direct_post", actual = it.parameters["redirect_uri"])
        }
        // Issuer Client redirects the request to redirectUri


        println("// -------- WALLET ----------")
        // wallet creates vp token
        val vpToken =
            "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCIsImtpZCI6ImRpZDprZXk6ejZNa3A3QVZ3dld4bnNORHVTU2JmMTlzZ0t6cngyMjNXWTk1QXFaeUFHaWZGVnlWI3o2TWtwN0FWd3ZXeG5zTkR1U1NiZjE5c2dLenJ4MjIzV1k5NUFxWnlBR2lmRlZ5ViJ9.eyJzdWIiOiJkaWQ6a2V5Ono2TWtwN0FWd3ZXeG5zTkR1U1NiZjE5c2dLenJ4MjIzV1k5NUFxWnlBR2lmRlZ5ViIsIm5iZiI6MTcyMDc2NDAxOSwiaWF0IjoxNzIwNzY0MDc5LCJqdGkiOiJ1cm46dXVpZDpiNzE2YThlOC0xNzVlLTRhMTYtODZlMC0xYzU2Zjc4NTFhZDEiLCJpc3MiOiJkaWQ6a2V5Ono2TWtwN0FWd3ZXeG5zTkR1U1NiZjE5c2dLenJ4MjIzV1k5NUFxWnlBR2lmRlZ5ViIsIm5vbmNlIjoiNDY0YTAwMTUtNzQ1OS00Y2Y4LWJmNjgtNDg0ODQyYTE5Y2FmIiwiYXVkIjoiZGlkOmtleTp6Nk1rcDdBVnd2V3huc05EdVNTYmYxOXNnS3pyeDIyM1dZOTVBcVp5QUdpZkZWeVYiLCJ2cCI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVQcmVzZW50YXRpb24iXSwiaWQiOiJ1cm46dXVpZDpiNzE2YThlOC0xNzVlLTRhMTYtODZlMC0xYzU2Zjc4NTFhZDEiLCJob2xkZXIiOiJkaWQ6a2V5Ono2TWtwN0FWd3ZXeG5zTkR1U1NiZjE5c2dLenJ4MjIzV1k5NUFxWnlBR2lmRlZ5ViIsInZlcmlmaWFibGVDcmVkZW50aWFsIjpbImV5SmhiR2NpT2lKRlpFUlRRU0lzSW5SNWNDSTZJa3BYVkNJc0ltdHBaQ0k2SW1ScFpEcHJaWGs2ZWpaTmEzQTNRVlozZGxkNGJuTk9SSFZUVTJKbU1UbHpaMHQ2Y25neU1qTlhXVGsxUVhGYWVVRkhhV1pHVm5sV0luMC5leUpwYzNNaU9pSmthV1E2YTJWNU9ubzJUV3R3TjBGV2QzWlhlRzV6VGtSMVUxTmlaakU1YzJkTGVuSjRNakl6VjFrNU5VRnhXbmxCUjJsbVJsWjVWaUlzSW5OMVlpSTZJbVJwWkRwclpYazZlalpOYTJwdE1tZGhSM052WkVkamFHWkhOR3M0VURaTGQwTklXbk5XUlZCYWFHODFWblZGWWxrNU5IRnBRa0k1SWl3aWRtTWlPbnNpUUdOdmJuUmxlSFFpT2xzaWFIUjBjSE02THk5M2QzY3Vkek11YjNKbkx6SXdNVGd2WTNKbFpHVnVkR2xoYkhNdmRqRWlMQ0pvZEhSd2N6b3ZMM0IxY213dWFXMXpaMnh2WW1Gc0xtOXlaeTl6Y0dWakwyOWlMM1l6Y0RBdlkyOXVkR1Y0ZEM1cWMyOXVJbDBzSW1sa0lqb2lkWEp1T25WMWFXUTZNVEl6SWl3aWRIbHdaU0k2V3lKV1pYSnBabWxoWW14bFEzSmxaR1Z1ZEdsaGJDSXNJazl3Wlc1Q1lXUm5aVU55WldSbGJuUnBZV3dpWFN3aWJtRnRaU0k2SWtwR1JpQjRJSFpqTFdWa2RTQlFiSFZuUm1WemRDQXpJRWx1ZEdWeWIzQmxjbUZpYVd4cGRIa2lMQ0pwYzNOMVpYSWlPbnNpZEhsd1pTSTZXeUpRY205bWFXeGxJbDBzSW1sa0lqb2laR2xrT21WNFlXMXdiR1U2TVRJeklpd2libUZ0WlNJNklrcHZZbk1nWm05eUlIUm9aU0JHZFhSMWNtVWdLRXBHUmlraUxDSjFjbXdpT2lKb2RIUndjem92TDNkM2R5NXFabVl1YjNKbkx5SXNJbWx0WVdkbElqcDdJbWxrSWpvaWFIUjBjSE02THk5M00yTXRZMk5uTG1kcGRHaDFZaTVwYnk5Mll5MWxaQzl3YkhWblptVnpkQzB4TFRJd01qSXZhVzFoWjJWekwwcEdSbDlNYjJkdlRHOWphM1Z3TG5CdVp5SXNJblI1Y0dVaU9pSkpiV0ZuWlNKOWZTd2lhWE56ZFdGdVkyVkVZWFJsSWpvaU1qQXlNeTB3TnkweU1GUXdOem93TlRvME5Gb2lMQ0psZUhCcGNtRjBhVzl1UkdGMFpTSTZJakl3TXpNdE1EY3RNakJVTURjNk1EVTZORFJhSWl3aVkzSmxaR1Z1ZEdsaGJGTjFZbXBsWTNRaU9uc2lhV1FpT2lKa2FXUTZaWGhoYlhCc1pUb3hNak1pTENKMGVYQmxJanBiSWtGamFHbGxkbVZ0Wlc1MFUzVmlhbVZqZENKZExDSmhZMmhwWlhabGJXVnVkQ0k2ZXlKcFpDSTZJblZ5YmpwMWRXbGtPbUZqTWpVMFltUTFMVGhtWVdRdE5HSmlNUzA1WkRJNUxXVm1aRGt6T0RVek5qa3lOaUlzSW5SNWNHVWlPbHNpUVdOb2FXVjJaVzFsYm5RaVhTd2libUZ0WlNJNklrcEdSaUI0SUhaakxXVmtkU0JRYkhWblJtVnpkQ0F6SUVsdWRHVnliM0JsY21GaWFXeHBkSGtpTENKa1pYTmpjbWx3ZEdsdmJpSTZJbFJvYVhNZ2QyRnNiR1YwSUhOMWNIQnZjblJ6SUhSb1pTQjFjMlVnYjJZZ1Z6TkRJRlpsY21sbWFXRmliR1VnUTNKbFpHVnVkR2xoYkhNZ1lXNWtJR2hoY3lCa1pXMXZibk4wY21GMFpXUWdhVzUwWlhKdmNHVnlZV0pwYkdsMGVTQmtkWEpwYm1jZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCeVpYRjFaWE4wSUhkdmNtdG1iRzkzSUdSMWNtbHVaeUJLUmtZZ2VDQldReTFGUkZVZ1VHeDFaMFpsYzNRZ015NGlMQ0pqY21sMFpYSnBZU0k2ZXlKMGVYQmxJam9pUTNKcGRHVnlhV0VpTENKdVlYSnlZWFJwZG1VaU9pSlhZV3hzWlhRZ2MyOXNkWFJwYjI1eklIQnliM1pwWkdWeWN5QmxZWEp1WldRZ2RHaHBjeUJpWVdSblpTQmllU0JrWlcxdmJuTjBjbUYwYVc1bklHbHVkR1Z5YjNCbGNtRmlhV3hwZEhrZ1pIVnlhVzVuSUhSb1pTQndjbVZ6Wlc1MFlYUnBiMjRnY21WeGRXVnpkQ0IzYjNKclpteHZkeTRnVkdocGN5QnBibU5zZFdSbGN5QnpkV05qWlhOelpuVnNiSGtnY21WalpXbDJhVzVuSUdFZ2NISmxjMlZ1ZEdGMGFXOXVJSEpsY1hWbGMzUXNJR0ZzYkc5M2FXNW5JSFJvWlNCb2IyeGtaWElnZEc4Z2MyVnNaV04wSUdGMElHeGxZWE4wSUhSM2J5QjBlWEJsY3lCdlppQjJaWEpwWm1saFlteGxJR055WldSbGJuUnBZV3h6SUhSdklHTnlaV0YwWlNCaElIWmxjbWxtYVdGaWJHVWdjSEpsYzJWdWRHRjBhVzl1TENCeVpYUjFjbTVwYm1jZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCMGJ5QjBhR1VnY21WeGRXVnpkRzl5TENCaGJtUWdjR0Z6YzJsdVp5QjJaWEpwWm1sallYUnBiMjRnYjJZZ2RHaGxJSEJ5WlhObGJuUmhkR2x2YmlCaGJtUWdkR2hsSUdsdVkyeDFaR1ZrSUdOeVpXUmxiblJwWVd4ekxpSjlMQ0pwYldGblpTSTZleUpwWkNJNkltaDBkSEJ6T2k4dmR6TmpMV05qWnk1bmFYUm9kV0l1YVc4dmRtTXRaV1F2Y0d4MVoyWmxjM1F0TXkweU1ESXpMMmx0WVdkbGN5OUtSa1l0VmtNdFJVUlZMVkJNVlVkR1JWTlVNeTFpWVdSblpTMXBiV0ZuWlM1d2JtY2lMQ0owZVhCbElqb2lTVzFoWjJVaWZYMTlMQ0pqY21Wa1pXNTBhV0ZzVTJOb1pXMWhJanA3SW1sa0lqb2lhSFIwY0hNNkx5OXdkWEpzTG1sdGMyZHNiMkpoYkM1dmNtY3ZjM0JsWXk5dllpOTJNM0F3TDNOamFHVnRZUzlxYzI5dUwyOWlYM1l6Y0RCZllXTm9hV1YyWlcxbGJuUmpjbVZrWlc1MGFXRnNYM05qYUdWdFlTNXFjMjl1SWl3aWRIbHdaU0k2SWtaMWJHeEtjMjl1VTJOb1pXMWhWbUZzYVdSaGRHOXlNakF5TVNKOWZTd2lhblJwSWpvaWRYSnVPblYxYVdRNk1USXpJaXdpWlhod0lqb3lNREExTkRVMU9UUTBMQ0pwWVhRaU9qRTJPRGs0TXpZM05EUXNJbTVpWmlJNk1UWTRPVGd6TmpjME5IMC5PRHZUQXVMN2JrME1pX3hNLVFualg4azByZ3VUeWtiYzJ6bFdFMVU2SGlmVXFjWTdFVU5GcUdUZWFUWHRESkxrODBuZWN6YkNNTGh1YlZseEFkdl9DdyJdfX0.zTXluOVIP0sQzc5GzNvtVvWRiaC-x9qMZg0d-EvCuRIg7QSgY0hmrfVlAzh2IDEvaXZ1ahM3hSVDx_YI74ToAw"
        // wallet calls POST /direct_post (e.g. redirect_uri of Issuer Auth Req) providing the vp_token and presentation submission

        println("// -------- CREDENTIAL ISSUER ----------")
        val vpTokenPayload = OpenID4VCI.validateAuthorizationRequestToken(vpToken)

        // Issuer Client validates states and nonces based on vpTokenPayload

        // Issuer client generates authorization code
        authorizationCode = "secured_code_vptoken"
        authCodeResponse =
            AuthorizationCodeResponse.success(authorizationCode, mapOf("state" to listOf(authReqWallet.state!!)))

        redirectUri =
            authCodeResponse.toRedirectUri(authReq.redirectUri ?: TODO(), authReq.responseMode ?: ResponseMode.query)
        Url(redirectUri).let {
            assertContains(iterable = it.parameters.names(), element = ResponseType.Code.name.lowercase())
            assertEquals(
                expected = authCodeResponse.code,
                actual = it.parameters[ResponseType.Code.name.lowercase()]
            )
        }


        println("// -------- WALLET ----------")
        println("// token req")
        tokenReq = TokenRequest.AuthorizationCode(
            clientId = WALLET_CLIENT_ID,
            code = authCodeResponse.code!!
        )

        println("tokenReq: $tokenReq")


        println("// -------- CREDENTIAL ISSUER ----------")
        // Validate token request against authorization code
        OpenID4VCI.validateTokenRequestRaw(tokenReq.toHttpParameters(), authorizationCode)

        // Generate Access Token
        expirationTime = (Clock.System.now().epochSeconds + 864000L) // ten days in milliseconds

        accessToken = OpenID4VCI.signToken(
            privateKey = ISSUER_TOKEN_KEY,
            payload = buildJsonObject {
                put(JWTClaims.Payload.audience, ISSUER_BASE_URL)
                put(JWTClaims.Payload.subject, authReq.clientId)
                put(JWTClaims.Payload.issuer, ISSUER_BASE_URL)
                put(JWTClaims.Payload.expirationTime, expirationTime)
                put(JWTClaims.Payload.notBeforeTime, Clock.System.now().epochSeconds)
            }
        )

        // Issuer client creates cPoPnonce
        cPoPNonce = "secured_cPoPnonce_idtoken"
        tokenResponse = TokenResponse.success(accessToken, "bearer", cNonce = cPoPNonce, expiresIn = expirationTime)

        // Issuer client sends successful response with tokenResponse

        println("// -------- WALLET ----------")
        assertTrue(actual = tokenResponse.isSuccess)
        assertNotNull(actual = tokenResponse.accessToken)
        assertNotNull(actual = tokenResponse.cNonce)

        println("// receive credential")
        nonce = tokenResponse.cNonce
        proofOfPossession = ProofOfPossession.JWTProofBuilder(ISSUER_BASE_URL, null, nonce, proofKeyId).build(holderKey)

        credReq = CredentialRequest.forOfferedCredential(offeredCredential, proofOfPossession)
        println("credReq: $credReq")

        println("// -------- CREDENTIAL ISSUER ----------")
        // Issuer Client extracts Access Token from header
        OpenID4VCI.verifyToken(tokenResponse.accessToken, ISSUER_TOKEN_KEY.getPublicKey())

        //Then VC Stuff


        // ----------------------------------
        // Authentication Method is PRE_AUTHORIZED
        // ----------------------------------
        println("// --Authentication method is PRE_AUTHORIZED--")
        val preAuthCode = randomUUIDString()
        credOffer = CredentialOffer.Draft13.Builder(ISSUER_BASE_URL)
            .addOfferedCredentialByReference(issuedCredentialId)
            .addPreAuthorizedCodeGrant(preAuthCode)
            .build()

        val issueReqUrl = OpenID4VCI.getCredentialOfferRequestUrl(credOffer, CREDENTIAL_OFFER_BASE_URL)
        // Issuer Client shows credential offer request as QR code
        println(issueReqUrl)

        println("// -------- WALLET ----------")
        credOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(issueReqUrl) as CredentialOffer.Draft13
        //providerMetadata = OpenID4VCI.resolveCIProviderMetadata(parsedCredOffer)
        assertEquals(expected = credOffer.credentialIssuer, actual = providerMetadata.credentialIssuer)

        println("// resolve offered credentials")
        offeredCredentials = OpenID4VCI.resolveOfferedCredentials(credOffer, providerMetadata)
        println("offeredCredentials: $offeredCredentials")
        assertEquals(expected = 1, actual = offeredCredentials.size)
        assertEquals(expected = CredentialFormat.jwt_vc_json, actual = offeredCredentials.first().format)
        assertEquals(expected = "VerifiableId", actual = offeredCredentials.first().credentialDefinition?.type?.last())
        offeredCredential = offeredCredentials.first()
        println("offeredCredentials[0]: $offeredCredential")
        assertNotNull(actual = credOffer.grants[GrantType.pre_authorized_code.value]?.preAuthorizedCode)


        println("// token req")
        val tokenReqPre = TokenRequest.PreAuthorizedCode(
            preAuthorizedCode = credOffer.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode!!
        )

        println("// -------- CREDENTIAL ISSUER ----------")
        // Validate token request against authorization code
        OpenID4VCI.validateTokenRequestRaw(tokenReqPre.toHttpParameters(), preAuthCode)

        // Generate Access Token
        expirationTime = (Clock.System.now().epochSeconds + 864000L) // ten days in milliseconds

        accessToken = OpenID4VCI.signToken(
            privateKey = ISSUER_TOKEN_KEY,
            payload = buildJsonObject {
                put(JWTClaims.Payload.audience, ISSUER_BASE_URL)
                put(JWTClaims.Payload.subject, authReq.clientId)
                put(JWTClaims.Payload.issuer, ISSUER_BASE_URL)
                put(JWTClaims.Payload.expirationTime, expirationTime)
                put(JWTClaims.Payload.notBeforeTime, Clock.System.now().epochSeconds)
            }
        )

        // Issuer client creates cPoPnonce
        cPoPNonce = "secured_cPoPnonce_preauthorized"
        tokenResponse = TokenResponse.success(accessToken, "bearer", cNonce = cPoPNonce, expiresIn = expirationTime)

        // Issuer client sends successful response with tokenResponse


        println("// -------- WALLET ----------")
        assertTrue(actual = tokenResponse.isSuccess)
        assertNotNull(actual = tokenResponse.accessToken)
        assertNotNull(actual = tokenResponse.cNonce)

        println("// receive credential")
        nonce = tokenResponse.cNonce
        proofOfPossession = ProofOfPossession.JWTProofBuilder(ISSUER_BASE_URL, null, nonce, proofKeyId).build(holderKey)

        credReq = CredentialRequest.forOfferedCredential(offeredCredential, proofOfPossession)
        println("credReq: $credReq")

        println("// -------- CREDENTIAL ISSUER ----------")
        // Issuer Client extracts Access Token from header
        OpenID4VCI.verifyToken(tokenResponse.accessToken, ISSUER_TOKEN_KEY.getPublicKey())

        //Then VC Stuff


    }

    private fun verifyIssuerAndSubjectId(credential: JsonObject, issuerId: String, subjectId: String) {
        assertEquals(expected = issuerId, actual = credential["issuer"]?.jsonPrimitive?.contentOrNull)
        assertEquals(
            expected = subjectId,
            actual = credential["credentialSubject"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull?.substringBefore(
                "#"
            )
        )
    }

    @Test
    fun testWalletRelatedIsolatedFunctions() = runTest {
        //
        // Issuer
        //

        // Setup Issuer - Currently, the deployed one is used
        val httpClient = testHttpClient()
        val createCredentialOfferRequestBody = buildJsonObject {
            put("issuerKey", buildJsonObject {
                put("type", "jwk")
                put("jwk", ISSUER_DID_KEY.exportJWKObject())
            }
            )
            put("issuerDid", ISSUER_DID)
            put("credentialConfigurationId", "OpenBadgeCredential_jwt_vc_json")
            put("credentialData", Json.parseToJsonElement(openBadgeCredentialData).jsonObject)
            put(
                "mapping", Json.parseToJsonElement(
                    """
                {
                     "id":"<uuid>",
                     "issuer":{
                        "id":"<issuerDid>"
                     },
                     "credentialSubject":{
                        "id":"<subjectDid>"
                     },
                     "issuanceDate":"<timestamp>",
                     "expirationDate":"<timestamp-in:365d>"
                  }
                """.trimIndent()
                ).jsonObject
            )
        }

        // Create Offer In Issuer API and get it as a String
        val credentialOfferUrlString = httpClient.post("${DEPLOYED_ISSUER_BASE_URL}/openid4vc/jwt/issue") {
            setBody(Json.encodeToJsonElement(createCredentialOfferRequestBody))
        }.expectSuccess().body<String>()

        //
        // Wallet
        //
        println("--- Wallet ---")
        println("Start: Credential offer = $credentialOfferUrlString")

        // Wallet API gets CredentialOfferString somehow.
        // Wallet parses it.
        val credentialOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(credentialOfferUrlString)
        println("Decoded credential offer: $credentialOffer")

        // W: resolveIssuerMetadataByCredentialOffer (CredOfferObject) -> OpenIDProvideMetadataObject
        val resolvedCIProviderMetadata = OpenID4VCI.resolveCIProviderMetadata(credentialOffer)
        println("Resolved CI Provider Metadata: $resolvedCIProviderMetadata")


        // The Wallet API checks if the offer has a preauthorized or authorization code (check GrantTypes.isAvailableIn() or so)
        // Its Pre-Authorized
        // The Wallet API constructs the Token the Request as follows (considering make this with type safety with sealed classed (e.g. TokenRequest() -> AuthorizationCode(), PreAuthorizedCode()) :
        val tokenRequest = TokenRequest.PreAuthorizedCode(
            preAuthorizedCode = credentialOffer.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode!!,
            clientId = null,  // The Wallet API should check for token_endpoint_auth_method in Issuer Metadata to see what client authentication types are supported.
        )
            println("Sending token request... $tokenRequest")

        val tokenResponse = OpenID4VCI.sendTokenRequest(
            providerMetadata = resolvedCIProviderMetadata,
            tokenRequest = tokenRequest
        )
        println("Response to token request is: $tokenResponse")

        // W: sendTokenRequest (code or preauthCode ) -> TokenRepsonseObject

        println("Validating token response...")
        // Wallet Validates the response ( check if there is successful and contains the required access_token)
        OpenID4VCI.validateTokenResponse(tokenResponse)
        println("Token response is valid!")

        /*
        The Wallet should check if it should create a proof.

        The scenario where proof_types_supported is empty or missing but the issuer still sends a c_nonce needs to be handled explicitly. Since proof is optional in OID4VCI, we need to carefully distinguish between:
           - Issuer explicitly supporting certain proof types (proof_types_supported is present).
           - Issuer not specifying any proof type (proof_types_supported is empty).
           - Issuer sending a c_nonce despite not specifying proof support (likely for replay attack protection).

         Possible cases:
           A. If proof_types_supported = "true", wallet must send proof.
           B. If proof_types_supported = "false" AND c_nonce is not provided, wallet skips proof.
           C. If proof_types_supported = "false" BUT c_nonce is provided, wallet should still generate proof to include the c_nonce (for replay protection).

         Steps:
         1. The wallet should check if there is the proof_types_supported in credential_configurations_supported(in Issuer Metadata), if its there it checks for the proof_type (i.e. jwt, ctw or ldp_vp) - currently the issuer implementation is does not contain this
             {
                  "cryptographic_binding_methods_supported": ["did:example", "jwk", "cose_key],
                  "proof_types_supported": [
                    {
                      "proof_type": "jwt",
                      "proof_signing_alg_values_supported": ["ES256", "EdDSA", "ES256K"]
                    },
                    {
                      "proof_type": "cwt",
                      "proof_signing_alg_values_supported": ["ES256"]
                    }
                  ]
                }


            It selects a proof type that both the issuer and wallet support (e.g., jwt or cwt).
            If proof_types_supported is defined and not empty → Select a supported proof type (jwt, cwt, etc.).
            If proof_types_supported is empty or missing:
                If c_nonce is provided, the wallet must still generate proof, but it must guess the proof type based on its own capabilities and common standards (e.g., jwt).
                If c_nonce is not provided, wallet skips proof since it's not required.

         2. When the proofType is defined we need to check the `proof_signing_alg_values_supported` (i.e. ES256) and choose a supported algorithm
            If a proof type was selected, choose a compatible algorithm from proof_signing_alg_values_supported.
            If no supported algorithm is found, we should stop with an error (but maybe for compatibility??).


         3. Then, the wallet should check the cryptographic_binding_methods_supported to understand with which key material will sign the proof (i.e. keys in JWK format `jwk`, keys expressed as a COSE Key `cose_key` or a specific did method, (e.g did:key), currently the issuer implementation is wrong
            If cryptographic_binding_methods_supported is specified, select a compatible method (did, jwk, etc.).
            If missing, default to a commonly supported method (e.g., did, jwk).
            If no compatible method is found, stop with an error (but maybe for compatibility??).

                sealed class ProofHandlingResult {
                    object SkipProof : ProofHandlingResult()  // No proof required, skip this step
                    object ProceedWithJWT : ProofHandlingResult() // Continue with JWT-based proof flow
                    data class TodoHandleOtherProofs(val proofTypes: List<ProofType>) : ProofHandlingResult() // Handle non-JWT proofs
                    object ProceedWithChosenProof : ProofHandlingResult() // Proof is not required, but c_nonce exists → choose proof or Continue with JWT-based proof flow
                }
        */

        // 4. The Wallet API constructs the credential request
        // The wallet constructs the proof based on the selected proof type, signing algorithm, and binding method.
        // If c_nonce is provided, it is included in the proof in a specific parameter based on `proof_type`.
        // The proof is included in the credential request and sent to the issuer.

        println("> Constructing credential request: vvv")

        val nonce = tokenResponse.cNonce
        println("cNonce: $nonce")

        val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(
            credentialOffer = credentialOffer,
            providerMetadata = resolvedCIProviderMetadata
        )
        println("Offered credentials (${offeredCredentials.size}x) are: $offeredCredentials")

        assertEquals(1, offeredCredentials.size)

        println("Using #0 credential offer")

        val offeredCredential = offeredCredentials[0]
        println("Offered credential: $offeredCredential")

        val holderDid = WALLET_DID
        val holderKey = JWKKey.importJWK(WALLET_KEY).getOrThrow()
        val holderKeyId = holderKey.getKeyId()
        val proofKeyId = "$holderDid#$holderKeyId"

        println("Data for PoP: holderDid = $holderDid, holderKey = $holderKey, holderKeyId = $holderKeyId, proofKeyId = $proofKeyId")

        val proofOfPossession = ProofOfPossession.JWTProofBuilder(
            issuerUrl = resolvedCIProviderMetadata.credentialIssuer!!,
            clientId = WALLET_CLIENT_ID,
            nonce = nonce,
            keyId = proofKeyId
        ).build(
            key = holderKey
        )
        println("Built proof of possession: $proofOfPossession")

        val accessToken = tokenResponse.accessToken!!
        println("Access token of token response was: $accessToken")

        val credentialRequest = CredentialRequest.forOfferedCredential(
            offeredCredential = offeredCredential,
            proof = proofOfPossession
        )
        println("Credential request for offered credential is: $credentialRequest")

        // 5. The Wallet API sends the credential request
        println("Sending credential request...")
        val credentialResponse = OpenID4VCI.sendCredentialRequest(
            providerMetadata = resolvedCIProviderMetadata,
            accessToken = accessToken,
            credentialRequest = credentialRequest
        )
        println("Response to credential request: $credentialResponse")
        println("-> Credential response success: ${credentialResponse.isSuccess}")

        assertNotNull(credentialResponse.credential)

        println("Signed credential is: ${credentialResponse.credential}")
        println("Credential data is: ${credentialResponse.credential.jsonPrimitive.content.decodeJws().payload["vc"]?.jsonObject}")
    }
}

fun testHttpClient(token: String? = null, doFollowRedirects: Boolean = true) = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(
            Json {
                explicitNulls = false
                encodeDefaults = true
            }
        )
    }
    install(DefaultRequest) {
        contentType(ContentType.Application.Json)
        host = "127.0.0.1"
        port = 22222

        if (token != null) bearerAuth(token)
    }
    install(Logging) {
        level = LogLevel.ALL
    }
    followRedirects = doFollowRedirects
}

fun HttpResponse.expectSuccess() = also {
    check(it.status.isSuccess()) { "Expected success for request ${it.request.url}, but was: $it" }
}

private val openBadgeCredentialData = """
      {
          "@context": [
            "https://www.w3.org/2018/credentials/v1",
            "https://purl.imsglobal.org/spec/ob/v3p0/context.json"
          ],
          "id": "urn:uuid:THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION (see below)",
          "type": [
            "VerifiableCredential",
            "OpenBadgeCredential"
          ],
          "name": "JFF x vc-edu PlugFest 3 Interoperability",
          "issuer": {
            "type": [
              "Profile"
            ],
            "name": "Jobs for the Future (JFF)",
            "url": "https://www.jff.org/",
            "image": "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png"
          },
          "credentialSubject": {
            "type": [
              "AchievementSubject"
            ],
            "achievement": {
              "id": "urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926",
              "type": [
                "Achievement"
              ],
              "name": "JFF x vc-edu PlugFest 3 Interoperability",
              "description": "This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.",
              "criteria": {
                "type": "Criteria",
                "narrative": "Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials."
              },
              "image": {
                "id": "https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png",
                "type": "Image"
              }
            }
          }
        }
      """.trimIndent()
