package id.walt.ades.jades

import id.walt.ades.test.CommonSigningFixture
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.Indication
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CommonSigningTest {
    @Test
    fun softwareKeyRoundTrip() = runTest {
        val payload = "portable signing test".encodeToByteArray()
        val signed = JadesSigner(CommonSigningFixture.credentials()).sign(payload)
        val validator = JadesValidator(AdesValidationPolicy(trustAnchors = listOf(CommonSigningFixture.certificate)))
        assertEquals(Indication.TOTAL_PASSED, validator.validate(signed).indication)
    }
}
