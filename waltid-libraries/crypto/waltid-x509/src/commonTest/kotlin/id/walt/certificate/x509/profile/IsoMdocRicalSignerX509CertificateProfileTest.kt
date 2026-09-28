package id.walt.certificate.x509.profile

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.validation.X509SingleCertificateValidator
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsoMdocRicalSignerX509CertificateProfileTest {

    @Test
    fun `RICAL signer certificate with an accepted policy passes`() = runTest {
        val result = validate(acceptedCertificatePolicyOids = setOf("1.2.3.4"))
        assertTrue(result.valid, "Validation log: ${result.log}")
    }

    @Test
    fun `RICAL signer certificate without an accepted policy is rejected`() = runTest {
        val result = validate(acceptedCertificatePolicyOids = setOf("1.2.3.5"))
        assertFalse(result.valid, "Validation log: ${result.log}")
    }

    @Test
    fun `RICAL signer certificate chains to its explicit provider root`() = runTest {
        val result = X509CertificateUtil.validateCertificateChain(listOf(signer), root)
        assertTrue(result.valid, "Validation log: ${result.log}")
    }

    @Test
    fun `RICAL signer certificate does not chain without its provider root as an explicit trust anchor`() = runTest {
        val result = X509CertificateUtil.validateCertificateChain(listOf(signer))
        assertFalse(result.valid, "Validation log: ${result.log}")
    }

    private suspend fun validate(acceptedCertificatePolicyOids: Set<String>) =
        X509SingleCertificateValidator(
            listOf(IsoMdocRicalSignerX509CertificateProfile(acceptedCertificatePolicyOids))
        ).validate(signer)

    private companion object {
        @OptIn(ExperimentalEncodingApi::class)
        val root = X509CertificateUtil.parseCertificateDerEncoded(
            ByteString(
                Base64.decode(
                    "MIIBjjCCATOgAwIBAgIIQAAAAAAAAAEwCgYIKoZIzj0EAwIwGjEYMBYGA1UEAwwPUklDQUwgVGVzdCBSb290MB4XDTI2MDgzMDIwNDIwNloXDTI3MDgzMDIwNDIwNlowGjEYMBYGA1UEAwwPUklDQUwgVGVzdCBSb290MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE4Fc8ezWNtclkN8nWzjzYGxLUF0J6FO0r8pq5/YWiprJBGwTK0CIcesfDL+/pxMofjHC5p4TRlb4yuCg9XlIW46NjMGEwHwYDVR0jBBgwFoAU0wI39+8enyK9prUvHV4RWCaylxkwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFNMCN/fvHp8ivaa1Lx1eEVgmspcZMAoGCCqGSM49BAMCA0kAMEYCIQDYcsOwU11G+fme3xD2EgCgE45qXt3Rg8nhJY2IuA2+3QIhALVJKhK8NfsJMONZo8amHvX43b2PuSWhmB3DHufEOnE0"
                )
            )
        )

        @OptIn(ExperimentalEncodingApi::class)
        val signer = X509CertificateUtil.parseCertificateDerEncoded(
            ByteString(
                Base64.decode(
                    "MIIB6zCCAZGgAwIBAgIIQAAAAAAAAAIwCgYIKoZIzj0EAwIwGjEYMBYGA1UEAwwPUklDQUwgVGVzdCBSb290MB4XDTI2MDgzMDIwNDIwNloXDTI2MDkyOTIwNDIwNlowSTELMAkGA1UEBhMCQVQxHjAcBgNVBAoMFXdhbHQuaWQgdGVzdCBmaXh0dXJlczEaMBgGA1UEAwwRUklDQUwgVGVzdCBTaWduZXIwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARtK4j0HidmssYZ3x9zeCEnQPrWct9tEeGqIrjLTJZomS7IbkM98UEC62dKFVWJi/AU+v7B2TamaU99V6Rem81No4GRMIGOMB0GA1UdDgQWBBSti6kphqKkS4k8rsZSMdVgQ2EhlzAfBgNVHSMEGDAWgBTTAjf37x6fIr2mtS8dXhFYJrKXGTAOBgNVHQ8BAf8EBAMCBkAwKgYDVR0fBCMwITAfoB2gG4YZaHR0cHM6Ly9yaWNhbC5leGFtcGxlL2NybDAQBgNVHSAECTAHMAUGAyoDBDAKBggqhkjOPQQDAgNIADBFAiEAkI4lWDQLobERLUoD9MjRf11cab4NLjcG8J1kZYwNxAYCIGGlfeY9tsoOj1GAfPJcd6QEB0LaySFQubeImAxEaARY"
                )
            )
        )
    }
}
