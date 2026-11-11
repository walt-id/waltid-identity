package id.walt.openid4vci.pkce

import id.walt.openid4vci.errors.OAuthErrorCodes
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PkceServiceTest {
    private val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    private val challenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
    private val binding = PkceBinding.Challenge(challenge, "S256")
    private val service = PkceService()

    @Test
    fun primitivesMatchRfcAndGenerateValidVerifierLengths() {
        assertEquals(challenge, Pkce.generateS256CodeChallenge(verifier))
        for (length in 43..128) {
            val generated = Pkce.generateCodeVerifier(length)
            assertEquals(length, generated.length)
            assertTrue(generated.matches(Regex("[A-Za-z0-9_-]+")))
        }
        assertEquals(128, Pkce.generateCodeVerifier().length)
        for (length in listOf(-1, 0, 42, 129)) {
            assertFailsWith<IllegalArgumentException> { Pkce.generateCodeVerifier(length) }
        }
    }

    @Test
    fun optionalAndRequiredPoliciesAreEnforcedAtBothStages() {
        assertEquals(PkceAuthorizationResult.Success(PkceBinding.NotUsed), service.validateAuthorizationRequest(emptyMap()))
        assertIs<PkceVerificationResult.Success>(service.verifyTokenRequest(emptyMap(), PkceBinding.NotUsed))
        val required = PkceService(PkceServiceConfig(requirePkce = true))
        assertEquals(OAuthErrorCodes.INVALID_REQUEST,
            assertIs<PkceAuthorizationResult.Failure>(required.validateAuthorizationRequest(emptyMap())).error.error)
        assertEquals(OAuthErrorCodes.INVALID_GRANT,
            assertIs<PkceVerificationResult.Failure>(required.verifyTokenRequest(emptyMap(), PkceBinding.NotUsed)).error.error)
        assertEquals(PkceAuthorizationResult.Success(binding), required.validateAuthorizationRequest(parameters()))
        assertIs<PkceVerificationResult.Success>(required.verifyTokenRequest(mapOf("code_verifier" to listOf(verifier)), binding))
    }

    private fun parameters() = mapOf("code_challenge" to listOf(challenge), "code_challenge_method" to listOf("S256"))
}
