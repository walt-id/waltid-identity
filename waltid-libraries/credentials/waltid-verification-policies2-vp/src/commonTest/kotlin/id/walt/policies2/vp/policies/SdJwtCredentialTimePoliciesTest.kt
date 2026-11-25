package id.walt.policies2.vp.policies

import id.walt.credentials.CredentialDetectorTypes.SDJWTVCSubType
import id.walt.credentials.formats.SdJwtCredential
import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class SdJwtCredentialTimePoliciesTest {
    @Test
    fun `expired and future not-before claims in reconstructed data fail`() = runTest {
        val now = Clock.System.now().epochSeconds
        assertFalse(ExpCheckSdJwtVPPolicy().runPolicy(presentation(mapOf("exp" to now - 3600)), null).success)
        assertFalse(NbfCheckSdJwtVPPolicy().runPolicy(presentation(mapOf("nbf" to now + 3600)), null).success)
    }

    @Test
    fun `valid and absent optional time claims pass`() = runTest {
        val now = Clock.System.now().epochSeconds
        assertTrue(ExpCheckSdJwtVPPolicy().runPolicy(presentation(mapOf("exp" to now + 3600)), null).success)
        assertTrue(NbfCheckSdJwtVPPolicy().runPolicy(presentation(mapOf("nbf" to now - 3600)), null).success)
        assertTrue(ExpCheckSdJwtVPPolicy().runPolicy(presentation(emptyMap()), null).success)
        assertTrue(NbfCheckSdJwtVPPolicy().runPolicy(presentation(emptyMap()), null).success)
    }

    @Test
    fun `configured clock skew still applies to reconstructed claims`() = runTest {
        val now = Clock.System.now().epochSeconds
        val expired = presentation(mapOf("exp" to now - 30))
        val future = presentation(mapOf("nbf" to now + 30))
        assertTrue(ExpCheckSdJwtVPPolicy(clockSkewSeconds = 60).runPolicy(expired, null).success)
        assertFalse(ExpCheckSdJwtVPPolicy(clockSkewSeconds = 0).runPolicy(expired, null).success)
        assertTrue(NbfCheckSdJwtVPPolicy(clockSkewSeconds = 60).runPolicy(future, null).success)
        assertFalse(NbfCheckSdJwtVPPolicy(clockSkewSeconds = 0).runPolicy(future, null).success)
    }

    private fun presentation(claims: Map<String, Long>): DcSdJwtPresentation {
        val core = "{\"alg\":\"ES256\"}".encodeToByteArray().encodeToBase64Url() + "." +
            "{\"vct\":\"test\"}".encodeToByteArray().encodeToBase64Url() + ".c2ln"
        return DcSdJwtPresentation(
            sdJwt = core,
            keyBindingJwt = "",
            credential = SdJwtCredential(
                dmtype = SDJWTVCSubType.sdjwtvc,
                credentialData = JsonObject(claims.mapValues { JsonPrimitive(it.value) }),
                signature = null,
                signed = core,
            ),
            audience = null,
            nonce = null,
            sdHash = null,
            presentationStringHashable = "$core~",
        )
    }
}
