package id.walt.ades.jades

import id.walt.ades.test.CommonSigningFixture
import id.walt.ades.validation.AdesValidationPolicy
import id.walt.ades.validation.Indication
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommonSigningTest {
    @Test
    fun softwareKeyRoundTrip() = runTest {
        val payload = "portable signing test".encodeToByteArray()
        val signed = JadesSigner(CommonSigningFixture.credentials()).sign(payload)
        val validator = JadesValidator(AdesValidationPolicy(trustAnchors = listOf(CommonSigningFixture.certificate)))
        assertEquals(Indication.TOTAL_PASSED, validator.validate(signed).indication)
    }
    @Test
    fun emptyAttachedJsonPayloadRemainsDistinctFromDetachedPayload() = runTest {
        val signer = JadesSigner(CommonSigningFixture.credentials())
        val validator = JadesValidator(AdesValidationPolicy(trustAnchors = listOf(CommonSigningFixture.certificate)))
        for (serialization in listOf(JadesSerialization.JSON_FLATTENED, JadesSerialization.JSON_GENERAL)) {
            for (base64 in listOf(true, false)) {
                val attached = signer.sign(ByteArray(0), JadesSignatureParameters(serialization = serialization, base64UrlPayload = base64))
                assertFalse(JadesDocument.parse(attached).isDetached)
                assertEquals(Indication.TOTAL_PASSED, validator.validate(attached).indication)
                val detached = signer.sign(ByteArray(0), JadesSignatureParameters(serialization = serialization, base64UrlPayload = base64, detached = true))
                assertTrue(JadesDocument.parse(detached).isDetached)
                assertEquals(Indication.TOTAL_PASSED, validator.validate(detached, detachedPayload = ByteArray(0)).indication)
                assertFalse(validator.validate(detached).isValid)
            }
        }
        val compact = signer.sign(ByteArray(0), JadesSignatureParameters(serialization = JadesSerialization.COMPACT))
        assertTrue(JadesDocument.parse(compact).isDetached)
        assertEquals(Indication.TOTAL_PASSED, validator.validate(compact, detachedPayload = ByteArray(0)).indication)
    }

}
