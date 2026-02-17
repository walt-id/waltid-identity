package id.walt.x509

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CertificateDerMPTest {

    @Test
    fun `fromPEMEncodedString parses LF and CRLF line endings`() {
        val pemLf = examplePem
        val pemCrLf = examplePem.replace("\n", "\r\n")

        val derFromLf = CertificateDer.fromPEMEncodedString(
            pemEncodedCertificate = pemLf,
        )
        val derFromCrLf = CertificateDer.fromPEMEncodedString(
            pemEncodedCertificate = pemCrLf,
        )

        assertEquals(
            expected = derFromLf.bytes,
            actual = derFromCrLf.bytes,
        )
    }

    @Test
    fun `fromPEMEncodedString round trips with toPEMEncodedString`() {
        val der = CertificateDer.fromPEMEncodedString(
            pemEncodedCertificate = examplePem,
        )

        val roundTrip = CertificateDer.fromPEMEncodedString(
            pemEncodedCertificate = der.toPEMEncodedString(),
        )

        assertEquals(
            expected = der.bytes,
            actual = roundTrip.bytes,
        )
    }

    @Test
    fun `fromPEMEncodedString fails with single symbol payload`() {
        val pem = """
            -----BEGIN CERTIFICATE-----
            A
            -----END CERTIFICATE-----
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            CertificateDer.fromPEMEncodedString(
                pemEncodedCertificate = pem,
            )
        }
    }

    @Test
    fun `fromPEMEncodedString fails with unpadded payload`() {
        val pem = """
            -----BEGIN CERTIFICATE-----
            AAA
            -----END CERTIFICATE-----
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            CertificateDer.fromPEMEncodedString(
                pemEncodedCertificate = pem,
            )
        }
    }

    private companion object {
        private val examplePem = """
            -----BEGIN CERTIFICATE-----
            MIIBtDCCAVqgAwIBAgIUTEBApuzyNump/cYzKXVdgubtZIwwCgYIKoZIzj0EAwIw
            JDELMAkGA1UEBhMCVVMxFTATBgNVBAMMDEV4YW1wbGUgSUFDQTAeFw0yNTA1Mjgx
            MjIzMDFaFw00MDA1MjQxMjIzMDFaMCQxCzAJBgNVBAYTAlVTMRUwEwYDVQQDDAxF
            eGFtcGxlIElBQ0EwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASrTGLbW652GQLV
            AzKR9ivI31twPHjzSIktJpEjTkJaBjYQ/tPcaq1IBNvqkrfIOYpnj4CjzzVaWKB5
            rEy9n+Iqo2owaDAdBgNVHQ4EFgQUmK8RITk6hvP5QrEfCFfKSNIVWjgwEgYDVR0T
            AQH/BAgwBgEB/wIBADAjBgNVHRIEHDAahhhodHRwczovL2lhY2EuZXhhbXBsZS5j
            b20wDgYDVR0PAQH/BAQDAgEGMAoGCCqGSM49BAMCA0gAMEUCIQCN8SX5ojwspuyL
            W/XZBSTYpFj3bqpAOWthCLoxW29pNAIgSYLq8sE43y2Bf1pDvKu5cYjtkJ8hel53
            z4eL4VJvD1A=
            -----END CERTIFICATE-----
        """.trimIndent()
    }
}
