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

        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        val actualChallenge = PKCEManager.generateCodeChallenge(verifier, PKCEManager.CodeChallengeMethod.S256)

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", actualChallenge)
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
