import COSE.AlgorithmID
import E2ETestWebService.loadResource
import cbor.Cbor
import com.nimbusds.jose.jwk.ECKey
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.utils.randomUUID
import id.walt.issuer.issuance.IssuanceExamples
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.dataelement.MapElement
import id.walt.oid4vc.data.AuthenticationMethod
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.ProofType
import id.walt.sdjwt.utils.Base64Utils.encodeToBase64Url
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.service.exchange.IssuanceServiceExternalSignatures
import id.walt.webwallet.service.keys.SingleKeyResponse
import id.walt.webwallet.web.controllers.exchange.*
import id.walt.webwallet.web.model.EmailAccountRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class ExchangeExternalSignatures {

    private var client: HttpClient
    private var authApi: AuthApi
    private lateinit var keysApi: KeysApi
    private lateinit var didsApi: DidsApi
    private lateinit var issuerApi: IssuerApi
    private lateinit var exchangeApi: ExchangeApi
    private lateinit var credentialsApi: CredentialsApi
    private lateinit var holderDID: String

    private val verifierSessionApi: Verifier.SessionApi
    private val verifierVerificationApi: Verifier.VerificationApi

    private val holderKey = runBlocking {
        JWKKey.generate(KeyType.secp256r1)
    }

    private val accountRequest = EmailAccountRequest(
        email = "${randomUUID()}@email.com",
        password = randomUUID(),
    )
    private var accountId = UUID.NIL
    private var walletId = UUID.NIL

    private suspend fun registerAccountAndLogin() {
        authApi.register(accountRequest)
        authApi.login(
            accountRequest,
        ) {
            client = E2ETest.testHttpClient(token = it["token"]!!.jsonPrimitive.content)
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

    private suspend fun clearWalletCredentials() {
        val response = client.get("/wallet-api/wallet/$walletId/credentials").expectSuccess()
        val credsList = response.body<List<WalletCredential>>()
        credsList.forEach {
            credentialsApi.delete(
                walletId,
                it.id,
                true,
            )
        }
    }

    private suspend fun initializeWallet() {
        //import the holder's public key to the wallet API
        keysApi.import(walletId, holderKey.getPublicKey().exportJWK())
        //check that it's the only key in the wallet
        var response = client.get("/wallet-api/wallet/$walletId/keys").expectSuccess()
        val keyList = response.body<List<SingleKeyResponse>>()
        assert(keyList.size == 1) { "There should only be one key in the holder's wallet now" }
        assert(keyList[0].keyId.id == holderKey.getPublicKey().getKeyId()) { "keyId mismatch" }
        //generate a DID
        didsApi.create(
            walletId,
            DidsApi.DidCreateRequest(
                method = "jwk",
                holderKey.getPublicKey().getKeyId(),
            ),
        )
        //check that it is the only did in the wallet
        response = client.get("/wallet-api/wallet/$walletId/dids").expectSuccess()
        val didList = response.body<List<WalletDid>>()
        assert(didList.size == 1) { "There should only be one did in the holder's wallet now" }
        holderDID = didList[0].did
    }

    init {
        client = E2ETest.testHttpClient()
        verifierSessionApi = Verifier.SessionApi(client)
        verifierVerificationApi = Verifier.VerificationApi(client)
        authApi = AuthApi(client)

        runBlocking {
            registerAccountAndLogin()
            prepareApis()
            cleanWallet()
        }
    }

    suspend fun executeTestCases() {
        val openBadgeIssuanceRequest = Json.decodeFromString<IssuanceRequest>(
            loadResource("issuance/openbadgecredential-issuance-request.json")
        ).apply {
            this.credentialFormat = CredentialFormat.jwt_vc_json
        }
        val universityDegreeIssuanceRequest = Json.decodeFromString<IssuanceRequest>(
            loadResource("issuance/universitydegree-issuance-request.json")
        ).apply {
            this.credentialFormat = CredentialFormat.jwt_vc_json
        }
        val mDocIssuanceRequest = Json.decodeFromString<IssuanceRequest>(
            IssuanceExamples.mDLCredentialIssuanceData
        ).copy(
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            credentialFormat = CredentialFormat.mso_mdoc,
        )
        val openbadgePresentationRequest = loadResource(
            "presentation/openbadgecredential-presentation-request.json"
        )
        val openbadgeUniversityDegreePresentationRequest = loadResource(
            "presentation/batch-openbadge-universitydegree-presentation-request.json"
        )
        val openbadgeSdJwtIssuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(E2ETest.sdjwtCredential).apply {
            credentialFormat = CredentialFormat.sd_jwt_vc
        }
        initializeWallet()
        testPreAuthorizedOID4VCI(
            issuanceRequests = listOf(openBadgeIssuanceRequest),
        )
        testOID4VP(openbadgePresentationRequest)
        clearWalletCredentials()
        testPreAuthorizedOID4VCI(
            useOptionalParameters = false,
            issuanceRequests = listOf(openBadgeIssuanceRequest),
        )
        testOID4VP(openbadgePresentationRequest)
        clearWalletCredentials()
        testPreAuthorizedOID4VCI(
            useOptionalParameters = false,
            issuanceRequests = listOf(mDocIssuanceRequest),
        )
        clearWalletCredentials()
        testPreAuthorizedOID4VCI(
            useOptionalParameters = false,
            issuanceRequests = listOf(
                openBadgeIssuanceRequest,
                universityDegreeIssuanceRequest,
            ),
        )
        testOID4VP(openbadgeUniversityDegreePresentationRequest)
        clearWalletCredentials()
        testPreAuthorizedOID4VCI(
            useOptionalParameters = true,
            issuanceRequests = listOf(
                openBadgeIssuanceRequest,
                universityDegreeIssuanceRequest,
            ),
        )
        testOID4VP(openbadgeUniversityDegreePresentationRequest)
        clearWalletCredentials()
        testPreAuthorizedOID4VCI(
            useOptionalParameters = false,
            issuanceRequests = listOf(
                openbadgeSdJwtIssuanceRequest,
            ),
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun computeProofOfPossessionFromProofRequest(
        proofReq: IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossessionParameters,
    ): IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossession {
        return if (proofReq.proofOfPossessionParameters.proofType == ProofType.jwt) {
            IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossession(
                proofReq.offeredCredential,
                ProofType.jwt,
                holderKey.signJws(
                    proofReq.proofOfPossessionParameters.payload.toString().toByteArray(),
                    Json.decodeFromJsonElement<Map<String, JsonElement>>(proofReq.proofOfPossessionParameters.header),
                )
            )
        } else {
            val ecKey = ECKey.parseFromPEMEncodedObjects(holderKey.exportPEM()).toECKey()
            val cryptoProvider = SimpleCOSECryptoProvider(
                listOf(
                    COSECryptoProviderKeyInfo(
                        holderKey.getKeyId(),
                        AlgorithmID.ECDSA_256,
                        ecKey.toECPublicKey(),
                        ecKey.toECPrivateKey(),
                    )
                )
            )
            val headers = Cbor.decodeFromByteArray<MapElement>(
                Json.decodeFromJsonElement<ByteArray>(proofReq.proofOfPossessionParameters.header)
            )
            val payload = Json.decodeFromJsonElement<ByteArray>(proofReq.proofOfPossessionParameters.payload)
            IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossession(
                proofReq.offeredCredential,
                ProofType.cwt,
                cryptoProvider.sign1(
                    payload = payload,
                    headersProtected = headers,
                    null,
                    holderKey.getKeyId(),
                ).toCBOR().encodeToBase64Url(),
            )
        }
    }

    private suspend fun getOfferURLForIssuanceRequests(
        issuanceRequests: List<IssuanceRequest>,
    ): String {
        lateinit var offerURL: String
        assert(issuanceRequests.isNotEmpty()) { "How can I test the flow with no issuance requests?" }
        val firstIssuanceRequest = issuanceRequests.first()
        assertNotNull(firstIssuanceRequest.credentialFormat) { "Credential format must be defined to infer which issuer endpoint to call" }
        if (issuanceRequests.size == 1) {
            when (firstIssuanceRequest.credentialFormat) {
                CredentialFormat.mso_mdoc -> {
                    issuerApi.mdoc(
                        firstIssuanceRequest,
                    ) {
                        offerURL = it
                        println("offer: $it")
                    }
                }

                CredentialFormat.sd_jwt_vc -> {
                    issuerApi.sdjwt(
                        firstIssuanceRequest,
                    ) {
                        offerURL = it
                        println("offer: $it")
                    }
                }

                else -> {
                    issuerApi.jwt(
                        firstIssuanceRequest,
                    ) {
                        offerURL = it
                        println("offer: $it")
                    }
                }
            }
        } else {
            assertNotEquals(
                CredentialFormat.mso_mdoc,
                firstIssuanceRequest.credentialFormat,
                "There is no batch issuance endpoint for mDocs",
            )
            when (firstIssuanceRequest.credentialFormat) {
                CredentialFormat.jwt_vc_json -> {
                    issuerApi.issueJwtBatch(
                        issuanceRequests,
                    ) {
                        offerURL = it
                        println("offer: $it")
                    }
                }

                else -> {
                    issuerApi.issueSdJwtBatch(
                        issuanceRequests,
                    ) {
                        offerURL = it
                        println("offer: $it")
                    }
                }
            }
        }
        return offerURL
    }

    private suspend fun testPreAuthorizedOID4VCI(
        useOptionalParameters: Boolean = true,
        issuanceRequests: List<IssuanceRequest>,
    ) {
        val offerURL = getOfferURLForIssuanceRequests(issuanceRequests)
        var response = client.post("/wallet-api/wallet/$walletId/exchange/external_signatures/offer/prepare") {
            setBody(
                PrepareOID4VCIRequest(
                    did = if (useOptionalParameters) holderDID else null,
                    offerURL = offerURL,
                )
            )
        }.expectSuccess()
        val prepareResponse = response.body<PrepareOID4VCIResponse>()
        //compute the signatures here
        val offeredCredentialProofsOfPossession = prepareResponse.offeredCredentialsProofRequests.map {
            computeProofOfPossessionFromProofRequest(it)
        }
        assertNotNull(prepareResponse.accessToken) { "There should be an access token in the response of the prepare endpoint" }
        response = client.post("/wallet-api/wallet/$walletId/exchange/external_signatures/offer/submit") {
            setBody(
                SubmitOID4VCIRequest(
                    did = if (useOptionalParameters) holderDID else null,
                    offerURL = offerURL,
                    credentialIssuer = prepareResponse.credentialIssuer,
                    offeredCredentialProofsOfPossession = offeredCredentialProofsOfPossession,
                    accessToken = prepareResponse.accessToken,
                )
            )
        }.expectSuccess()
        val credList = response.body<List<WalletCredential>>()
        assert(credList.size == issuanceRequests.size) { "There should be as many credentials in the wallet as requested" }
    }

    private suspend fun testOID4VP(
        presentationRequest: String,
    ) {
        lateinit var presentationRequestURL: String
        lateinit var verificationID: String
        lateinit var resolvedPresentationRequestURL: String
        lateinit var presentationDefinition: String
        lateinit var matchedCredentialList: List<WalletCredential>
        var response = client.get("/wallet-api/wallet/$walletId/credentials").expectSuccess()
        val walletCredentialList = response.body<List<WalletCredential>>()
        verifierVerificationApi.verify(presentationRequest) {
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
        response = client.post("/wallet-api/wallet/$walletId/exchange/external_signatures/presentation/prepare") {
            setBody(
                PrepareOID4VPRequest(
                    did = holderDID,
                    presentationRequestURL,
                    matchedCredentialList.map { it.id },
                )
            )
        }.expectSuccess()
        val prepareResponse = response.body<PrepareOID4VPResponse>()
        //client computes the externally provided signature value
        val signedVPToken = holderKey.signJws(
            prepareResponse.vpTokenParams.payload.toByteArray(),
            prepareResponse.vpTokenParams.header,
        )
        client.post("/wallet-api/wallet/$walletId/exchange/external_signatures/presentation/submit") {
            setBody(
                SubmitOID4VPRequest(
                    holderDID,
                    signedVPToken,
                    prepareResponse.presentationRequest,
                    prepareResponse.resolvedAuthReq,
                    prepareResponse.presentationSubmission,
                    prepareResponse.presentedCredentialIdList,

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