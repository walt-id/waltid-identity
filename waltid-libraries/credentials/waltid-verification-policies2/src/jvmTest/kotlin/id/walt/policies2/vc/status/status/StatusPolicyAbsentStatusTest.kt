package id.walt.policies2.vc.status.status

import id.walt.credentials.CredentialParser
import id.walt.policies2.vc.policies.StatusPolicy
import id.walt.policies2.vc.policies.status.entry.MDocEntryExtractor
import id.walt.policies2.vc.policies.status.model.IETFStatusPolicyAttribute
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatusPolicyAbsentStatusTest {

    // SD-JWT VC payload (type urn:eudi:pid:1) without any status mechanism.
    private val sdJwtVcWithoutStatus = """
        {
          "vct": "urn:eudi:pid:1",
          "iss": "https://issuer.example.com",
          "iat": 1720000000,
          "exp": 2000000000,
          "_sd_alg": "sha-256"
        }
    """.trimIndent()

    @Test
    fun `ietf status policy succeeds when credential has no status claim`() = runTest {
        val credential = CredentialParser.parseOnly(sdJwtVcWithoutStatus)

        val result = StatusPolicy(IETFStatusPolicyAttribute(value = 0u)).verify(credential)

        assertTrue(result.isSuccess, result.exceptionOrNull()?.message)
        assertEquals(JsonPrimitive(false), result.getOrThrow().jsonObject["policy_available"])
    }

    @Test
    fun `mdoc entry extractor returns null for non-mdoc credential`() = runTest {
        val credential = CredentialParser.parseOnly(sdJwtVcWithoutStatus)

        assertNull(MDocEntryExtractor().extract(credential))
    }
}