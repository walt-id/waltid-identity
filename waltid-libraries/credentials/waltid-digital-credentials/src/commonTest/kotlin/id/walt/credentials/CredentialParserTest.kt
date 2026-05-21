package id.walt.credentials

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertNotNull

class CredentialParserTest {
    @Test
    fun testBrokenCredentialParsing() = runTest {
        val json = """
{
  "credentialSubject": {
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "type": ["VerifiableCredential", "OpenBadgeCredential"],
    "name": "VCDM 1.1 vs 2.0 comparison badge"
  },
  "id": "urn:uuid:be6e624c-0de6-47eb-8158-d232be2a6acb"
}
"""
        // This should reproduce the error if the structure is indeed the issue
        // We call detectAndParse directly
        val (detection, credential) = CredentialParser.detectAndParse(json)
        assertNotNull(credential)
    }
}
