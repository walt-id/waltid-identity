import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.did.helpers.WaltidServices
import id.walt.wallet.core.CoreWallet
import id.walt.wallet.core.service.exchange.CredentialDataResult
import id.walt.wallet.core.service.exchange.UsePresentationResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

class EntraTest {
    private val http = HttpClient() {
        install(ContentNegotiation) {
            json()
        }
    }

    private lateinit var key: Key
    private lateinit var did: String
    private lateinit var issuanceOffer: String
    private lateinit var verificationOffer: String
    private lateinit var verificationNonce: String
    private val verificationCheckUrl: String
        get() = "https://entra-demo.walt.id/entra/status/$verificationNonce"

    @Test
    fun testEntraFlow() = runTest {
        println("=> Init...")
        WaltidServices.minimalInit()
        key = JWKKey.generate(KeyType.secp256r1)
        did = DidService.registerByKey("jwk", key).did

        getIssuanceOffer()
        getVerificationOffer()


        println("=> Receiving credentials...")
        val receivedCredentials = entraReceive()
        println("=> Received credentials: $receivedCredentials")

        println("=> Presenting credentials...")
        val presentationResponse = entraPresent(receivedCredentials)
        println("=> Presentation response: $presentationResponse")
    }

    suspend fun getIssuanceOffer() {
        println("=> Making issuance offer...")
        issuanceOffer = http.post("https://entra-demo.walt.id/entra/issue") {
            setBody("{\"data\":{\"authority\":\"did:web:verifiedid.entra.microsoft.com:a8671fa1-780f-4af1-8341-cd431da2c46d:356de688-3752-d83c-6225-9ae1005e2aeb\",\"claims\":{\"given_name\":\"Max\",\"family_name\":\"Mustermann\"},\"manifest\":\"https://verifiedid.did.msidentity.com/v1.0/tenants/a8671fa1-780f-4af1-8341-cd431da2c46d/verifiableCredentials/contracts/a1ef334a-a5a4-ab0c-47bb-29b7d84f6c9b/manifest\",\"type\":\"VerifiableCredential,MyID\"}}")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.bodyAsText()
        println("=> Issuance offer: $issuanceOffer")
    }

    suspend fun getVerificationOffer() {
        println("=> Making verification offer...")
        val res = http.post("https://entra-demo.walt.id/entra/verify") {
            setBody("{\"data\":{\"vc_policies\":[\"expired\",\"not-before\",{\"policy\":\"allowed-issuer\",\"args\":\"did:web:verifiedid.entra.microsoft.com:a8671fa1-780f-4af1-8341-cd431da2c46d:356de688-3752-d83c-6225-9ae1005e2aeb\"}]},\"entraVerification\":{\"authority\":\"did:web:verifiedid.entra.microsoft.com:a8671fa1-780f-4af1-8341-cd431da2c46d:356de688-3752-d83c-6225-9ae1005e2aeb\",\"credentials\":[{\"purpose\":\"TEST\",\"type\":\"MyID\"}]}}")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body<JsonObject>()

        verificationOffer = res["url"]!!.jsonPrimitive.content
        println("=> Verification offer: $verificationOffer")
        verificationNonce = res["nonce"]!!.jsonPrimitive.content
    }

    suspend fun checkVerification() {
        http.get(verificationCheckUrl)
    }

    suspend fun entraReceive(): List<CredentialDataResult> {
        return CoreWallet.useOfferRequest(issuanceOffer, did = did, key = key)
    }

    suspend fun entraPresent(credentials: List<CredentialDataResult>): UsePresentationResponse {
        return CoreWallet.usePresentationRequest(
            presentationRequest = verificationOffer,
            did = did,
            key = key,
            selectedCredentials = credentials,
            disclosures = null
        )
    }
}
