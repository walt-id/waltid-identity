package id.waltid.openid4vci.wallet.oauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PKCEManagerTest {

    @Test
    fun testGenerateCodeVerifier() {
        val verifier = PKCEManager.generateCodeVerifier(64)
        assertEquals(64, verifier.length)
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '.', '_', '~')
        assertTrue(verifier.all { it in allowedChars })
    }

    @Test
    fun testGenerateCodeChallengeS256() {
        // Example from RFC 7636 Appendix B
        // Code Verifier: dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
        // SHA-256 Hash: [ 107, 137, 237, 213, 222, 169, 137, 101, 14, 212, 132, 222, 114, 186, 232, 12, 18, 232, 150, 110, 123, 117, 218, 140, 228, 112, 189, 58, 230, 22, 137, 122 ]
        // Base64URL-encoded Hash: E9Mel-PQDx0nm_7i_9yvSg9SclU

        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        val actualChallenge = PKCEManager.generateCodeChallenge(verifier, PKCEManager.CodeChallengeMethod.S256)

        assertTrue(actualChallenge.isNotEmpty())
    }

    @Test
    fun testGenerateCodeChallengePlain() {
        val verifier = "some-random-verifier"
        val challenge = PKCEManager.generateCodeChallenge(verifier, PKCEManager.CodeChallengeMethod.PLAIN)
        assertEquals(verifier, challenge)
    }

    @Test
    fun testGeneratePKCEData() {
        val data = PKCEManager.generatePKCEData(PKCEManager.CodeChallengeMethod.S256)
        assertEquals(PKCEManager.CodeChallengeMethod.S256, data.codeChallengeMethod)
        assertTrue(data.codeVerifier.length in 43..128)

        val expectedChallenge =
            PKCEManager.generateCodeChallenge(data.codeVerifier, PKCEManager.CodeChallengeMethod.S256)
        assertEquals(expectedChallenge, data.codeChallenge)
    }
}
