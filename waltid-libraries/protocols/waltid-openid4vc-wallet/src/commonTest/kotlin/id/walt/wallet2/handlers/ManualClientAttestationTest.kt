package id.walt.wallet2.handlers

import id.waltid.openid4vci.wallet.attestation.ClientAttestationHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ManualClientAttestationTest {
    private val attestation = ClientAttestationHeaders.HEADER_ATTESTATION
    private val proof = ClientAttestationHeaders.HEADER_ATTESTATION_POP

    @Test
    fun absentAttestationDoesNotSelectManualAuthentication() {
        assertNull(parseManualClientAttestationHeaders(emptyMap()))
        assertNull(parseManualClientAttestationHeaders(mapOf("X-Tenant" to "tenant")))
    }

    @Test
    fun namesAreCaseInsensitiveAndValuesArePreservedWithoutJwtValidation() {
        assertEquals(
            ClientAttestationHeaders("opaque-attestation", " opaque-proof "),
            parseManualClientAttestationHeaders(mapOf(
                attestation.lowercase() to "opaque-attestation",
                proof.uppercase() to " opaque-proof ",
            )),
        )
    }

    @Test
    fun malformedPairsAreRejectedWithoutIncludingHeaderValues() {
        val valid = mapOf(attestation to "private-wia", proof to "private-pop")
        val malformed = listOf(
            mapOf(attestation to "private-wia"),
            mapOf(proof to "private-pop"),
            valid + (attestation to ""),
            valid + (proof to " \t"),
            valid + (attestation.lowercase() to "private-duplicate"),
            valid + (proof.uppercase() to "private-duplicate"),
        )
        for (headers in malformed) {
            val failure = assertFailsWith<IllegalArgumentException> {
                parseManualClientAttestationHeaders(headers)
            }
            assertFalse(failure.message.orEmpty().contains("private-"))
        }
    }
}
