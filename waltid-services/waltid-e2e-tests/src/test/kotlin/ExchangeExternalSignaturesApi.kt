import E2ETestWebService.loadResource
import id.walt.commons.web.plugins.httpJson
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.utils.randomUUID
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.service.keys.SingleKeyResponse
import id.walt.webwallet.web.controllers.PrepareOID4VPRequest
import id.walt.webwallet.web.controllers.PrepareOID4VPResponse
import id.walt.webwallet.web.controllers.SubmitOID4VPRequest
import id.walt.webwallet.web.model.EmailAccountRequest
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
import io.ktor.server.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExchangeExternalSignatures {

    private var client: HttpClient
    private var authApi: AuthApi
    private lateinit var keysApi: KeysApi
    private lateinit var didsApi: DidsApi
    private lateinit var issuerApi: IssuerApi
    private lateinit var exchangeApi: ExchangeApi
    private lateinit var credentialsApi: CredentialsApi
    private lateinit var holderKey: JWKKey
    private val verifierSessionApi: Verifier.SessionApi
    private val verifierVerificationApi: Verifier.VerificationApi

    //    private val holderKey = runBlocking {
//        JWKKey.generate(KeyType.Ed25519)
//    }
    private lateinit var holderDID: String

    private val accountRequest = EmailAccountRequest(
        email = "${randomUUID()}@email.com",
        password = randomUUID(),
    )
    private var accountId = UUID.NIL
    private var walletId = UUID.NIL

    private fun testHttpClient(token: String? = null, doFollowRedirects: Boolean = true) = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(httpJson)
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

    private suspend fun registerAccountAndLogin() {
        authApi.register(accountRequest)
        authApi.login(
            accountRequest,
        ) {
            client = testHttpClient(token = it["token"]!!.jsonPrimitive.content)
            authApi = AuthApi(client)
        }
        authApi.userInfo(HttpStatusCode.OK) {
            accountId = it.id
        }
        authApi.userSession()
        authApi.userWallets(accountId) {
            walletId = it.wallets.first().id
            println("Selected wallet: $walletId")
        }
    }

    private fun prepareApis() {
        keysApi = KeysApi(client)
        didsApi = DidsApi(client)
        issuerApi = IssuerApi(client)
        exchangeApi = ExchangeApi(client)
        credentialsApi = CredentialsApi(client)
    }

    private suspend fun cleanWallet() {
        var response = client.get("/wallet-api/wallet/$walletId/keys").expectSuccess()
        val keyList = response.body<List<SingleKeyResponse>>()
        for (key in keyList) {
            keysApi.delete(walletId, key.keyId.id)
        }
        response = client.get("/wallet-api/wallet/$walletId/dids").expectSuccess()
        val didList = response.body<List<WalletDid>>()
        for (did in didList) {
            didsApi.delete(walletId, did.did)
        }
    }

    /*
    DO NOT DELETE, WILL BE OF USE WHEN EXTERNAL SIGNATURE ENDPOINTS FOR OID4VCI FLOWS
    WILL BE IMPLEMENTED.
    TODO: DELETE THIS COMMENT WHEN OID4VCI PULL REQUEST IS TO BE MERGED.
    * */
//        private suspend fun initializeWallet() {
//            cleanWallet()
//            //import the holder's public key to the wallet API
//            keysApi.import(walletId, holderKey.getPublicKey().exportJWK())
//            //check that its the only key in the wallet
//            var response = client.get("/wallet-api/wallet/$walletId/keys").expectSuccess()
//            val keyList = response.body<List<SingleKeyResponse>>()
//            assert(keyList.size == 1) { "There should only be one key in the holder's wallet now" }
//            assert(keyList[0].keyId.id == holderKey.getPublicKey().getKeyId()) { "keyId mismatch" }
//            //generate a DID
//            didsApi.create(
//                walletId,
//                DidsApi.DidCreateRequest(
//                    method = "jwk",
//                    holderKey.getPublicKey().getKeyId(),
//                ),
//            )
//            //check that it is the only did in the wallet
//            response = client.get("/wallet-api/wallet/$walletId/dids").expectSuccess()
//            val didList = response.body<List<WalletDid>>()
//            assert(didList.size == 1) { "There should only be one did in the holder's wallet now" }
//            holderDID = didList[0].did
//        }

    /*
    TODO: SWAP THIS FUNCTION WITH THE ONE ABOVE WHEN EXTERNAL SIGNATURE ENDPOINTS FOR OID4VCI FLOWS ARE IMPLEMENTED.
    * */
    private suspend fun getWalletDefaultGeneratedParams() {
        var response = client.get("/wallet-api/wallet/$walletId/keys").expectSuccess()
        val keyList = response.body<List<SingleKeyResponse>>()
        assert(keyList.size == 1) { "Expecting to have a default key generated" }
        response = client.get("/wallet-api/wallet/$walletId/keys/${keyList[0].keyId.id}/export") {
            url {
                parameters.append("format", "JWK")
                parameters.append("loadPrivateKey", "true")
            }
        }.expectSuccess()
        holderKey = response.bodyAsText().let {
            JWKKey.importJWK(it)
        }.getOrThrow()
        response = client.get("/wallet-api/wallet/$walletId/dids").expectSuccess()
        val didList = response.body<List<WalletDid>>()
        assert(didList.size == 1) { "Expecting to have a default did generated" }
        holderDID = didList.first().did

    }

    private suspend fun issueCredentialToWallet() {
        lateinit var offerURL: String
        issuerApi.issue(
            Json.decodeFromString<IssuanceRequest>(loadResource("issuance/openbadgecredential-issuance-request.json"))
        ) {
            offerURL = it
            println("offer: $offerURL")
        }
        var response = client.post("/wallet-api/wallet/$walletId/exchange/useOfferRequest") {
            url {
                parameters.append("did", holderDID)
                parameters.append("requireUserInput", "false")
            }
            setBody(offerURL)
        }.expectSuccess()
        val credList = response.body<List<WalletCredential>>()
        assert(credList.size == 1) { "Expecting to have a credential in the wallet now" }
        assertFalse(credList[0].pending,"Credential should not be pending")

    }

    init {
        client = testHttpClient()
        verifierSessionApi = Verifier.SessionApi(client)
        verifierVerificationApi = Verifier.VerificationApi(client)
        authApi = AuthApi(client)

        runBlocking {
            registerAccountAndLogin()
            prepareApis()
            getWalletDefaultGeneratedParams()
//                initializeWallet()
            issueCredentialToWallet()
        }
    }

    suspend fun executeTestCases() {
        lateinit var presentationRequestURL: String
        lateinit var verificationID: String
        lateinit var resolvedPresentationRequestURL: String
        lateinit var presentationDefinition: String
        lateinit var matchedCredentialList: List<WalletCredential>
        var response = client.get("/wallet-api/wallet/$walletId/credentials").expectSuccess()
        val walletCredentialList = response.body<List<WalletCredential>>()
        verifierVerificationApi.verify(loadResource("presentation/openbadgecredential-presentation-request.json")) {
            presentationRequestURL = it
            assert(presentationRequestURL.contains("presentation_definition_uri="))
            assert(!presentationRequestURL.contains("presentation_definition="))
            verificationID = Url(presentationRequestURL).parameters.getOrFail("state")
        }
        exchangeApi.resolvePresentationRequest(
            walletId,
            presentationRequestURL
        ) {
            resolvedPresentationRequestURL = it
            presentationDefinition = Url(resolvedPresentationRequestURL).parameters.getOrFail("presentation_definition")
        }
        exchangeApi.matchCredentialsForPresentationDefinition(
            walletId,
            presentationDefinition,
            walletCredentialList.map { it.id },
        ) {
            matchedCredentialList = it
        }
        response = client.post("/wallet-api/wallet/$walletId/exchange/external_signatures/oid4vp/prepare") {
            setBody(
                PrepareOID4VPRequest(
                    did = holderDID,
                    presentationRequestURL,
                    matchedCredentialList.map { it.id },
                )
            )
        }.expectSuccess()
        val prepareResponse = response.body<PrepareOID4VPResponse>()
        //na kanoume sign edw
        val signedVPToken = holderKey.signJws(
            prepareResponse.vpTokenParams.payload.toByteArray(),
            prepareResponse.vpTokenParams.header,
        )
        response = client.post("/wallet-api/wallet/$walletId/exchange/external_signatures/oid4vp/submit") {
            setBody(
                SubmitOID4VPRequest(
                    signedVPToken,
                    prepareResponse,
                )
            )
        }.expectSuccess()
        verifierSessionApi.get(verificationID) {
            assert(it.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null) { "Received no valid token response!" }
            assert(it.tokenResponse?.presentationSubmission != null) { "should have a presentation submission after submission" }

            assert(it.verificationResult == true) { "overall verification should be valid" }
            it.policyResults.let {
                require(it != null) { "policyResults should be available after running policies" }
                assert(it.size > 1) { "no policies have run" }
            }
        }
    }
}