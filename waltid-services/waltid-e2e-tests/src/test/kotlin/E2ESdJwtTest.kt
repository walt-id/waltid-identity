import WaltidServicesE2ETests.Companion.nameFieldSchemaPresentationRequestPayload
import WaltidServicesE2ETests.Companion.sdjwtCredential
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import io.ktor.http.*
import io.ktor.server.util.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class E2ESdJwtTest(
    private val issuerApi: IssuerApi,
    private val exchangeApi: ExchangeApi,
    private val sessionApi: Verifier.SessionApi,
    private val verificationApi: Verifier.VerificationApi,
) {

    fun e2e(wallet: Uuid, did: String) = runTest {
        //region -Issuer / offer url-
        lateinit var offerUrl: String
        val issuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(sdjwtCredential)
        println("issuance-request:")
        println(issuanceRequest)
        issuerApi.sdjwt(issuanceRequest) {
            offerUrl = it
            println("offer: $offerUrl")
        }
        //endregion -Issuer / offer url-

        //region -Exchange / claim-
        lateinit var newCredential: WalletCredential
        exchangeApi.resolveCredentialOffer(wallet, offerUrl)
        exchangeApi.useOfferRequest(wallet, offerUrl, 1) {
            newCredential = it.first()
        }
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
            assert(it.presentationDefinition == PresentationDefinition.fromJSONString(presentationDefinition))
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
            assert(it.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null) { "Received no valid token response!" }
            assert(it.tokenResponse?.presentationSubmission != null) { "should have a presentation submission after submission" }

            assert(it.verificationResult == false) { "overall verification should be valid" }
            it.policyResults.let {
                require(it != null) { "policyResults should be available after running policies" }
                assert(it.size > 1) { "no policies have run" }
            }
        }
        //endregion -Exchange / presentation-
    }
}
