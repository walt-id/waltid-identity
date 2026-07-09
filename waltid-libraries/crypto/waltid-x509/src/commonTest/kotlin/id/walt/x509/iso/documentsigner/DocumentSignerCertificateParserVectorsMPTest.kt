package id.walt.x509.iso.documentsigner

import id.walt.x509.CertificateDer
import id.walt.x509.iso.DocumentSignerEkuOID
import id.walt.x509.iso.documentsigner.parser.DocumentSignerCertificateParser
import id.walt.x509.iso.documentsigner.validate.DocumentSignerValidator
import id.walt.x509.iso.iaca.parser.IACACertificateParser
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentSignerCertificateParserVectorsMPTest {

    @Test
    fun `parser validates known document signer certificate vector`() = runTest {
        val iacaDecodedCertificate = IACACertificateParser().parse(iacaCertificate)
        val documentSignerDecodedCertificate = DocumentSignerCertificateParser().parse(documentSignerCertificate)

        assertEquals("US", documentSignerDecodedCertificate.principalName.country)
        assertEquals("Texas", documentSignerDecodedCertificate.principalName.stateOrProvinceName)
        assertEquals("Example DS Org", documentSignerDecodedCertificate.principalName.organizationName)
        assertEquals("Austin", documentSignerDecodedCertificate.principalName.localityName)
        assertEquals("Example Document Signer", documentSignerDecodedCertificate.principalName.commonName)
        assertEquals("https://iaca.example.com/crl", documentSignerDecodedCertificate.crlDistributionPointUri)
        assertEquals(setOf(DocumentSignerEkuOID), documentSignerDecodedCertificate.extendedKeyUsage)

        DocumentSignerValidator().validate(
            dsDecodedCert = documentSignerDecodedCertificate,
            iacaDecodedCert = iacaDecodedCertificate,
        )
    }

    private companion object {
        private val iacaCertificate = CertificateDer(
            Base64.decode(
                "MIICQDCCAeWgAwIBAgIUBOBgaUQ+yzwCbUpgv2Q06v2RDnEwCgYIKoZIzj0EAwIwTzELMAkGA1UEBhMCVVMxDjAMBgNVBAgMBVRleGFzMRkwFwYDVQQKDBBFeGFtcGxlIElBQ0EgT3JnMRUwEwYDVQQDDAxFeGFtcGxlIElBQ0EwHhcNMjYwNjI5MTUzOTUzWhcNMzYwNjI2MTUzOTUzWjBPMQswCQYDVQQGEwJVUzEOMAwGA1UECAwFVGV4YXMxGTAXBgNVBAoMEEV4YW1wbGUgSUFDQSBPcmcxFTATBgNVBAMMDEV4YW1wbGUgSUFDQTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABNhTb3o2q0TTCeb4DkXXaglTeiHa3Q61HkQ0DN7UfMJjREyzW+cnRo+GYOjonoPodfavr6sv7DndLqAruU6NLN6jgZ4wgZswEgYDVR0TAQH/BAgwBgEB/wIBADAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFOH8U2/p0YCQ4aIP1vFiLUA7FXLBMB8GA1UdIwQYMBaAFOH8U2/p0YCQ4aIP1vFiLUA7FXLBMDUGA1UdEgQuMCyBEGlhY2FAZXhhbXBsZS5jb22GGGh0dHBzOi8vaWFjYS5leGFtcGxlLmNvbTAKBggqhkjOPQQDAgNJADBGAiEAsWQJeBHjy3MfcKljZMgQvHiQQGsXUaTnNoDV0i678AYCIQCgvvNUJQGbOg0OuOMvqtEw2Zoi4aD/PxDuwWemrTvi7A=="
            )
        )

        private val documentSignerCertificate = CertificateDer(
            Base64.decode(
                "MIICjDCCAjGgAwIBAgIUPpcX19EybwDv1nw4dghb3U3Sj9EwCgYIKoZIzj0EAwIwTzELMAkGA1UEBhMCVVMxDjAMBgNVBAgMBVRleGFzMRkwFwYDVQQKDBBFeGFtcGxlIElBQ0EgT3JnMRUwEwYDVQQDDAxFeGFtcGxlIElBQ0EwHhcNMjYwNjI5MTUzOTUzWhcNMjcwODAzMTUzOTUzWjBpMQswCQYDVQQGEwJVUzEOMAwGA1UECAwFVGV4YXMxFzAVBgNVBAoMDkV4YW1wbGUgRFMgT3JnMQ8wDQYDVQQHDAZBdXN0aW4xIDAeBgNVBAMMF0V4YW1wbGUgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAERqr6qaGRgk0+qXV2psSsd75MKo46pwxVnb5x7iR+MMOPeMX4WZe/h5JRT9OeNFrtkNygiU4BSE3hBkvJ+vKk4KOB0DCBzTAOBgNVHQ8BAf8EBAMCB4AwFQYDVR0lAQH/BAswCQYHKIGMXQUBAjAdBgNVHQ4EFgQUGy6wZMPf3ubO25W6jw2XQk3NfecwHwYDVR0jBBgwFoAU4fxTb+nRgJDhog/W8WItQDsVcsEwNQYDVR0SBC4wLIEQaWFjYUBleGFtcGxlLmNvbYYYaHR0cHM6Ly9pYWNhLmV4YW1wbGUuY29tMC0GA1UdHwQmMCQwIqAgoB6GHGh0dHBzOi8vaWFjYS5leGFtcGxlLmNvbS9jcmwwCgYIKoZIzj0EAwIDSQAwRgIhAIbLX+FSk0LSNWMEEE8n0YdhHdSwnEi0khC8PgjyzvdmAiEAuZ97DD2Py/4SrVn5OEv55fjzbqt5RaZy5E8VfSg8pHE="
            )
        )
    }
}
