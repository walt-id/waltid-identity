package id.walt.x509

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CertificateDerMPTest {

    @Test
    fun `fromPEMEncodedString parses LF and CRLF line endings`() {
        val pemLf = examplePem
        val pemCrLf = examplePem.replace(
            oldValue = "\n",
            newValue = "\r\n",
        )

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
    fun `fromPEMEncodedString fails when header is missing`() {
        val pem = """
            $minimalBase64Payload
            $pemFooter
        """.trimIndent()

        assertFailsWith<IllegalArgumentException>(
            block = {
                CertificateDer.fromPEMEncodedString(
                    pemEncodedCertificate = pem,
                )
            },
        )
    }

    @Test
    fun `fromPEMEncodedString fails when footer is missing`() {
        val pem = """
            $pemHeader
            $minimalBase64Payload
        """.trimIndent()

        assertFailsWith<IllegalArgumentException>(
            block = {
                CertificateDer.fromPEMEncodedString(
                    pemEncodedCertificate = pem,
                )
            },
        )
    }

    @Test
    fun `fromPEMEncodedString fails when footer appears before header`() {
        val pem = """
            $pemFooter
            $minimalBase64Payload
            $pemHeader
        """.trimIndent()

        assertFailsWith<IllegalArgumentException>(
            block = {
                CertificateDer.fromPEMEncodedString(
                    pemEncodedCertificate = pem,
                )
            },
        )
    }

    @Test
    fun `fromPEMEncodedString fails when content precedes header`() {
        val pem = """
            not-a-header
            $pemHeader
            $minimalBase64Payload
            $pemFooter
        """.trimIndent()

        assertFailsWith<IllegalArgumentException>(
            block = {
                CertificateDer.fromPEMEncodedString(
                    pemEncodedCertificate = pem,
                )
            },
        )
    }

    @Test
    fun `fromPEMEncodedString fails when content follows footer`() {
        val pem = """
            $pemHeader
            $minimalBase64Payload
            $pemFooter
            trailing-content
        """.trimIndent()

        assertFailsWith<IllegalArgumentException>(
            block = {
                CertificateDer.fromPEMEncodedString(
                    pemEncodedCertificate = pem,
                )
            },
        )
    }

    @Test
    fun `fromPEMEncodedString fails when multiple PEM blocks are present`() {
        val pem = """
            $pemHeader
            $minimalBase64Payload
            $pemFooter
            $pemHeader
            $minimalBase64Payload
            $pemFooter
        """.trimIndent()

        assertFailsWith<IllegalArgumentException>(
            block = {
                CertificateDer.fromPEMEncodedString(
                    pemEncodedCertificate = pem,
                )
            },
        )
    }

    @Test
    fun `fromPEMEncodedString fails when payload is empty`() {
        val pem = """
            $pemHeader

            $pemFooter
        """.trimIndent()

        assertFailsWith<IllegalArgumentException>(
            block = {
                CertificateDer.fromPEMEncodedString(
                    pemEncodedCertificate = pem,
                )
            },
        )
    }

    @Test
    fun `fromPEMEncodedString fails with single symbol payload`() {
        val pem = """
            -----BEGIN CERTIFICATE-----
            A
            -----END CERTIFICATE-----
        """.trimIndent()

        assertFailsWith<IllegalArgumentException>(
            block = {
                CertificateDer.fromPEMEncodedString(
                    pemEncodedCertificate = pem,
                )
            },
        )
    }

    @Test
    fun `fromPEMEncodedString fails with unpadded payload`() {
        val pem = """
            -----BEGIN CERTIFICATE-----
            AAA
            -----END CERTIFICATE-----
        """.trimIndent()

        assertFailsWith<IllegalArgumentException>(
            block = {
                CertificateDer.fromPEMEncodedString(
                    pemEncodedCertificate = pem,
                )
            },
        )
    }

    private companion object {
        private const val pemHeader = "-----BEGIN CERTIFICATE-----"
        private const val pemFooter = "-----END CERTIFICATE-----"
        private const val minimalBase64Payload = "AQ=="

        private val examplePem = """
            $pemHeader
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
            $pemFooter
        """.trimIndent()
    }
}
