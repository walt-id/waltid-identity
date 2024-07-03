package id.walt.oid4vc.shared

import id.walt.crypto.IosKey
import id.walt.crypto.keys.KeyType
import id.walt.did.dids.DidService
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.providers.OpenIDClientConfig
import id.walt.oid4vc.requests.CredentialOfferRequest
import io.ktor.http.Url
import io.ktor.util.toMap
import kotlinx.serialization.json.jsonPrimitive

val testCIClientConfig = OpenIDClientConfig("test-client", null, redirectUri = "http://blank")

internal lateinit var credentialWallet: TestCredentialWallet

@Throws(Exception::class)
suspend fun generateEcKey(kid: String) = IosKey.create(kid, KeyType.secp256r1).exportJWK()

@Throws(Exception::class)
internal fun setupWallet(kid: String) {
    DidMethod.DidJwk(kid).let { didMethod ->
        credentialWallet = TestCredentialWallet(didMethod, CredentialWalletConfig("https://blank"))
    }
}

@Throws(Exception::class)
suspend fun acceptOffer(kid: String, offerUri: String) {
    DidService.minimalInit()

    if (!::credentialWallet.isInitialized) {
        setupWallet(kid)
    }

    println("// -------- WALLET ----------")
    println("// as WALLET: receive credential offer, either being called via deeplink or by scanning QR code")
    println("// parse credential URI")

    val credentialOffer: CredentialOffer = credentialWallet.resolveCredentialOffer(
        CredentialOfferRequest.fromHttpParameters(Url(offerUri).parameters.toMap())
    )

    checkNotNull(credentialOffer.credentialIssuer)// shouldNotBe null
    check(GrantType.pre_authorized_code.value in credentialOffer.grants.keys)
    checkNotNull(credentialOffer.grants[GrantType.pre_authorized_code.value]?.preAuthorizedCode)// shouldNotBe null

    println("// credoffer: $credentialOffer")

//    println("// get issuer metadata")
//    val providerMetadataUri =
//        credentialWallet.getCIProviderMetadataUrl(credentialOffer.credentialIssuer)
//    val providerMetadata: OpenIDProviderMetadata = ktorClient.get(providerMetadataUri).body()  //call.body<OpenIDProviderMetadata>()
//    println("providerMetadata: $providerMetadata")
//
//    checkNotNull(providerMetadata.credentialsSupported)
//
//    println("// resolve offered credentials")
//    val offeredCredentials = credentialOffer.resolveOfferedCredentials(providerMetadata)
//    println("offeredCredentials: $offeredCredentials")
//    check(offeredCredentials.size == 1 )
//    check(offeredCredentials.first().format == CredentialFormat.jwt_vc_json)
//    val offeredCredential = offeredCredentials.first()
//    println("offeredCredentials[0]: $offeredCredential")

    println("// fetch access token using pre-authorized code (skipping authorization step)")
    val credentialResp =
        credentialWallet.executePreAuthorizedCodeFlow(credentialOffer, testCIClientConfig, null)
//    var tokenReq = TokenRequest(
//        grantType = GrantType.pre_authorized_code,
//        clientId = testCIClientConfig.clientID,
//        redirectUri = credentialWallet.config.redirectUri,
//        preAuthorizedCode = credentialOffer.grants[GrantType.pre_authorized_code.value]!!.preAuthorizedCode,
//        userPin = null
//    )
//    println("tokenReq: $tokenReq")
//    println("dest: ${providerMetadata.tokenEndpoint}")
//
//    var tokenResp = ktorClient.submitForm(
//        providerMetadata.tokenEndpoint!!, formParameters = parametersOf(tokenReq.toHttpParameters())
//    ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
//    println("tokenResp: $tokenResp")
//
//    println(">>> Token response = success: ${tokenResp.isSuccess}")
//    check(tokenResp.isSuccess) //shouldBe true
//    checkNotNull(tokenResp.accessToken) //shouldNotBe null
//    checkNotNull(tokenResp.cNonce)// shouldNotBe null
//
//    println("// receive credential")
//    //ciTestProvider.deferIssuance = false
//    var nonce = tokenResp.cNonce!!
//
//    val credReq = CredentialRequest.forOfferedCredential(
//        offeredCredential = offeredCredential,
//        proof = credentialWallet.generateDidProof(credentialWallet.TEST_DID, "https://issuer.portal.walt.id", nonce)
//    )
//    println("credReq: $credReq")
//
//    val credentialResp = ktorClient.post(providerMetadata.credentialEndpoint!!) {
//        contentType(ContentType.Application.Json)
//        bearerAuth(tokenResp.accessToken!!)
//        setBody(credReq.toJSON())
//    }.body<JsonObject>().let { listOf(CredentialResponse.fromJSON(it)) }
    println("credentialResp: $credentialResp")

    check(credentialResp.all { it.isSuccess })// shouldBe true
    check(!credentialResp.all { it.isDeferred })// shouldBe false
//    check(credentialResp.format!! == CredentialFormat.jwt_vc_json)
//    credentialResp.credential.shouldBeInstanceOf<JsonPrimitive>()

    println("// parse and verify credential")
    credentialResp.forEach {
        it.credential!!.jsonPrimitive.content.let { println(">>> Issued credential: $it") }
    }
    credentialWallet.walletCredentials =
        credentialResp.map { it.credential!!.jsonPrimitive.content }
//    val credential = credentialResp.credential!!.jsonPrimitive.content

//    JwtSignaturePolicy().verify(credential, null, mapOf()).isSuccess shouldBe true

}

@Throws(Throwable::class)
fun authorize(kid: String, uri: String) = credentialWallet.acceptOpenId4VPAuthorize(uri, kid)