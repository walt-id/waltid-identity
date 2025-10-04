package id.walt.entrawallet.core

import id.walt.entrawallet.core.service.exchange.CredentialDataResult
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class W3CTest : BaseTest() {

    suspend fun getIssuanceOffer() =
        http.post("https://issuer.demo.walt.id/openid4vc/jwt/issue") {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(
                "{\"issuerDid\":\"did:jwk:eyJrdHkiOiJFQyIsImNydiI6IlAtMjU2Iiwia2lkIjoiM1lOZDlGbng5Smx5UFZZd2dXRkUzN0UzR3dJMGVHbENLOHdGbFd4R2ZwTSIsIngiOiJGb3ZZMjFMQUFPVGxnLW0tTmVLV2haRUw1YUZyblIwdWNKakQ1VEtwR3VnIiwieSI6IkNyRkpmR1RkUDI5SkpjY3BRWHV5TU8zb2h0enJUcVB6QlBCSVRZajBvZ0EifQ\",\"issuerKey\":{\"type\":\"jwk\",\"jwk\":{\"kty\":\"EC\",\"d\":\"8jH4vwtvCw6tcBzdxQ6V7FY2L215lBGm-x3flgENx4Y\",\"crv\":\"P-256\",\"kid\":\"3YNd9Fnx9JlyPVYwgWFE37E3GwI0eGlCK8wFlWxGfpM\",\"x\":\"FovY21LAAOTlg-m-NeKWhZEL5aFrnR0ucJjD5TKpGug\",\"y\":\"CrFJfGTdP29JJccpQXuyMO3ohtzrTqPzBPBITYj0ogA\"}},\"credentialConfigurationId\":\"OpenBadgeCredential_jwt_vc_json\",\"credentialData\":{\"@context\":[\"https://www.w3.org/2018/credentials/v1\",\"https://purl.imsglobal.org/spec/ob/v3p0/context.json\"],\"id\":\"406c6556-4b66-44b4-b141-ba4262b8d673\",\"type\":[\"VerifiableCredential\",\"OpenBadgeCredential\"],\"name\":\"JFF x vc-edu PlugFest 3 Interoperability\",\"issuer\":{\"type\":[\"Profile\"],\"id\":\"did:example:123\",\"name\":\"Jobs for the Future (JFF)\",\"url\":\"https://www.jff.org/\",\"image\":\"https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png\"},\"issuanceDate\":\"2023-07-20T07:05:44Z\",\"expirationDate\":\"2033-07-20T07:05:44Z\",\"credentialSubject\":{\"id\":\"did:example:123\",\"type\":[\"AchievementSubject\"],\"achievement\":{\"id\":\"urn:uuid:ac254bd5-8fad-4bb1-9d29-efd938536926\",\"type\":[\"Achievement\"],\"name\":\"JFF x vc-edu PlugFest 3 Interoperability\",\"description\":\"This wallet supports the use of W3C Verifiable Credentials and has demonstrated interoperability during the presentation request workflow during JFF x VC-EDU PlugFest 3.\",\"criteria\":{\"type\":\"Criteria\",\"narrative\":\"Wallet solutions providers earned this badge by demonstrating interoperability during the presentation request workflow. This includes successfully receiving a presentation request, allowing the holder to select at least two types of verifiable credentials to create a verifiable presentation, returning the presentation to the requestor, and passing verification of the presentation and the included credentials.\"},\"image\":{\"id\":\"https://w3c-ccg.github.io/vc-ed/plugfest-3-2023/images/JFF-VC-EDU-PLUGFEST3-badge-image.png\",\"type\":\"Image\"}}}},\"mapping\":{\"id\":\"<uuid>\",\"issuer\":{\"id\":\"<issuerDid>\"},\"credentialSubject\":{\"id\":\"<subjectDid>\"},\"issuanceDate\":\"<timestamp>\",\"expirationDate\":\"<timestamp-in:365d>\"},\"authenticationMethod\":\"PRE_AUTHORIZED\"}"
            )
        }.bodyAsText()


    suspend fun getVerificationRequest() =
        http.post("https://verifier.demo.walt.id/openid4vc/verify") {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            header("successRedirectUri", "https://portal.walt.id/success/\$id")
            header("errorRedirectUri", "https://portal.walt.id/success/\$id")
            setBody("{\"request_credentials\":[{\"type\":\"OpenBadgeCredential\",\"format\":\"jwt_vc_json\"}],\"vc_policies\":[\"signature\",\"expired\",\"not-before\"]}")
        }.bodyAsText()


    suspend fun w3cFlowIssuance(): List<CredentialDataResult> {
        println("=> Making issuance offer...")
        val issuanceOffer = getIssuanceOffer()
        println("=> Issuance offer: $issuanceOffer")

        /* //BLOCKED: Waiting for openid changes
        println("=> Receiving credentials...")

        val receivedCredentials = CoreWallet.useOfferRequest(issuanceOffer, did = did, key = key)
        println("=> Received credentials: $receivedCredentials")
        check(receivedCredentials.isNotEmpty())

        return receivedCredentials

         */
        return emptyList()
    }

    @Test
    fun testW3CFlow() = runTest {
        w3cFlowIssuance()
        val verificationRequest = getVerificationRequest()
    }

}
