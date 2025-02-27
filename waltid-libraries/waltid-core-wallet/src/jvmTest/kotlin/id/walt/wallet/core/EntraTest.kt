package id.walt.wallet.core

import id.walt.wallet.core.service.exchange.CredentialDataResult
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class EntraTest : BaseTest() {

    suspend fun entraFlowIssuance(): List<CredentialDataResult> {
        println("=> Making issuance offer...")
        val issuanceOffer = getIssuanceOffer()
        println("=> Issuance offer: $issuanceOffer")

        println("=> Receiving credentials...")
        val receivedCredentials = CoreWallet.useOfferRequest(issuanceOffer, did = did, key = key)
        println("=> Received credentials: $receivedCredentials")
        check(receivedCredentials.isNotEmpty())

        return receivedCredentials
    }

    suspend fun entraFlowVerification(receivedCredentials: List<CredentialDataResult>) {
        println("=> Making verification offer...")
        val (verificationOffer, verificationNonce) = getVerificationOffer()
        println("=> Verification offer: $verificationOffer")

        val verificationCheckUrl = "https://entra-demo.walt.id/entra/status/$verificationNonce"

        println("=> Presenting credentials...")
        val presentationResponse = CoreWallet.usePresentationRequest(
            presentationRequest = verificationOffer,
            did = did,
            key = key,
            selectedCredentials = receivedCredentials,
            disclosures = null
        )
        println("=> Presentation response: $presentationResponse")
        check(presentationResponse.ok)
        check(presentationResponse.errorMessage == null)

        val verification = checkVerification(verificationCheckUrl)
        println("Verification: $verification")
    }

    @Test // FIXME: Entra Issuer deployment at https://entra-demo.walt.id/entra/issue is unavailable
    fun testEntraFlow() = runTest(timeout = 3.minutes) {
        val receivedCredentials = entraFlowIssuance()
        entraFlowVerification(receivedCredentials)
    }

    suspend fun getIssuanceOffer() =
        http.post("https://entra-demo.walt.id/entra/issue") {
            setBody("{\"data\":{\"authority\":\"did:web:verifiedid.entra.microsoft.com:a8671fa1-780f-4af1-8341-cd431da2c46d:356de688-3752-d83c-6225-9ae1005e2aeb\",\"claims\":{\"given_name\":\"Max\",\"family_name\":\"Mustermann\"},\"manifest\":\"https://verifiedid.did.msidentity.com/v1.0/tenants/a8671fa1-780f-4af1-8341-cd431da2c46d/verifiableCredentials/contracts/a1ef334a-a5a4-ab0c-47bb-29b7d84f6c9b/manifest\",\"type\":\"VerifiableCredential,MyID\"}}")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.bodyAsText()

    suspend fun getVerificationOffer(): Pair<String, String> {
        val res = http.post("https://entra-demo.walt.id/entra/verify") {
            setBody("{\"data\":{\"vc_policies\":[\"expired\",\"not-before\",{\"policy\":\"allowed-issuer\",\"args\":\"did:web:verifiedid.entra.microsoft.com:a8671fa1-780f-4af1-8341-cd431da2c46d:356de688-3752-d83c-6225-9ae1005e2aeb\"}]},\"entraVerification\":{\"authority\":\"did:web:verifiedid.entra.microsoft.com:a8671fa1-780f-4af1-8341-cd431da2c46d:356de688-3752-d83c-6225-9ae1005e2aeb\",\"credentials\":[{\"purpose\":\"TEST\",\"type\":\"MyID\"}]}}")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body<JsonObject>()

        return res["url"]!!.jsonPrimitive.content to res["nonce"]!!.jsonPrimitive.content
    }

    suspend fun checkVerification(verificationCheckUrl: String): List<JsonObject> = http.get(verificationCheckUrl).body<List<JsonObject>>()
        .also { check(it.isNotEmpty()) }

}
