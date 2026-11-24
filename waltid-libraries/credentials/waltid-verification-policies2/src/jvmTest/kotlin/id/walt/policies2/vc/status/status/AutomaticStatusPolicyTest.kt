package id.walt.policies2.vc.status.status

import id.walt.credentials.CredentialParser
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.VerificationPolicyManager
import id.walt.policies2.vc.policies.StatusPolicy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutomaticStatusPolicyTest {
    @Test
    fun `simple policy and argument defaults survive serialization`() = runTest {
        for (encoded in listOf(
            """["signature","credential-status"]""",
            """[{"policy":"credential-status"}]""",
            """[{"policy":"credential-status","argument":{}}]""",
            """[{"policy":"credential-status","argument":{"value":0}}]""",
            """[{"policy":"credential-status","argument":{"discriminator":"ietf"}}]""",
            """[{"policy":"credential-status","argument":{"discriminator":"w3c","purpose":"revocation","type":"BitstringStatusList"}}]""",
        )) {
            var policies = Json.decodeFromString<VCPolicyList>(encoded)
            policies = Json.decodeFromString(Json.encodeToString(policies))
            val policy = policies.policies.last()
            assertTrue(policy is StatusPolicy)
            val credential = CredentialParser.parseOnly("""{"vct":"urn:example:credential","iss":"https://issuer.example"}""")
            assertTrue(policy.verify(credential).isSuccess)
        }
        assertEquals(listOf("signature", "credential-status"),
            VerificationPolicyManager.defaultCredentialPolicies().policies.map { it.id })
    }

    @Test
    fun `present invalid status must not pass as absent`() = runTest {
        for (status in listOf("null", "{}", "[]", "false", "{\"unknown\":{}}")) {
            val credential = CredentialParser.parseOnly("""{"vct":"urn:example:credential","status":$status}""")
            assertTrue(StatusPolicy().verify(credential).isFailure, status)
        }
        for (status in listOf("null", "[]", "{}", "{\"type\":\"UnknownStatus\"}")) {
            val credential = CredentialParser.parseOnly("""{
                "@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential"],
                "credentialStatus":$status
            }""")
            assertTrue(StatusPolicy().verify(credential).isFailure, status)
        }
    }
}
