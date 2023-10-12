import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.TSEKey
import id.walt.crypto.keys.TSEKeyMetadata
import id.walt.did.dids.DidService
import id.walt.did.helpers.WaltidServices
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.test.Test

class VcApiTest {

    @Test
    @EnabledIf("hostCondition")
    fun testVcApi() = runTest {
        // Initialize services
        WaltidServices.init()

        val tseMetadata = TSEKeyMetadata("http://127.0.0.1:8200/v1/transit", "dev-only-token")

        // Generate key & derive DID from key
        val key = TSEKey.generate(KeyType.Ed25519, tseMetadata)
        //val key = LocalKey.generate(KeyType.Ed25519)
        val did = DidService.registerByKey("jwk", key).did

        // Syntax-sugar to create VC
        val vc = W3CVC.build(
            context = listOf("https://example.org"),
            type = listOf("VerifiableCredential", "VerifiableId"),

            "id" to "urn:uuid:4177e048-9a4a-474e-9dc6-aed4e61a6439",
            "issuer" to did,
            "issuanceDate" to "2023-08-02T08:03:13Z",
            "issued" to "2023-08-02T08:03:13Z",
            "validFrom" to "2023-08-02T08:03:13Z",
            "credentialSchema" to mapOf(
                "id" to "https://raw.githubusercontent.com/walt-id/waltid-ssikit-vclib/master/src/test/resources/schemas/VerifiableId.json",
                "type" to "FullJsonSchemaValidator2021"
            ),
            "credentialSubject" to mapOf(
                "id" to did,
                "currentAddress" to listOf("1 Boulevard de la Liberté, 59800 Lille"),
                "dateOfBirth" to "1993-04-08",
                "familyName" to "DOE",
                "firstName" to "Jane",
                "gender" to "FEMALE",
                "nameAndFamilyNameAtBirth" to "Jane DOE",
                "personalIdentifier" to "0904008084H",
                "placeOfBirth" to "LILLE, FRANCE"
            ),
            "evidence" to listOf(
                mapOf(
                    "documentPresence" to listOf("Physical"),
                    "evidenceDocument" to listOf("Passport"),
                    "subjectPresence" to "Physical",
                    "type" to listOf("DocumentVerification"),
                    "verifier" to "did:ebsi:2A9BZ9SUe6BatacSpvs1V5CdjHvLpQ7bEsi2Jb6LdHKnQxaN"
                )
            )
        )

        println(vc.toPrettyJson())

        // Sign VC
        val jws = vc.signJws(
            issuerKey = key,
            issuerDid = did,
            subjectDid = did
        )
        println(jws)

        key.delete()

    }

    private fun hostCondition() = runCatching {
        runBlocking { HttpClient().get("http://127.0.0.1:8200") }.status == HttpStatusCode.OK
    }.fold(onSuccess = { true }, onFailure = { false })

}
