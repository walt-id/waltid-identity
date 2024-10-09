@file:OptIn(ExperimentalUuidApi::class)

import COSE.AlgorithmID
import cbor.Cbor
import com.nimbusds.jose.jwk.ECKey
import id.walt.commons.interop.LspPotentialInterop
import id.walt.commons.testing.E2ETest.getBaseURL
import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.credentials.utils.VCFormat
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.utils.randomUUID
import id.walt.issuer.issuance.IssuanceExamples
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.lspPotential.LspPotentialIssuanceInterop
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.dataelement.MapElement
import id.walt.oid4vc.data.AuthenticationMethod
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.OpenId4VPProfile
import id.walt.oid4vc.data.ProofType
import id.walt.sdjwt.SDField
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.utils.Base64Utils.encodeToBase64Url
import id.walt.verifier.oidc.RequestedCredential
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.service.exchange.IssuanceServiceExternalSignatures
import id.walt.webwallet.service.keys.SingleKeyResponse
import id.walt.webwallet.web.controllers.exchange.models.oid4vci.PrepareOID4VCIRequest
import id.walt.webwallet.web.controllers.exchange.models.oid4vci.PrepareOID4VCIResponse
import id.walt.webwallet.web.controllers.exchange.models.oid4vci.SubmitOID4VCIRequest
import id.walt.webwallet.web.controllers.exchange.models.oid4vp.IETFSdJwtVpTokenProof
import id.walt.webwallet.web.controllers.exchange.models.oid4vp.PrepareOID4VPRequest
import id.walt.webwallet.web.controllers.exchange.models.oid4vp.PrepareOID4VPResponse
import id.walt.webwallet.web.controllers.exchange.models.oid4vp.SubmitOID4VPRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
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
    private var accountId = Uuid.NIL
    private var walletId = Uuid.NIL

    //credential issuance requests
    //w3c jwt_vc_json - no disclosures
    private val openBadgeIssuanceRequest = Json.decodeFromString<IssuanceRequest>(
        loadResource("issuance/openbadgecredential-issuance-request.json")
    ).apply {
        this.credentialFormat = CredentialFormat.jwt_vc_json
    }
    private val universityDegreeIssuanceRequest = Json.decodeFromString<IssuanceRequest>(
        loadResource("issuance/universitydegree-issuance-request.json")
    ).apply {
        this.credentialFormat = CredentialFormat.jwt_vc_json
    }

    //w3c jwt_vc_json - with disclosures
    private val openbadgeSdJwtIssuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(WaltidServicesE2ETests.sdjwtCredential).apply {
        credentialFormat = CredentialFormat.jwt_vc_json
    }

    //ietf sd_jwt_vc - with disclosures
    private val identityCredentialIETFSdJwtX5cIssuanceRequest = IssuanceRequest(
        Json.parseToJsonElement(KeySerialization.serializeKey(LspPotentialIssuanceInterop.POTENTIAL_ISSUER_JWK_KEY)).jsonObject,
        "identity_credential_vc+sd-jwt",
        credentialData = buildJsonObject {
            put("family_name", "Doe")
            put("given_name", "John")
            put("birthdate", "1940-01-01")
        },
        "identity_credential",
        x5Chain = listOf(LspPotentialInterop.POTENTIAL_ISSUER_CERT),
        trustedRootCAs = listOf(LspPotentialInterop.POTENTIAL_ROOT_CA_CERT),
        selectiveDisclosure = SDMap(
            mapOf(
                "birthdate" to SDField(sd = true)
            )
        ),
        credentialFormat = CredentialFormat.sd_jwt_vc
    )
    private val identityCredentialIETFSdJwtDidIssuanceRequest = IssuanceRequest(
        Json.parseToJsonElement(KeySerialization.serializeKey(LspPotentialIssuanceInterop.POTENTIAL_ISSUER_JWK_KEY)).jsonObject,
        "identity_credential_vc+sd-jwt",
        credentialData = buildJsonObject {
            put("family_name", "Doe")
            put("given_name", "John")
            put("birthdate", "1940-01-01")
        },
        mdocData = null,
        selectiveDisclosure = SDMap(
            mapOf(
                "birthdate" to SDField(sd = true)
            )
        ),
        issuerDid = LspPotentialIssuanceInterop.ISSUER_DID,
        credentialFormat = CredentialFormat.sd_jwt_vc
    )

    //mDoc
    private val mDocIssuanceRequest = Json.decodeFromString<IssuanceRequest>(
        IssuanceExamples.mDLCredentialIssuanceData
    ).copy(
        authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
        credentialFormat = CredentialFormat.mso_mdoc,
    )

    //presentation requests
    //w3c jwt_vc_json
    private val openbadgePresentationRequest = loadResource(
        "presentation/openbadgecredential-presentation-request.json"
    )
    private val openbadgeSdJwtPresentationRequest = loadResource(
        "presentation/openbadgecredential-sd-presentation-request.json"
    )
    private val openbadgeUniversityDegreePresentationRequest = loadResource(
        "presentation/batch-openbadge-universitydegree-presentation-request.json"
    )

    private suspend fun registerAccountAndLogin() {
        authApi.register(accountRequest)
        authApi.login(
            accountRequest,
        ) {
            client = WaltidServicesE2ETests.testHttpClient(token = it["token"]!!.jsonPrimitive.content)
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
        assert(keyList[0].keyId.id == holderKey.getKeyId()) { "keyId mismatch" }
        //generate a DID
        didsApi.create(
            walletId,
            DidsApi.DidCreateRequest(
                method = "jwk",
                holderKey.getKeyId(),
            ),
        )
        //check that it is the only did in the wallet
        response = client.get("/wallet-api/wallet/$walletId/dids").expectSuccess()
        val didList = response.body<List<WalletDid>>()
        assert(didList.size == 1) { "There should only be one did in the holder's wallet now" }
        holderDID = didList[0].did
    }

    init {
        client = WaltidServicesE2ETests.testHttpClient()
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
        initializeWallet()
        regularJwtVcJsonTestCases()
        mDocTestCases()
        w3cSdJwtVcTestCases()
        ietfSdJwtVcTestCases()
    }

    /**
     * The following test function fails because the Verifier is currently
     * unable to handle multiple vp token entries in the token response.
     * The Verifier only checks for JsonPrimitive and JsonObject types to
     * decode the vp token.
     * @see [id.walt.verifier.oidc.OIDCVerifierService.doVerify]
     */
    private suspend fun combinedW3CIETFSdJwtVCTestCase() {
        testPreAuthorizedOID4VCI(
            useOptionalParameters = false,
            issuanceRequests = listOf(
                identityCredentialIETFSdJwtDidIssuanceRequest,
            ),
        )
        val openbadgeSdJwtIssuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(WaltidServicesE2ETests.sdjwtCredential).apply {
            credentialFormat = CredentialFormat.jwt_vc_json
        }
        testPreAuthorizedOID4VCI(
            useOptionalParameters = false,
            issuanceRequests = listOf(
                openbadgeSdJwtIssuanceRequest,
            ),
        )
        testOID4VPW3CIETFSdJwtVc(true)
    }

    private suspend fun regularJwtVcJsonTestCases() {
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
    }

    private suspend fun mDocTestCases() {
        testPreAuthorizedOID4VCI(
            useOptionalParameters = false,
            issuanceRequests = listOf(mDocIssuanceRequest),
        )
        clearWalletCredentials()
    }

    private suspend fun w3cSdJwtVcTestCases() {
        testPreAuthorizedOID4VCI(
            useOptionalParameters = false,
            issuanceRequests = listOf(
                openbadgeSdJwtIssuanceRequest,
            ),
        )
        testOID4VP(openbadgeSdJwtPresentationRequest)
        testOID4VP(openbadgeSdJwtPresentationRequest, true)
        clearWalletCredentials()
    }

    private suspend fun ietfSdJwtVcTestCases() {
        testPreAuthorizedOID4VCI(
            useOptionalParameters = false,
            issuanceRequests = listOf(
                identityCredentialIETFSdJwtDidIssuanceRequest,
            ),
        )
        testOID4VPSdJwtVc()
        testOID4VPSdJwtVc(true)
        clearWalletCredentials()
        testPreAuthorizedOID4VCI(
            useOptionalParameters = false,
            issuanceRequests = listOf(
                identityCredentialIETFSdJwtX5cIssuanceRequest,
            ),
        )
        testOID4VPSdJwtVc()
        testOID4VPSdJwtVc(true)
        clearWalletCredentials()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun computeProofOfPossessionFromProofRequest(
        proofReq: IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossessionParameters,
    ): IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossession {
        return if (proofReq.proofOfPossessionParameters.proofType == ProofType.jwt) {
            IssuanceServiceExternalSignatures.OfferedCredentialProofOfPossession(
                proofReq.offeredCredential, ProofType.jwt, holderKey.signJws(
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
                Json.decodeFromJsonElement<String>(proofReq.proofOfPossessionParameters.header).decodeFromBase64()
            )
            val payload = Json.decodeFromJsonElement<String>(proofReq.proofOfPossessionParameters.payload).decodeFromBase64()
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
                SubmitOID4VCIRequest.build(
                    prepareResponse,
                    credentialIssuer = prepareResponse.credentialIssuer,
                    offeredCredentialProofsOfPossession = offeredCredentialProofsOfPossession,
                )
            )
        }.expectSuccess()
        val credList = response.body<List<WalletCredential>>()
        assert(credList.size == issuanceRequests.size) { "There should be as many credentials in the wallet as requested" }
    }

    private suspend fun testOID4VP(
        presentationRequest: String,
        addDisclosures: Boolean = false,
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
            walletId, presentationRequestURL
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
        val prepareRequest = PrepareOID4VPRequest(
            did = holderDID,
            presentationRequest = presentationRequestURL,
            selectedCredentialIdList = matchedCredentialList.map { it.id },
            disclosures = if (addDisclosures) matchedCredentialList.filter { it.disclosures != null }.associate {
                Pair(it.id, listOf(it.disclosures!!))
            } else null,
        )
        println(prepareRequest)
        response = client.post("/wallet-api/wallet/$walletId/exchange/external_signatures/presentation/prepare") {
            setBody(prepareRequest)
        }.expectSuccess()
        val prepareResponse = response.body<PrepareOID4VPResponse>()
        client.post("/wallet-api/wallet/$walletId/exchange/external_signatures/presentation/submit") {
            setBody(SubmitOID4VPRequest.build(prepareResponse,
                disclosures = if (addDisclosures) matchedCredentialList.filter { it.disclosures != null }.associate {
                    Pair(it.id, listOf(it.disclosures!!))
                } else null,
                w3cJwtVpProof = prepareResponse.w3CJwtVpProofParameters?.let { params ->
                    holderKey.signJws(
                        params.payload.toJsonElement().toString().toByteArray(),
                        params.header,
                    )
                },
                ietfSdJwtVpProofs = prepareResponse.ietfSdJwtVpProofParameters?.map { params ->
                    IETFSdJwtVpTokenProof(
                        credentialId = params.credentialId, sdJwtVc = params.sdJwtVc, vpTokenProof = holderKey.signJws(
                            params.payload.toJsonElement().toString().toByteArray(),
                            params.header,
                        )
                    )
                })
            )
        }.expectSuccess()
        verifierSessionApi.get(verificationID) { sessionInfo ->
            assert(sessionInfo.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null) { "Received no valid token response!" }
            assert(sessionInfo.tokenResponse?.presentationSubmission != null) { "should have a presentation submission after submission" }

            assert(sessionInfo.verificationResult == true) { "overall verification should be valid" }
            sessionInfo.policyResults.let {
                require(it != null) { "policyResults should be available after running policies" }
                assert(it.size > 1) { "no policies have run" }
            }
        }
    }

    private suspend fun testOID4VPSdJwtVc(
        addDisclosures: Boolean = false,
    ) {
        lateinit var presentationRequestURL: String
        lateinit var resolvedPresentationRequestURL: String
        lateinit var presentationDefinition: String
        lateinit var matchedCredentialList: List<WalletCredential>
        var response = client.get("/wallet-api/wallet/$walletId/credentials").expectSuccess()
        val walletCredentialList = response.body<List<WalletCredential>>()
        val createReqResponse = client.post("/openid4vc/verify") {
            header("authorizeBaseUrl", "openid4vp://")
            header("openId4VPProfile", OpenId4VPProfile.DEFAULT)
            header("responseMode", "direct_post")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put(
                    "request_credentials", JsonArray(
                        listOf(RequestedCredential(
                            format = VCFormat.sd_jwt_vc,
                            vct = "${getBaseURL()}/identity_credential",
                        ).let {
                            Json.encodeToJsonElement(it)
                        })
                    )
                )
            })
        }
        assertEquals(200, createReqResponse.status.value)
        presentationRequestURL = createReqResponse.bodyAsText()
        val verificationID = Url(presentationRequestURL).parameters.getOrFail("state")
        exchangeApi.resolvePresentationRequest(
            walletId, presentationRequestURL
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
        val prepareRequest = PrepareOID4VPRequest(
            did = holderDID,
            presentationRequest = presentationRequestURL,
            selectedCredentialIdList = matchedCredentialList.map { it.id },
            disclosures = if (addDisclosures) matchedCredentialList.filter { it.disclosures != null }.associate {
                Pair(it.id, listOf(it.disclosures!!))
            } else null,
        )
        println(prepareRequest)
        response = client.post("/wallet-api/wallet/$walletId/exchange/external_signatures/presentation/prepare") {
            setBody(prepareRequest)
        }.expectSuccess()
        val prepareResponse = response.body<PrepareOID4VPResponse>()
        client.post("/wallet-api/wallet/$walletId/exchange/external_signatures/presentation/submit") {
            setBody(SubmitOID4VPRequest.build(prepareResponse,
                disclosures = if (addDisclosures) matchedCredentialList.filter { it.disclosures != null }.associate {
                    Pair(it.id, listOf(it.disclosures!!))
                } else null,
                w3cJwtVpProof = prepareResponse.w3CJwtVpProofParameters?.let { params ->
                    holderKey.signJws(
                        params.payload.toJsonElement().toString().toByteArray(),
                        params.header,
                    )
                },
                ietfSdJwtVpProofs = prepareResponse.ietfSdJwtVpProofParameters?.map { params ->
                    IETFSdJwtVpTokenProof(
                        credentialId = params.credentialId, sdJwtVc = params.sdJwtVc, vpTokenProof = holderKey.signJws(
                            params.payload.toJsonElement().toString().toByteArray(),
                            params.header,
                        )
                    )
                })
            )
        }.expectSuccess()
        verifierSessionApi.get(verificationID) { sessionInfo ->
//            assert(sessionInfo.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null) { "Received no valid token response!" }
            assert(sessionInfo.tokenResponse?.presentationSubmission != null) { "should have a presentation submission after submission" }

            assert(sessionInfo.verificationResult == true) { "overall verification should be valid" }
            sessionInfo.policyResults.let {
                require(it != null) { "policyResults should be available after running policies" }
                assert(it.size > 1) { "no policies have run" }
            }
        }
    }

    private suspend fun testOID4VPW3CIETFSdJwtVc(
        addDisclosures: Boolean = false,
    ) {
        lateinit var presentationRequestURL: String
        lateinit var resolvedPresentationRequestURL: String
        lateinit var presentationDefinition: String
        lateinit var matchedCredentialList: List<WalletCredential>
        var response = client.get("/wallet-api/wallet/$walletId/credentials").expectSuccess()
        val walletCredentialList = response.body<List<WalletCredential>>()
        val createReqResponse = client.post("/openid4vc/verify") {
            header("authorizeBaseUrl", "openid4vp://")
            header("openId4VPProfile", OpenId4VPProfile.DEFAULT)
            header("responseMode", "direct_post")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put(
                    "request_credentials", JsonArray(
                        listOf(
                            RequestedCredential(
                                format = VCFormat.sd_jwt_vc,
                                vct = "${getBaseURL()}/identity_credential",
                            ).let {
                                Json.encodeToJsonElement(it)
                            },
                            RequestedCredential(
                                format = VCFormat.jwt_vc_json,
                                type = "OpenBadgeCredential",
                            ).let {
                                Json.encodeToJsonElement(it)
                            },
                        )
                    )
                )
            })
        }
        assertEquals(200, createReqResponse.status.value)
        presentationRequestURL = createReqResponse.bodyAsText()
        val verificationID = Url(presentationRequestURL).parameters.getOrFail("state")
        exchangeApi.resolvePresentationRequest(
            walletId, presentationRequestURL
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
        val prepareRequest = PrepareOID4VPRequest(
            did = holderDID,
            presentationRequest = presentationRequestURL,
            selectedCredentialIdList = matchedCredentialList.map { it.id },
            disclosures = if (addDisclosures) matchedCredentialList.filter { it.disclosures != null }.associate {
                Pair(it.id, listOf(it.disclosures!!))
            } else null,
        )
        println(prepareRequest)
        response = client.post("/wallet-api/wallet/$walletId/exchange/external_signatures/presentation/prepare") {
            setBody(prepareRequest)
        }.expectSuccess()
        val prepareResponse = response.body<PrepareOID4VPResponse>()
        client.post("/wallet-api/wallet/$walletId/exchange/external_signatures/presentation/submit") {
            setBody(SubmitOID4VPRequest.build(prepareResponse,
                disclosures = if (addDisclosures) matchedCredentialList.filter { it.disclosures != null }.associate {
                    Pair(it.id, listOf(it.disclosures!!))
                } else null,
                w3cJwtVpProof = prepareResponse.w3CJwtVpProofParameters?.let { params ->
                    holderKey.signJws(
                        params.payload.toJsonElement().toString().toByteArray(),
                        params.header,
                    )
                },
                ietfSdJwtVpProofs = prepareResponse.ietfSdJwtVpProofParameters?.map { params ->
                    IETFSdJwtVpTokenProof(
                        credentialId = params.credentialId, sdJwtVc = params.sdJwtVc, vpTokenProof = holderKey.signJws(
                            params.payload.toJsonElement().toString().toByteArray(),
                            params.header,
                        )
                    )
                })
            )
        }.expectSuccess()
        verifierSessionApi.get(verificationID) { sessionInfo ->
//            assert(sessionInfo.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null) { "Received no valid token response!" }
            assert(sessionInfo.tokenResponse?.presentationSubmission != null) { "should have a presentation submission after submission" }

            assert(sessionInfo.verificationResult == true) { "overall verification should be valid" }
            sessionInfo.policyResults.let {
                require(it != null) { "policyResults should be available after running policies" }
                assert(it.size > 1) { "no policies have run" }
            }
        }
    }
}
