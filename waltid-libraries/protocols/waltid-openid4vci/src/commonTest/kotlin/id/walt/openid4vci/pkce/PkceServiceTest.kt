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

    @Test
    fun authorizationRejectsMalformedRepeatedAndUnsupportedParameters() {
        val invalid = listOf(
            parameters() + ("code_challenge" to emptyList()),
            parameters() + ("code_challenge" to listOf(challenge, "")),
            parameters() + ("code_challenge_method" to emptyList()),
            parameters() + ("code_challenge_method" to listOf("S256", "")),
            parameters() + ("code_challenge_method" to listOf("plain")),
            parameters() + ("code_challenge_method" to listOf("s256")),
            parameters() + ("code_challenge_method" to listOf("unknown")),
            parameters() + ("code_challenge_method" to listOf("")),
            parameters() - "code_challenge_method",
            parameters() - "code_challenge",
        ) + listOf("", "a".repeat(42), "a".repeat(44), "a".repeat(42) + "=", ".".repeat(43), "é".repeat(43))
            .map { parameters() + ("code_challenge" to listOf(it)) }
        for (form in invalid) {
            val failure = assertIs<PkceAuthorizationResult.Failure>(service.validateAuthorizationRequest(form), form.toString())
            assertEquals(OAuthErrorCodes.INVALID_REQUEST, failure.error.error)
        }
    }

    @Test
    fun tokenRejectsMissingDuplicateAndInvalidVerifiers() {
        assertEquals(OAuthErrorCodes.INVALID_REQUEST,
            assertIs<PkceVerificationResult.Failure>(service.verifyTokenRequest(emptyMap(), binding)).error.error)
        for (values in listOf(emptyList(), listOf(verifier, ""), listOf("", verifier))) {
            assertEquals(OAuthErrorCodes.INVALID_REQUEST,
                assertIs<PkceVerificationResult.Failure>(service.verifyTokenRequest(mapOf("code_verifier" to values), binding)).error.error)
        }
        for (value in listOf("", "a".repeat(42), "a".repeat(129), "?".repeat(43), "é".repeat(43), " ".repeat(43))) {
            // Even a matching hash cannot make invalid verifier syntax acceptable.
            val matching = PkceBinding.Challenge(Pkce.generateS256CodeChallenge(value), "S256")
            assertEquals(OAuthErrorCodes.INVALID_GRANT,
                assertIs<PkceVerificationResult.Failure>(service.verifyTokenRequest(mapOf("code_verifier" to listOf(value)), matching)).error.error)
        }
        assertEquals(OAuthErrorCodes.INVALID_GRANT,
            assertIs<PkceVerificationResult.Failure>(service.verifyTokenRequest(mapOf("code_verifier" to listOf("x".repeat(43))), binding)).error.error)
    }

    private fun parameters() = mapOf("code_challenge" to listOf(challenge), "code_challenge_method" to listOf("S256"))
}
