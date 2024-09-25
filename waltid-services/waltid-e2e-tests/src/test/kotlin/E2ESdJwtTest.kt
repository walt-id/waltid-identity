import E2ETest.Companion.nameFieldSchemaPresentationRequestPayload
import E2ETest.Companion.sdjwtCredential
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

class E2ESdJwtTest(
    private val issuerApi: IssuerApi,
    private val exchangeApi: ExchangeApi,
    private val sessionApi: Verifier.SessionApi,
    private val verificationApi: Verifier.VerificationApi,
) {

    fun e2e(did: String) = runTest {
        //region -Issuer / offer url-
        lateinit var offerUrl: String
        val issuanceRequest = Json.decodeFromJsonElement<IssuanceRequest>(sdjwtCredential)
        println("issuance-request:")
        println(issuanceRequest)
        offerUrl = issuerApi.sdjwt(issuanceRequest)
        exchangeApi.resolveCredentialOffer(offerUrl)
        //endregion -Issuer / offer url-

        //region -Exchange / claim-
        val newCredential: WalletCredential = exchangeApi.useOfferRequest(offerUrl, 1).first()
        lateinit var verificationId: String
        //endregion -Exchange / claim-

        //region -Verifier / request url-
        val verificationUrl: String = verificationApi.verify(nameFieldSchemaPresentationRequestPayload)
        verificationId = Url(verificationUrl).parameters.getOrFail("state")
        lateinit var presentationDefinition: String
        //endregion -Verifier / request url-

        //region -Exchange / presentation-
        val resolvedPresentationOfferString: String = exchangeApi.resolvePresentationRequest(verificationUrl)
        presentationDefinition = Url(resolvedPresentationOfferString).parameters.getOrFail("presentation_definition")

        var presentationSession = sessionApi.get(verificationId)
        assert(
            presentationSession.presentationDefinition == PresentationDefinition.fromJSONString(
                presentationDefinition
            )
        )

        exchangeApi.matchCredentialsForPresentationDefinition(
            presentationDefinition, listOf(newCredential.id)
        )
        exchangeApi.unmatchedCredentialsForPresentationDefinition(presentationDefinition)
        exchangeApi.usePresentationRequest(
            request = UsePresentationRequest(
                did = did,
                presentationRequest = resolvedPresentationOfferString,
                selectedCredentials = listOf(newCredential.id),
                disclosures = newCredential.disclosures?.let { mapOf(newCredential.id to listOf(it)) },
            )
        )

        presentationSession = sessionApi.get(verificationId)
        assert(presentationSession.tokenResponse?.vpToken?.jsonPrimitive?.contentOrNull?.expectLooksLikeJwt() != null) { "Received no valid token response!" }
        assert(presentationSession.tokenResponse?.presentationSubmission != null) { "should have a presentation submission after submission" }

        assert(presentationSession.verificationResult == true) { "overall verification should be valid" }
        presentationSession.policyResults.let {
            require(it != null) { "policyResults should be available after running policies" }
            assert(it.isNotEmpty()) { "no policies have run" }
        }
        //endregion -Exchange / presentation-
    }
}