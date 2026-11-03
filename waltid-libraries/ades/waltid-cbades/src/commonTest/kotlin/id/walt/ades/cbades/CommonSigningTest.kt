package id.walt.ades.cbades

import id.walt.ades.test.CommonSigningFixture
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.Indication
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CommonSigningTest {
    @Test
    fun softwareKeyRoundTrip() = runTest {
        val payload = "portable signing test".encodeToByteArray()
        val signed = CbAdesSigner().signSign1(payload, CommonSigningFixture.credentials())
        val validator = CbAdesValidator(AdesValidationPolicy(trustAnchors = listOf(CommonSigningFixture.certificate)))
        assertEquals(Indication.TOTAL_PASSED, validator.validate(signed.encode()).indication)
        assertFalse(validator.validate(CoseMessage.Sign1(signed.headers, "tampered".encodeToByteArray(), signed.signature, signed.tagged).encode()).indication == Indication.TOTAL_PASSED)
    }
}
