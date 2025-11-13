import WaltidServicesE2ETests.Companion.ieftSdjwtPresentationRequestPayload
import WaltidServicesE2ETests.Companion.nameFieldSchemaPresentationRequestPayload
import WaltidServicesE2ETests.Companion.sdjwtIETFCredential
import WaltidServicesE2ETests.Companion.sdjwtIETFCredentialWithoutDisclosures
import WaltidServicesE2ETests.Companion.sdjwtW3CCredential
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.resolver.local.DidKeyResolver
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.util.JwtUtils
import id.walt.oid4vc.util.http
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.metadata.type.SdJwtVcTypeMetadataDraft04
import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class E2ESdJwtTest(
    private val issuerApi: IssuerApi,
    private val exchangeApi: ExchangeApi,
    private val sessionApi: Verifier.SessionApi,
    private val verificationApi: Verifier.VerificationApi,
    private val credentialsApi: CredentialsApi,
) {

    fun testW3CVC(wallet: Uuid, did: String) = runTest {
        lateinit var newCredential: WalletCredential

        val issuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(sdjwtW3CCredential)

        newCredential = executePreAuthorizedFlow(wallet, issuanceRequest)

        assertContains(JwtUtils.parseJWTPayload(newCredential.document).keys, JwsSignatureScheme.JwsOption.VC)

        val verificationId = executePresentation(
            wallet = wallet,
            presentationRequest = nameFieldSchemaPresentationRequestPayload,
            newCredential = newCredential,
            did = did
        )

        sessionApi.get(verificationId) { it ->
            assertTrue(
                it.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null,
                "Received no valid token response!"
            )
            assertTrue(
                it.tokenResponse?.presentationSubmission != null,
                "should have a presentation submission after submission"
            )

            assertTrue(it.verificationResult == true, "overall verification should be valid")
            it.policyResults.let {
                require(it != null) { "policyResults should be available after running policies" }
                assertTrue(it.size > 1, "no policies have run")
            }
        }
        //endregion -Exchange / presentation-

        //delete credential
        credentialsApi.delete(wallet, newCredential.id)
    }

    fun testIEFTSDJWTVC(wallet: Uuid, did: String) = runTest {
        lateinit var newCredential: WalletCredential

        val issuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(sdjwtIETFCredential)

        newCredential = executePreAuthorizedFlow(wallet, issuanceRequest)

        // assert sd alg in raw object
        assertContains(newCredential.parsedDocument!!.keys, "_sd_alg")
        assertEquals("sha-256", newCredential.parsedDocument!!["_sd_alg"]!!.jsonPrimitive.content)

        // assert SDJwtVC token
        val credential = SDJwtVC.parse(newCredential.document)

        // assert sd alg in SDJwtVC
        assertNotNull(credential.sdAlg)
        assertEquals("sha-256", credential.sdAlg)

        // check SD-JWT-VC type metadata
        assertNotNull(credential.vct)
        val vctUrl = Url(credential.vct!!)
        val typeMetadataUrl =
            "${vctUrl.protocolWithAuthority}/.well-known/vct/${vctUrl.fullPath.substringAfter("/")}"

        val typeMetadata = http.get(typeMetadataUrl).expectSuccess().body<SdJwtVcTypeMetadataDraft04>()
        assertEquals(credential.vct, typeMetadata.vct)

        // check SD-JWT-VC issuer metadata
        assertNotNull(credential.issuer)
        assertEquals(issuanceRequest.issuerDid, credential.issuer)

        // check issuer key and signature
        val resolvedIssuerDid = DidKeyResolver().resolve(credential.issuer!!)
        assert(resolvedIssuerDid.isSuccess)
        // TODO: this works, but check if there's a more elegant way to find the right key for verification!
        val keyJwk = (resolvedIssuerDid.getOrNull()!!["verificationMethod"] as JsonArray).first {
            it.jsonObject["id"]!!.jsonPrimitive.content == credential.keyID!!
        }.jsonObject["publicKeyJwk"]!!.jsonObject

        val issuerJwk = JWKKey.importJWK(keyJwk.toString()).getOrNull()

        assertNotNull(issuerJwk)
        val verifyResult = issuerJwk.verifyJws(credential.jwt)
        assert(verifyResult.isSuccess)

        val verificationId = executePresentation(
            wallet = wallet,
            presentationRequest = ieftSdjwtPresentationRequestPayload,
            newCredential = newCredential,
            did = did
        )

        sessionApi.get(verificationId) { it ->
            assertTrue(
                it.tokenResponse?.presentationSubmission != null,
                "should have a presentation submission after submission"
            )

            assertEquals(it.verificationResult, true, "overall verification should be valid")
            it.policyResults.let {
                require(it != null) { "policyResults should be available after running policies" }
                assertTrue(it.size > 1, "no policies have run")
            }
        }
        //endregion -Exchange / presentation-

        //delete credential
        credentialsApi.delete(wallet, newCredential.id)
    }

    fun testIEFTSDJWTVCWithoutDisclosures(wallet: Uuid, did: String) = runTest {

        credentialsApi.list(wallet, expectedSize = 0)

        lateinit var newCredential: WalletCredential

        val issuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(sdjwtIETFCredentialWithoutDisclosures)

        newCredential = executePreAuthorizedFlow(wallet, issuanceRequest)

        credentialsApi.list(wallet, expectedSize = 1, expectedCredential = arrayOf(newCredential.id))

        // assert sd alg in raw object
        assertContains(newCredential.parsedDocument!!.keys, "_sd_alg")
        assertEquals("sha-256", newCredential.parsedDocument!!["_sd_alg"]!!.jsonPrimitive.content)

        // assert SDJwtVC token
        val credential = SDJwtVC.parse(newCredential.document)

        // assert sd alg in SDJwtVC
        assertNotNull(credential.sdAlg)
        assertEquals("sha-256", credential.sdAlg)

        // check SD-JWT-VC type metadata
        assertNotNull(credential.vct)
        val vctUrl = Url(credential.vct!!)
        val typeMetadataUrl =
            "${vctUrl.protocolWithAuthority}/.well-known/vct/${vctUrl.fullPath.substringAfter("/")}"

        val typeMetadata = http.get(typeMetadataUrl).expectSuccess().body<SdJwtVcTypeMetadataDraft04>()

        assertEquals(credential.vct, typeMetadata.vct)

        // check SD-JWT-VC issuer metadata
        assertNotNull(credential.issuer)
        assertEquals(issuanceRequest.issuerDid, credential.issuer)

        // check issuer key and signature
        val resolvedIssuerDid = DidKeyResolver().resolve(credential.issuer!!)
        assert(resolvedIssuerDid.isSuccess)
        // TODO: this works, but check if there's a more elegant way to find the right key for verification!
        val keyJwk = (resolvedIssuerDid.getOrNull()!!["verificationMethod"] as JsonArray).first {
            it.jsonObject["id"]!!.jsonPrimitive.content == credential.keyID!!
        }.jsonObject["publicKeyJwk"]!!.jsonObject

        val issuerJwk = JWKKey.importJWK(keyJwk.toString()).getOrNull()

        assertNotNull(issuerJwk)
        val verifyResult = issuerJwk.verifyJws(credential.jwt)
        assert(verifyResult.isSuccess)

        val verificationId = executePresentation(
            wallet = wallet,
            presentationRequest = ieftSdjwtPresentationRequestPayload,
            newCredential = newCredential,
            did = did
        )

        sessionApi.get(verificationId) { it ->
            assertTrue(
                it.tokenResponse?.presentationSubmission != null,
                "should have a presentation submission after submission"
            )

            assertTrue(it.verificationResult == true, "overall verification should be valid")
            it.policyResults.let {
                require(it != null) { "policyResults should be available after running policies" }
                assertTrue(it.size > 1, "no policies have run")
            }
        }
        //endregion -Exchange / presentation-

        //delete credential
        credentialsApi.delete(wallet, newCredential.id)
    }

    private suspend fun executePreAuthorizedFlow(wallet: Uuid, issuanceRequest: IssuanceRequest): WalletCredential {
        lateinit var credentialOfferUrl: String
        lateinit var newCredential: WalletCredential

        issuerApi.sdjwt(issuanceRequest) {
            credentialOfferUrl = it
            println("offer: $credentialOfferUrl")
        }

        exchangeApi.resolveCredentialOffer(wallet, credentialOfferUrl)
        exchangeApi.useOfferRequest(wallet, credentialOfferUrl, 1) {
            newCredential = it.first()
        }

        return newCredential
    }

    private suspend fun executePresentation(
        wallet: Uuid,
        presentationRequest: String,
        newCredential: WalletCredential,
        did: String,
    ): String {
        lateinit var verificationUrl: String
        lateinit var verificationId: String
        verificationApi.verify(presentationRequest) {
            verificationUrl = it
            verificationId = Url(verificationUrl).parameters.getOrFail("state")
        }

        //region - Exchange / presentation -
        lateinit var resolvedPresentationOfferString: String
        lateinit var presentationDefinition: String
        exchangeApi.resolvePresentationRequest(wallet, verificationUrl) {
            resolvedPresentationOfferString = it
            presentationDefinition = Url(it).parameters.getOrFail("presentation_definition")
        }

        sessionApi.get(verificationId) {
            assertTrue(it.presentationDefinition == PresentationDefinition.fromJSONString(presentationDefinition))
        }

        exchangeApi.matchCredentialsForPresentationDefinition(
            wallet, presentationDefinition, listOf(newCredential.id)
        )
        exchangeApi.unmatchedCredentialsForPresentationDefinition(wallet, presentationDefinition)
        exchangeApi.usePresentationRequest(
            wallet = wallet,
            request = UsePresentationRequest(
                did = did,
                presentationRequest = resolvedPresentationOfferString,
                selectedCredentials = listOf(newCredential.id),
                disclosures = newCredential.disclosures?.let { mapOf(newCredential.id to listOf(it)) },
            ),
            expectStatus = expectSuccess,
        )

        return verificationId
    }

}
