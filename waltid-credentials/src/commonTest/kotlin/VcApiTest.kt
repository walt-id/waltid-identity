import id.walt.core.crypto.keys.KeyType
import id.walt.core.crypto.keys.LocalKey
import id.walt.core.crypto.keys.TSEKey
import id.walt.ssikit.did.DidService
import id.walt.ssikit.helpers.WaltidServices
import id.walt.ssikit.vc.list.W3CVC
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class VcApiTest {

    @Test
    fun testVcApi() = runTest {
        // Initialize services
        WaltidServices.init()

        // Generate key & derive DID from key
        val key = TSEKey.generate(KeyType.Ed25519)
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


    }

}
