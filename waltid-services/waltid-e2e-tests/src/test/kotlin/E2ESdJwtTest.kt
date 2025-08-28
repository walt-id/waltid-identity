import WaltidServicesE2ETests.Companion.nameFieldSchemaPresentationRequestPayload
import WaltidServicesE2ETests.Companion.sdjwtIETFCredential
import WaltidServicesE2ETests.Companion.sdjwtW3CCredential
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.resolver.local.DidKeyResolver
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.util.JwtUtils
import id.walt.sdjwt.SDJwtVC
import id.walt.w3c.schemes.JwsSignatureScheme
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertTrue
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import id.walt.oid4vc.util.http
import id.walt.sdjwt.SDJWTVCTypeMetadata
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject

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

        sessionApi.get(verificationId) {
            assertTrue(
                it.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null,
                "Received no valid token response!"
            )
            assertTrue(
                it.tokenResponse?.presentationSubmission != null,
                "should have a presentation submission after submission"
            )

            assertTrue(it.verificationResult == false, "overall verification should be valid")
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

////         assert parsedDocument
////        assertContains(newCredential.parsedDocument!!.keys, "_sd_alg")
////        assertEquals("sha-256", newCredential.parsedDocument!!["_sd_alg"]!!.jsonPrimitive.content)

        // assert SDJwtVC token
        val credential = SDJwtVC.parse(newCredential.document)

        // check SD-JWT-VC type metadata
        assertNotNull(credential.vct)
        val vctUrl = Url(credential.vct!!)
        val typeMetadataUrl =
            "${vctUrl.protocolWithAuthority}/.well-known/vct/${vctUrl.fullPath.substringAfter("/")}"

        val typeMetadata = http.get(typeMetadataUrl).expectSuccess().body<SDJWTVCTypeMetadata>()
        assertEquals(credential.vct, typeMetadata.vct)

        // check SD-JWT-VC issuer metadata
        assertNotNull(credential.issuer)
        assertEquals(issuanceRequest.issuerDid, credential.issuer)

        // check issuer key and signature
        val resolvedIssuerDid = DidKeyResolver().resolve(credential.issuer!!)
        assert(resolvedIssuerDid.isSuccess)
        // TODO: this works, but check if there's a more elegant way to find the right key for verification!
        val keyJwk = (resolvedIssuerDid.getOrNull()!!.get("verificationMethod") as JsonArray).first {
            it.jsonObject["id"]!!.jsonPrimitive.content == credential.keyID!!
        }.jsonObject["publicKeyJwk"]!!.jsonObject

        val issuerJwk = JWKKey.importJWK(keyJwk.toString()).getOrNull()

        assertNotNull(issuerJwk)
        val verifyResult = issuerJwk.verifyJws(credential.jwt)
        assert(verifyResult.isSuccess)

////        //endregion - Exchange / claim-
//////
//////        //region - Verifier / request url-
//////        lateinit var verificationUrl: String
//////        lateinit var verificationId: String
//////        verificationApi.verify(nameFieldSchemaPresentationRequestPayload) {
//////            verificationUrl = it
//////            verificationId = Url(verificationUrl).parameters.getOrFail("state")
//////        }
//////        //endregion -Verifier / request url-
//////
//////        //region - Exchange / presentation-
//////        lateinit var resolvedPresentationOfferString: String
//////        lateinit var presentationDefinition: String
//////        exchangeApi.resolvePresentationRequest(wallet, verificationUrl) {
//////            resolvedPresentationOfferString = it
//////            presentationDefinition = Url(it).parameters.getOrFail("presentation_definition")
//////        }
//////
//////        sessionApi.get(verificationId) {
//////            assertTrue(it.presentationDefinition == PresentationDefinition.fromJSONString(presentationDefinition))
//////        }
//////
//////        exchangeApi.matchCredentialsForPresentationDefinition(
//////            wallet, presentationDefinition, listOf(newCredential.id)
//////        )
//////        exchangeApi.unmatchedCredentialsForPresentationDefinition(wallet, presentationDefinition)
//////        exchangeApi.usePresentationRequest(
//////            wallet = wallet,
//////            request = UsePresentationRequest(
//////                did = did,
//////                presentationRequest = resolvedPresentationOfferString,
//////                selectedCredentials = listOf(newCredential.id),
//////                disclosures = newCredential.disclosures?.let { mapOf(newCredential.id to listOf(it)) },
//////            ),
//////            expectStatus = expectFailure,
//////        )
//////
//////        sessionApi.get(verificationId) {
//////            assertTrue(it.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null) { "Received no valid token response!" }
//////            assertTrue(it.tokenResponse?.presentationSubmission != null) { "should have a presentation submission after submission" }
//////
//////            assertTrue(it.verificationResult == false) { "overall verification should be valid" }
//////            it.policyResults.let {
//////                require(it != null) { "policyResults should be available after running policies" }
//////                assertTrue(it.size > 1) { "no policies have run" }
//////            }
//////        }
//////        //endregion - Exchange / presentation-
////        credentialsApi.delete(wallet, newCredential.id)

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
            expectStatus = expectFailure,
        )

        return verificationId
    }

}
