import id.walt.credentials.CredentialBuilder
import id.walt.credentials.CredentialBuilderType.W3CV2CredentialBuilder
import id.walt.crypto.utils.JsonUtils.toJsonObject
import kotlin.test.Test
import kotlin.time.Duration.Companion.days

class DataBuilderTest {

    val entityIdentificationNumber = "12345"
    val issuingAuthorityId = "abc"

    val proofType = "document"
    val proofLocation = "Berlin-Brandenburg"

    @Test
    fun testDataBuilder() {
        val myCustomData = mapOf(
            "entityIdentification" to entityIdentificationNumber,
            "issuingAuthority" to issuingAuthorityId,
            "issuingCircumstances" to mapOf(
                "proofType" to proofType,
                "locationType" to "physicalLocation",
                "location" to proofLocation
            )
        ).toJsonObject()

        // build a W3C V2.0 credential
        val vc = CredentialBuilder(W3CV2CredentialBuilder).apply {
            addContext("https://www.w3.org/ns/credentials/examples/v2") // [W3CV2 VC context, custom context]
            addType("MyCustomCredential") // [VerifiableCredential, MyCustomCredential]

                                          // credentialId = "123"
            randomCredentialSubjectUUID() // automatically generate
            issuerDid = "did:key:abc"     // possibly later overridden by data mapping during issuance
            subjectDid = "did:key:xyz"    // possibly later overridden by data mapping during issuance

            validFromNow()                // set validFrom per current time
            validFor(90.days)             // set expiration date to now + 3 months

            useStatusList2021Revocation("https://university.example/credentials/status/3", 94567)

            useCredentialSubject(myCustomData)
            // "custom" set myCustomData
        }.buildW3C()

        print(vc.toPrettyJson())
    }
}
