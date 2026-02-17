package id.walt.x509.iso.iaca

import id.walt.x509.CertificateDer
import id.walt.x509.iso.iaca.parser.IACACertificateParser
import id.walt.x509.iso.iaca.validate.IACAValidator
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.decodeHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IACACertificateInfoVectorsMPTest {

    @Test
    fun `certificate info extras match known PEM vectors`() = runTest {
        vectors.forEach { vector ->

            val decodedCertificate = parser.parse(
                certificate = CertificateDer.fromPEMEncodedString(vector.pem),
            )

            //just to make sure that an invalid PEM encoded certificate is not added by mistake
            validator.validate(decodedCertificate)

            val info = decodedCertificate.toIacaCertificateInfo()

            assertEquals(
                expected = vector.issuingAuthority,
                actual = info.issuingAuthority,
            )

            val issuer = assertNotNull(info.issuer)
            val subject = assertNotNull(info.subject)

            assertEquals(
                expected = vector.issuerHex.decodeHex(),
                actual = issuer,
            )
            assertEquals(
                expected = vector.subjectHex.decodeHex(),
                actual = subject,
            )

        }
    }

    private data class IacaCertificateInfoVector(
        val pem: String,
        val issuingAuthority: String,
        val issuerHex: String,
        val subjectHex: String,
    )

    private companion object {
        private val parser = IACACertificateParser()
        private val validator = IACAValidator()

        private val vectors = listOf(
            IacaCertificateInfoVector(
                pem = """
                    -----BEGIN CERTIFICATE-----
                    MIIC/TCCAqSgAwIBAgIUBAlgS9FXMuQksnLjqOTIVlYurbwwCgYIKoZIzj0EAwIw
                    gbAxCzAJBgNVBAYTAkdSMVowWAYDVQQDDFHOlyDOus6xzrvPhc+EzrXPgc+Mz4TO
                    tc+BzrcgzrHPgc+Hzq4gz4DOuc+Dz4TOv8+Azr/Or863z4POt8+CIM+Dz4TOv869
                    IM66z4zPg868zr8xFTATBgNVBAgMDM6Rz4TPhM65zrrOrjEuMCwGA1UECgwlzqXP
                    gM6/z4XPgc6zzrXOr86/IM6czrXPhM6xz4bOv8+Bz47OvTAeFw0yNTA1MjgxMjIz
                    MDFaFw00MDA1MjQxMjIzMDFaMIGwMQswCQYDVQQGEwJHUjFaMFgGA1UEAwxRzpcg
                    zrrOsc67z4XPhM61z4HPjM+EzrXPgc63IM6xz4HPh86uIM+AzrnPg8+Ezr/PgM6/
                    zq/Ot8+DzrfPgiDPg8+Ezr/OvSDOus+Mz4POvM6/MRUwEwYDVQQIDAzOkc+Ez4TO
                    uc66zq4xLjAsBgNVBAoMJc6lz4DOv8+Fz4HOs861zq/OvyDOnM61z4TOsc+Gzr/P
                    gc+Ozr0wWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASrTGLbW652GQLVAzKR9ivI
                    31twPHjzSIktJpEjTkJaBjYQ/tPcaq1IBNvqkrfIOYpnj4CjzzVaWKB5rEy9n+Iq
                    o4GZMIGWMB0GA1UdDgQWBBSYrxEhOTqG8/lCsR8IV8pI0hVaODASBgNVHRMBAf8E
                    CDAGAQH/AgEAMCMGA1UdEgQcMBqGGGh0dHBzOi8vaWFjYS5leGFtcGxlLmNvbTAO
                    BgNVHQ8BAf8EBAMCAQYwLAYDVR0fBCUwIzAhoB+gHYYbaHR0cHM6Ly9jcmwuZ292
                    LmdyL2lhY2EuY3JsMAoGCCqGSM49BAMCA0cAMEQCIFPWHi68eADZxb8fid1vWBKt
                    pb5ucDMPXAP6IZxcvLMqAiB8vwu0dPhMJ20Bl0LWfM1h/jZa27o9Vm6YmjtNdMna
                    SA==
                    -----END CERTIFICATE-----
                """.trimIndent(),
                issuingAuthority = "C=GR,CN=Η καλυτερότερη αρχή πιστοποίησης στον κόσμο,ST=Αττική,O=Υπουργείο Μεταφορών",
                issuerHex = "3081b0310b3009060355040613024752315a305806035504030c51ce9720cebaceb1cebbcf85cf84ceb5cf81cf8ccf84ceb5cf81ceb720ceb1cf81cf87ceae20cf80ceb9cf83cf84cebfcf80cebfceafceb7cf83ceb7cf8220cf83cf84cebfcebd20cebacf8ccf83cebccebf3115301306035504080c0cce91cf84cf84ceb9cebaceae312e302c060355040a0c25cea5cf80cebfcf85cf81ceb3ceb5ceafcebf20ce9cceb5cf84ceb1cf86cebfcf81cf8ecebd",
                subjectHex = "3081b0310b3009060355040613024752315a305806035504030c51ce9720cebaceb1cebbcf85cf84ceb5cf81cf8ccf84ceb5cf81ceb720ceb1cf81cf87ceae20cf80ceb9cf83cf84cebfcf80cebfceafceb7cf83ceb7cf8220cf83cf84cebfcebd20cebacf8ccf83cebccebf3115301306035504080c0cce91cf84cf84ceb9cebaceae312e302c060355040a0c25cea5cf80cebfcf85cf81ceb3ceb5ceafcebf20ce9cceb5cf84ceb1cf86cebfcf81cf8ecebd",
            ),
            IacaCertificateInfoVector(
                pem = """
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
                """.trimIndent(),
                issuingAuthority = "C=US,CN=Example IACA",
                issuerHex = "3024310b30090603550406130255533115301306035504030c0c4578616d706c652049414341",
                subjectHex = "3024310b30090603550406130255533115301306035504030c0c4578616d706c652049414341",
            ),
        )
    }
}
