package id.walt.x509

import kotlinx.io.bytestring.ByteString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class IsoRicalSignerCertificateTest {
    @Test
    fun `ISO certificate serial numbers require a positive non-zero 63-bit value`() {
        validateIsoCertificateSerialNumber(
            ByteString(byteArrayOf(0x40, 0, 0, 0, 0, 0, 0, 0)),
            "Test certificate",
        )

        listOf(
            byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
            byteArrayOf(0, 0, 1, 0, 0, 0, 0, 0, 0),
            byteArrayOf(0x80.toByte(), 0, 0, 0, 0, 0, 0, 0),
        ).forEach { invalid ->
            assertFailsWith<X509ValidationException> {
                validateIsoCertificateSerialNumber(ByteString(invalid), "Test certificate")
            }
        }
    }

    @Test
    fun `RICAL signer profile and path require accepted policy and explicit root`() {
        validateRicalSignerCertificateProfile(
            signer,
            acceptedCertificatePolicyOids = setOf("1.2.3.4"),
            now = Instant.parse("2026-09-01T00:00:00Z"),
        )
        validateRicalSignerCertificateChain(
            leaf = signer,
            chain = emptyList(),
            trustAnchors = listOf(root),
            now = Instant.parse("2026-09-01T00:00:00Z"),
        )

        assertFailsWith<X509ValidationException> {
            validateRicalSignerCertificateProfile(
                signer,
                acceptedCertificatePolicyOids = setOf("1.2.3.5"),
                now = Instant.parse("2026-09-01T00:00:00Z"),
            )
        }
        assertFailsWith<X509ValidationException> {
            validateRicalSignerCertificateChain(
                leaf = signer,
                chain = listOf(root),
                trustAnchors = listOf(root),
                now = Instant.parse("2026-09-01T00:00:00Z"),
            )
        }
        assertFailsWith<X509ValidationException> {
            validateRicalSignerCertificateChain(
                leaf = root,
                chain = emptyList(),
                trustAnchors = listOf(root),
                now = Instant.parse("2026-09-01T00:00:00Z"),
            )
        }
    }

    @Test
    fun `expired RICAL signer is rejected`() {
        assertFailsWith<X509ValidationException> {
            validateRicalSignerCertificateProfile(
                signer,
                acceptedCertificatePolicyOids = setOf("1.2.3.4"),
                now = Instant.parse("2026-10-01T00:00:00Z"),
            )
        }
        assertFailsWith<X509ValidationException> {
            validateRicalSignerCertificateProfile(
                signer,
                acceptedCertificatePolicyOids = setOf("1.2.3.4"),
                now = Instant.parse("2026-09-29T20:42:06Z"),
            )
        }
    }

    private companion object {
        @OptIn(ExperimentalEncodingApi::class)
        val root = CertificateDer(
            Base64.decode(
                "MIIBjjCCATOgAwIBAgIIQAAAAAAAAAEwCgYIKoZIzj0EAwIwGjEYMBYGA1UEAwwPUklDQUwgVGVzdCBSb290MB4XDTI2MDgzMDIwNDIwNloXDTI3MDgzMDIwNDIwNlowGjEYMBYGA1UEAwwPUklDQUwgVGVzdCBSb290MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE4Fc8ezWNtclkN8nWzjzYGxLUF0J6FO0r8pq5/YWiprJBGwTK0CIcesfDL+/pxMofjHC5p4TRlb4yuCg9XlIW46NjMGEwHwYDVR0jBBgwFoAU0wI39+8enyK9prUvHV4RWCaylxkwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFNMCN/fvHp8ivaa1Lx1eEVgmspcZMAoGCCqGSM49BAMCA0kAMEYCIQDYcsOwU11G+fme3xD2EgCgE45qXt3Rg8nhJY2IuA2+3QIhALVJKhK8NfsJMONZo8amHvX43b2PuSWhmB3DHufEOnE0"
            )
        )

        @OptIn(ExperimentalEncodingApi::class)
        val signer = CertificateDer(
            Base64.decode(
                "MIIB6zCCAZGgAwIBAgIIQAAAAAAAAAIwCgYIKoZIzj0EAwIwGjEYMBYGA1UEAwwPUklDQUwgVGVzdCBSb290MB4XDTI2MDgzMDIwNDIwNloXDTI2MDkyOTIwNDIwNlowSTELMAkGA1UEBhMCQVQxHjAcBgNVBAoMFXdhbHQuaWQgdGVzdCBmaXh0dXJlczEaMBgGA1UEAwwRUklDQUwgVGVzdCBTaWduZXIwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARtK4j0HidmssYZ3x9zeCEnQPrWct9tEeGqIrjLTJZomS7IbkM98UEC62dKFVWJi/AU+v7B2TamaU99V6Rem81No4GRMIGOMB0GA1UdDgQWBBSti6kphqKkS4k8rsZSMdVgQ2EhlzAfBgNVHSMEGDAWgBTTAjf37x6fIr2mtS8dXhFYJrKXGTAOBgNVHQ8BAf8EBAMCBkAwKgYDVR0fBCMwITAfoB2gG4YZaHR0cHM6Ly9yaWNhbC5leGFtcGxlL2NybDAQBgNVHSAECTAHMAUGAyoDBDAKBggqhkjOPQQDAgNIADBFAiEAkI4lWDQLobERLUoD9MjRf11cab4NLjcG8J1kZYwNxAYCIGGlfeY9tsoOj1GAfPJcd6QEB0LaySFQubeImAxEaARY"
            )
        )
    }
}
