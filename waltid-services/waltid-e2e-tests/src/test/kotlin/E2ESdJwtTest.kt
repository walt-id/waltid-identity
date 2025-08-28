import WaltidServicesE2ETests.Companion.nameFieldSchemaPresentationRequestPayload
import WaltidServicesE2ETests.Companion.sdjwtW3CCredential
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.util.JwtUtils
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class E2ESdJwtTest(
    private val issuerApi: IssuerApi,
    private val exchangeApi: ExchangeApi,
    private val sessionApi: Verifier.SessionApi,
    private val verificationApi: Verifier.VerificationApi,
) {

    fun testW3CVC(wallet: Uuid, did: String) = runTest {
        //region -Issuer / offer url-
        lateinit var credentialOfferUrl: String
        val issuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(sdjwtW3CCredential)
        println("issuance-request:")
        println(issuanceRequest)
        issuerApi.sdjwt(issuanceRequest) {
            credentialOfferUrl = it
            println("offer: $credentialOfferUrl")
        }
        //endregion -Issuer / offer url-

        //region -Exchange / claim-
        lateinit var newCredential: WalletCredential
        exchangeApi.resolveCredentialOffer(wallet, credentialOfferUrl)
        exchangeApi.useOfferRequest(wallet, credentialOfferUrl, 1) {
            newCredential = it.first()
        }
        assertContains(JwtUtils.parseJWTPayload(newCredential.document).keys, JwsSignatureScheme.JwsOption.VC)
        //endregion -Exchange / claim-

        //region -Verifier / request url-
        lateinit var verificationUrl: String
        lateinit var verificationId: String
        verificationApi.verify(nameFieldSchemaPresentationRequestPayload) {
            verificationUrl = it
            verificationId = Url(verificationUrl).parameters.getOrFail("state")
        }
        //endregion -Verifier / request url-

        //region -Exchange / presentation-
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

        sessionApi.get(verificationId) {
            assertTrue(it.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null, "Received no valid token response!" )
            assertTrue(it.tokenResponse?.presentationSubmission != null, "should have a presentation submission after submission" )

            assertTrue(it.verificationResult == false,  "overall verification should be valid" )
            it.policyResults.let {
                require(it != null) { "policyResults should be available after running policies" }
                assertTrue(it.size > 1, "no policies have run" )
            }
        }
        //endregion -Exchange / presentation-
    }

    fun testIEFTSDJWTVC(wallet: Uuid, did: String) = runTest {
        //region - Issuer / offer url-
        lateinit var credentialOfferUrl: String
        val issuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(sdjwtIETFCredential)

        issuerApi.sdjwt(issuanceRequest) {
            credentialOfferUrl = it
        }

        //endregion - Issuer / offer url-

        //region - Exchange / claim-
        lateinit var newCredential: WalletCredential
        exchangeApi.resolveCredentialOffer(wallet, credentialOfferUrl)
        exchangeApi.useOfferRequest(wallet, credentialOfferUrl, 1) {
            newCredential = it.first()
        }

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
    }
}
