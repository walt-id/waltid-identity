package id.walt.issuance2

import id.walt.commons.logging.LoggingManager
import id.walt.commons.logging.setups.TraceLoggingSetup
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.*
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.errors.CredentialError
import id.walt.oid4vc.interfaces.CredentialResult
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.AuthorizationCodeResponse
import id.walt.oid4vc.responses.CredentialErrorCode
import id.walt.oid4vc.responses.CredentialResponse
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.JwtUtils
import id.walt.policies.policies.JwtSignaturePolicy
import id.walt.sdjwt.SDJwt
import id.walt.w3c.CredentialBuilder
import id.walt.w3c.CredentialBuilderType
import id.walt.w3c.issuance.Issuer.baseIssue
import io.ktor.http.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


val clientId = "test-client"
private val walletRedirect = "http://blank"
private const val TEST_WALLET_KEY1 =
    "{\"kty\":\"EC\",\"d\":\"uD-uxub011cplvr5Bd6MrIPSEUBsgLk-C1y3tnmfetQ\",\"use\":\"sig\",\"crv\":\"secp256k1\",\"kid\":\"48d8a34263cf492aa7ff61b6183e8bcf\",\"x\":\"TKaQ6sCocTDsmuj9tTR996tFXpEcS2EJN-1gOadaBvk\",\"y\":\"0TrIYHcfC93VpEuvj-HXTnyKt0snayOMwGSJA1XiDX8\"}"
private const val TEST_WALLET_DID_WEB1 = "did:web:entra.walt.id:holder"
private const val url = "http://localhost:9001"
private val issuerDidKey by lazy { runBlocking { JWKKey.generate(KeyType.Ed25519) } }
private val testIssuerDid by lazy { runBlocking { DidService.registerByKey("key", issuerDidKey).did } }
private val CI_TOKEN_KEY = runBlocking { JWKKey.generate(KeyType.RSA) }

private fun signToken(target: TokenTarget, payload: JsonObject, header: JsonObject? = null, keyId: String? = null, privKey: Key? = null) =
    runBlocking { CI_TOKEN_KEY.signJws(payload.toString().toByteArray()) }

@OptIn(ExperimentalEncodingApi::class)
fun parseTokenHeader(token: String): JsonObject {
    return token.substringBefore(".").let {
        Json.decodeFromString(Base64.UrlSafe.decode(it).decodeToString())
    }
}

private fun doGenerateCredential(credentialRequest: CredentialRequest): CredentialResult {
    if (credentialRequest.format == CredentialFormat.mso_mdoc) throw CredentialError(
        credentialRequest,
        CredentialErrorCode.unsupported_credential_format
    )
    val types = credentialRequest.credentialDefinition?.type ?: credentialRequest.credentialDefinition?.type ?: throw CredentialError(
        credentialRequest,
        CredentialErrorCode.unsupported_credential_type
    )
    val proofHeader = credentialRequest.proof?.jwt?.let { parseTokenHeader(it) } ?: throw CredentialError(
        credentialRequest,
        CredentialErrorCode.invalid_or_missing_proof,
        message = "Proof must be JWT proof"
    )
    val holderKid = proofHeader[JWTClaims.Header.keyID]?.jsonPrimitive?.content ?: throw CredentialError(
        credentialRequest,
        CredentialErrorCode.invalid_or_missing_proof,
        message = "Proof JWT header must contain kid claim"
    )
    return runBlocking {
        CredentialBuilder(CredentialBuilderType.W3CV2CredentialBuilder).apply {
            type = credentialRequest.credentialDefinition?.type ?: listOf("VerifiableCredential")
            issuerDid = testIssuerDid
            subjectDid = holderKid
        }.buildW3C().baseIssue(issuerDidKey, testIssuerDid, holderKid, mapOf(), mapOf(), mapOf(), mapOf())
    }.let {
        CredentialResult(CredentialFormat.jwt_vc_json, JsonPrimitive(it))
    }
}

suspend fun main() {

    LoggingManager.useLoggingSetup(TraceLoggingSetup)
    LoggingManager.setup()

    DidService.minimalInit()

    println("// -------- CREDENTIAL ISSUER ----------")
    // init credential offer for full authorization code flow




    val credOffer = CredentialOffer.Draft13.Builder(url)
        .addOfferedCredentialByReference("VerifiableId")
        .addAuthorizationCodeGrant("test-state")
        .build()
    println("offer: $credOffer")
    val issueReqUrl = OpenID4VCI.getCredentialOfferRequestUrl(credOffer)

    // Show credential offer request as QR code
    println(issueReqUrl)

    println("// -------- WALLET ----------")
    val parsedCredOffer = runBlocking { OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(issueReqUrl) }
    println("parse and resolve: $issueReqUrl")

    check(credOffer.toJSONString() == parsedCredOffer.toJSONString())

    println("Resolve metadata from ${parsedCredOffer.credentialIssuer}")
    val providerMetadata = runBlocking { OpenID4VCI.resolveCIProviderMetadata(parsedCredOffer) as OpenIDProviderMetadata.Draft13 }

    check(parsedCredOffer.credentialIssuer == providerMetadata.credentialIssuer)

    println("// resolve offered credentials")
    val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedCredOffer, providerMetadata)
    println("offeredCredentials: $offeredCredentials")

    //check(offeredCredentials.size == 1)
    offeredCredentials.any { it.format ==  CredentialFormat.jwt_vc_json}
    offeredCredentials.any { it.credentialDefinition?.type?.last() == "VerifiableId" }

    val offeredCredential = offeredCredentials.first()
    println("offeredCredentials[0]: $offeredCredential")

    println("// go through full authorization code flow to receive offered credential")
    println("// auth request (short-cut, without pushed authorization request)")
    val authReq = AuthorizationRequest(
        setOf(ResponseType.Code), clientId,
        redirectUri = walletRedirect,
        issuerState = parsedCredOffer.grants[GrantType.authorization_code.value]!!.issuerState
    )
    println("authReq: $authReq")

    println("// -------- CREDENTIAL ISSUER ----------")

    // create issuance session and generate authorization code
    val authCodeResponse: AuthorizationCodeResponse = AuthorizationCodeResponse.success("test-code")
    val redirectUri = authCodeResponse.toRedirectUri(authReq.redirectUri ?: TODO(), authReq.responseMode ?: ResponseMode.query)
    println("Redirect: $redirectUri")
    Url(redirectUri).let {
        check(ResponseType.Code.name.lowercase() in it.parameters.names())
        check(it.parameters[ResponseType.Code.name.lowercase()] == authCodeResponse.code)
    }

    println("// -------- WALLET ----------")
    println("// token req")
    val tokenReq =
        TokenRequest.AuthorizationCode(
            clientId = clientId,
            code = authCodeResponse.code!!
        )
    println("tokenReq: $tokenReq")

    println("// -------- CREDENTIAL ISSUER ----------")

    // TODO: Validate authorization code
    // TODO: generate access token
    val accessToken = signToken(
        target = TokenTarget.ACCESS,
        payload = buildJsonObject {
            put(JWTClaims.Payload.subject, "test-issuance-session")
            put(JWTClaims.Payload.issuer, "http://blank?ci-provider=baseUrl")
            put(JWTClaims.Payload.audience, TokenTarget.ACCESS.name)
            put(JWTClaims.Payload.jwtID, "token-id")
        })
    val cNonce = "pop-nonce"
    val tokenResponse: TokenResponse = TokenResponse.success(accessToken, "bearer", cNonce = cNonce)

    println("// -------- WALLET ----------")
    check(tokenResponse.isSuccess)
    check(tokenResponse.accessToken != null)
    check(tokenResponse.cNonce != null)

    println("// receive credential")
    val nonce = tokenResponse.cNonce!!
    val holderDid = TEST_WALLET_DID_WEB1
//        val holderKey = runBlocking { JWKKey.importJWK(TEST_WALLET_KEY1) }.getOrThrow()
    val holderKey = JWKKey.importJWK(TEST_WALLET_KEY1).getOrThrow()
//        val holderKeyId = runBlocking { holderKey.getKeyId() }
    val holderKeyId = holderKey.getKeyId()
    val proofKeyId = "$holderDid#$holderKeyId"
    val proofOfPossession =
        ProofOfPossession.JWTProofBuilder("http://blank?ci-provider=baseUrl", null, nonce, proofKeyId).build(holderKey)

    val credReq = CredentialRequest.forOfferedCredential(offeredCredential, proofOfPossession)
    println("credReq: $credReq")

    println("// -------- CREDENTIAL ISSUER ----------")
    val parsedHolderKeyId = credReq.proof?.jwt?.let { JwtUtils.parseJWTHeader(it) }?.get("kid")?.jsonPrimitive?.content

    check(parsedHolderKeyId != null)
    check(parsedHolderKeyId.startsWith("did:"))

    val parsedHolderDid = parsedHolderKeyId.substringBefore("#") // todo: null check
//        val resolvedKeyForHolderDid = runBlocking { DidService.resolveToKey(parsedHolderDid) }.getOrThrow()
    println("New holder: $parsedHolderDid")
    val resolvedKeyForHolderDid = DidService.resolveToKey(parsedHolderDid).getOrThrow()

    val validPoP =
        credReq.proof?.validateJwtProof(resolvedKeyForHolderDid, "http://blank?ci-provider=baseUrl", null, nonce, parsedHolderKeyId)
    check(validPoP!!)


    // todo: handle deferred issuance
    val generatedCredential = doGenerateCredential(credReq).credential
    check(generatedCredential != null)

    val credentialResponse: CredentialResponse = CredentialResponse.success(credReq.format, credential = generatedCredential)
    println("Credential response: $credentialResponse")

    println("// -------- WALLET ----------")
    check(credentialResponse.isSuccess)
    check(!credentialResponse.isDeferred)
    check(credentialResponse.format == CredentialFormat.jwt_vc_json)
    check(credentialResponse.credential!!.instanceOf(JsonPrimitive::class))

    println("// parse and verify credential")
    val credential = credentialResponse.credential!!.jsonPrimitive.content
    println(">>> Issued credential: $credential")
    verifyIssuerAndSubjectId(
        SDJwt.parse(credential).fullPayload["vc"]?.jsonObject!!,
        testIssuerDid, TEST_WALLET_DID_WEB1
    )
    check(JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess)
}

private fun verifyIssuerAndSubjectId(credential: JsonObject, issuerId: String, subjectId: String) {
    check(issuerId == credential["issuer"]?.jsonPrimitive?.contentOrNull)
    check(
        subjectId == credential["credentialSubject"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull?.substringBefore("#")
    ) // FIXME <-- remove
}
