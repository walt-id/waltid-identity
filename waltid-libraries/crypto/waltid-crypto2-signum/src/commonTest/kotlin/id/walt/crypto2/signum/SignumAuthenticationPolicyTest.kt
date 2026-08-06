package id.walt.crypto2.signum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SignumAuthenticationPolicyTest {
    @Test
    fun `user presence accepts independent factors and operation text`() {
        val policy = SignumAuthenticationPolicy.UserPresence(
            biometric = true,
            allowNewBiometrics = true,
            deviceCredential = true,
            timeoutSeconds = 30,
            prompt = "Approve signing",
            cancelText = "Not now",
        )

        assertEquals(true, policy.biometric)
        assertEquals(true, policy.allowNewBiometrics)
        assertEquals(true, policy.deviceCredential)
        assertEquals(30, policy.timeoutSeconds)
        assertEquals("Approve signing", policy.prompt)
        assertEquals("Not now", policy.cancelText)
    }

    @Test
    fun `user presence rejects impossible factor and prompt combinations`() {
        assertFailsWith<IllegalArgumentException> {
            SignumAuthenticationPolicy.UserPresence(biometric = false, deviceCredential = false)
        }
        assertFailsWith<IllegalArgumentException> {
            SignumAuthenticationPolicy.UserPresence(biometric = false, allowNewBiometrics = true)
        }
        assertFailsWith<IllegalArgumentException> {
            SignumAuthenticationPolicy.UserPresence(timeoutSeconds = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            SignumAuthenticationPolicy.UserPresence(prompt = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            SignumAuthenticationPolicy.UserPresence(cancelText = " ")
        }
    }
}
