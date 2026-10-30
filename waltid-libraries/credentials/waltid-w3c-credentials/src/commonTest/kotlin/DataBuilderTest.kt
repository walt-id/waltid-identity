import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.w3c.CredentialBuilder
import id.walt.w3c.CredentialBuilderType.W3CV2CredentialBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
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

        assertEquals(
            listOf("https://www.w3.org/ns/credentials/v2", "https://www.w3.org/ns/credentials/examples/v2"),
            vc.getValue("@context").jsonArray.map { it.jsonPrimitive.content }
        )
        assertEquals(listOf("VerifiableCredential", "MyCustomCredential"), vc.getType())
        assertEquals(JsonPrimitive("did:key:abc"), vc["issuer"])
        assertEquals(Json.parseToJsonElement("""
            {
              "id": "did:key:xyz",
              "entityIdentification": "12345",
              "issuingAuthority": "abc",
              "issuingCircumstances": {
                "proofType": "document",
                "locationType": "physicalLocation",
                "location": "Berlin-Brandenburg"
              }
            }
        """), vc["credentialSubject"])
        assertEquals(Json.parseToJsonElement("""
            {
              "id": "https://university.example/credentials/status/3#94567",
              "type": "StatusList2021Entry",
              "statusPurpose": "revocation",
              "statusListIndex": "94567",
              "statusListCredential": "https://university.example/credentials/status/3"
            }
        """), vc["credentialStatus"])
    }
}
