package id.walt.certificate.x509.revocation

import id.walt.certificate.x509.X509CertificateUtil
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Instant

class CertificateRevocationListVerifierTest {
    private val verifier = CertificateRevocationListVerifier()
    private val at = Instant.parse("2026-09-07T21:00:00Z")

    @Test
    fun independentEcdsaAndRsaCrlsEstablishGoodAndRevokedLeafAndCaStatus() = runTest {
        for (profile in listOf("EC256", "EC384", "EC512", "RSA256")) {
            val issuer = certificate("${profile}_CA")
            for (subject in listOf(certificate("${profile}_LEAF"), issuer)) {
                assertEquals(
                    CrlCertificateStatus.Good(Instant.parse("2026-09-07T20:00:00Z"), Instant.parse("2026-09-08T04:00:00Z")),
                    verifier.verify(der("${profile}_GOOD"), subject, issuer, at), profile,
                )
                assertEquals(
                    CrlCertificateStatus.Revoked(Instant.parse("2026-09-07T19:00:00Z"), 6),
                    verifier.verify(der("${profile}_REVOKED"), subject, issuer, at), profile,
                )
            }
        }
        for (name in listOf("RSA384_GOOD", "RSA512_GOOD")) {
            assertIs<CrlCertificateStatus.Good>(verifier.verify(der(name), certificate("RSA256_LEAF"), certificate("RSA256_CA"), at), name)
        }
    }

    @Test
    fun freshnessAcceptsThisUpdateButRejectsFutureAndExpiredLists() = runTest {
        val leaf = certificate("EC256_LEAF")
        val issuer = certificate("EC256_CA")
        val crl = der("EC256_GOOD")
        assertIs<CrlCertificateStatus.Good>(verifier.verify(crl, leaf, issuer, Instant.parse("2026-09-07T20:00:00Z")))
        for (time in listOf("2026-09-07T19:59:59Z", "2026-09-08T04:00:00Z", "2026-09-09T00:00:00Z")) {
            assertEquals(CrlCertificateStatus.Indeterminate(CrlFailure.STALE), verifier.verify(crl, leaf, issuer, Instant.parse(time)), time)
        }
    }

    @Test
    fun unsupportedCrlScopeAndCriticalExtensionsNeverEstablishGood() = runTest {
        val leaf = certificate("EC256_LEAF")
        val issuer = certificate("EC256_CA")
        for (name in listOf("UNKNOWN_CRITICAL", "DELTA", "PARTITIONED", "INDIRECT_ENTRY", "NO_AKI", "NO_NUMBER",
            "CRITICAL_AKI", "CRITICAL_NUMBER", "REMOVE_FROM_CRL")) {
            assertEquals(CrlCertificateStatus.Indeterminate(CrlFailure.UNSUPPORTED_PROFILE), verifier.verify(der(name), leaf, issuer, at), name)
        }
        assertIs<CrlCertificateStatus.Good>(verifier.verify(der("UNKNOWN_NONCRITICAL"), leaf, issuer, at))
        assertEquals(CrlCertificateStatus.Indeterminate(CrlFailure.INVALID_DER), verifier.verify(der("FUTURE_REVOCATION"), leaf, issuer, at))
    }

    @Test
    fun issuerMustBeACurrentCaAuthorisedToSignCrls() = runTest {
        for (name in listOf("ISSUER_NOT_CA", "ISSUER_NO_CRL_SIGN", "ISSUER_EXPIRED", "ISSUER_NOT_YET_VALID")) {
            assertEquals(CrlCertificateStatus.Indeterminate(CrlFailure.INVALID_ISSUER),
                verifier.verify(der("EC256_GOOD"), certificate("EC256_LEAF"), certificate(name), at), name)
        }
    }

    @Test
    fun wrongIssuerAndSigningKeyCannotEstablishStatus() = runTest {
        val leaf = certificate("EC256_LEAF")
        assertEquals(CrlCertificateStatus.Indeterminate(CrlFailure.ISSUER_MISMATCH),
            verifier.verify(der("EC256_GOOD"), leaf, certificate("EC384_CA"), at))
        assertEquals(CrlCertificateStatus.Indeterminate(CrlFailure.ISSUER_MISMATCH),
            verifier.verify(der("WRONG_KEY"), leaf, certificate("EC256_CA"), at))
        val corrupted = CrlTestFixtures.der("EC256_GOOD").also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertEquals(CrlCertificateStatus.Indeterminate(CrlFailure.INVALID_SIGNATURE),
            verifier.verify(ByteString(corrupted), leaf, certificate("EC256_CA"), at))
    }

    @Test
    fun malformedLengthsTrailingBytesAndInputLimitNeverEstablishStatus() = runTest {
        val leaf = certificate("EC256_LEAF")
        val issuer = certificate("EC256_CA")
        val valid = CrlTestFixtures.der("EC256_GOOD")
        val cases = listOf(
            byteArrayOf(), valid.copyOf(2), valid.copyOf(valid.size - 1), valid + byteArrayOf(0),
            valid.copyOf().also { it[1] = 0x80.toByte() }, // DER forbids indefinite length.
            byteArrayOf(0x30, 0x83.toByte(), 0x7f, 0xff.toByte(), 0xff.toByte()),
            ByteArray(2_097_153),
        )
        for ((index, bytes) in cases.withIndex()) {
            assertEquals(CrlCertificateStatus.Indeterminate(CrlFailure.INVALID_DER),
                verifier.verify(ByteString(bytes), leaf, issuer, at), "malformed fixture $index")
        }
    }

    @Test
    fun serialNumbersRequirePositiveCanonicalDerWithinTwentyOctets() {
        assertEquals(ByteString(byteArrayOf(0x80.toByte(), 1)), normaliseSerial(byteArrayOf(0, 0x80.toByte(), 1)))
        assertEquals(ByteString(ByteArray(20) { 0x7f }), normaliseSerial(ByteArray(20) { 0x7f }))
        for (bytes in listOf(byteArrayOf(), byteArrayOf(0), byteArrayOf(0, 1), byteArrayOf(0x80.toByte()),
            byteArrayOf(0) + ByteArray(20) { 0x80.toByte() })) {
            assertEquals(CrlFailure.INVALID_DER, assertFailsWith<CrlValidationException> { normaliseSerial(bytes) }.reason)
        }
    }

    @Test
    fun mismatchedAndUnsupportedSignatureAlgorithmsRemainIndeterminate() = runTest {
        val oid = byteArrayOf(0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x04, 0x03, 0x02)
        val valid = CrlTestFixtures.der("EC256_GOOD")
        val offsets = (0..valid.size - oid.size).filter { offset ->
            oid.indices.all { valid[offset + it] == oid[it] }
        }
        assertEquals(2, offsets.size)
        for ((changes, expected) in listOf(offsets.take(1) to CrlFailure.INVALID_DER, offsets to CrlFailure.UNSUPPORTED_PROFILE)) {
            val bytes = valid.copyOf()
            changes.forEach { bytes[it + oid.lastIndex] = 1 } // ECDSA with SHA-224 is outside this profile.
            assertEquals(CrlCertificateStatus.Indeterminate(expected),
                verifier.verify(ByteString(bytes), certificate("EC256_LEAF"), certificate("EC256_CA"), at))
        }
    }

    private fun der(name: String) = ByteString(CrlTestFixtures.der(name))
    private fun certificate(name: String) = X509CertificateUtil.parseCertificateDerEncoded(der(name))
}
