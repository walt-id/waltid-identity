@file:OptIn(ExperimentalEncodingApi::class)

package id.walt.x509

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class OrderedCertificateChainTest {

    @Test
    fun verifiesLeafSignedByIssuer() {
        verifyOrderedCertificateChainSignatures(listOf(leafCertificate, issuerCertificate))
    }

    @Test
    fun rejectsLeafWithTamperedSignature() {
        assertFailsWith<X509ValidationException> {
            verifyOrderedCertificateChainSignatures(listOf(tamperedLeafCertificate, issuerCertificate))
        }
    }

    @Test
    fun rejectsIssuerSubjectMismatch() {
        assertFailsWith<X509ValidationException> {
            verifyOrderedCertificateChainSignatures(listOf(issuerCertificate, leafCertificate))
        }
    }

    @Test
    fun extractsKeyIdentifiersFromCertificateDer() {
        assertContentEquals(
            expected = issuerCertificate.subjectKeyIdentifier,
            actual = leafCertificate.authorityKeyIdentifier,
        )
    }

    private companion object {
        private val leafCertificate = CertificateDer(
            Base64.decode(
                "MIIBuTCCAV+gAwIBAgIULVajycAnSGTLObP+/Dp1Wmy9UjwwCgYIKoZIzj0EAwIwJjELMAkGA1UEBhMCVVMxFzAVBgNVBAMMDldhbHQgVGVzdCBJQUNBMB4XDTI2MDYyOTEzNTcyMFoXDTM2MDYyNjEzNTcyMFowMTELMAkGA1UEBhMCVVMxIjAgBgNVBAMMGVdhbHQgVGVzdCBEb2N1bWVudCBTaWduZXIwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQSHV7PBODYnk4FjIcPMTFjcIX+L0EM9zPH97cB0zIkvKXk7zu3RIW1bXM9DgBzsSRCOmRQzKMOhgFoAx2jabT6o2AwXjAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB/wQEAwIHgDAdBgNVHQ4EFgQUoJB2sXVvq/2MhuS7cws0OL2o5xswHwYDVR0jBBgwFoAUsTrNBLUA5d4f/LE8POyPYLxiA3QwCgYIKoZIzj0EAwIDSAAwRQIgB1ikYok1QzN73w1yulCYIYCU0UqnbHrI3HFq8feJ4GMCIQDMYR+cvUX4nkFrNVOmlkyd1VW1Q3g5MSObOvYibekVcg=="
            )
        )

        private val issuerCertificate = CertificateDer(
            Base64.decode(
                "MIIBtDCCAVqgAwIBAgIUXeZ8TU7ctMGSpHGjMLm3MFeMtYgwCgYIKoZIzj0EAwIwJjELMAkGA1UEBhMCVVMxFzAVBgNVBAMMDldhbHQgVGVzdCBJQUNBMB4XDTI2MDYyOTEzNTcyMFoXDTM2MDYyNjEzNTcyMFowJjELMAkGA1UEBhMCVVMxFzAVBgNVBAMMDldhbHQgVGVzdCBJQUNBMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPjayd0F/HXWABJ8+G5IRlmAKyJlC4xf4aj5/iVbkF32u60EHD3EbMw3v4PAhb8mfSdDkrx3p4ObFEYpXquGc66NmMGQwEgYDVR0TAQH/BAgwBgEB/wIBADAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFLE6zQS1AOXeH/yxPDzsj2C8YgN0MB8GA1UdIwQYMBaAFLE6zQS1AOXeH/yxPDzsj2C8YgN0MAoGCCqGSM49BAMCA0gAMEUCIQDCvxqC6szjFRNUalcGoxE/DR7S+pA07tb8/ZnQMuQ6NQIgUpCsr/4qDaQmsGHGlMR2OpTgdu0moGKXNQD6dpqhy10="
            )
        )

        private val tamperedLeafCertificate = CertificateDer(
            leafCertificate.bytes.toByteArray().copyOf().also {
                it[it.lastIndex] = (it[it.lastIndex].toInt() xor 0x01).toByte()
            }
        )
    }
}
