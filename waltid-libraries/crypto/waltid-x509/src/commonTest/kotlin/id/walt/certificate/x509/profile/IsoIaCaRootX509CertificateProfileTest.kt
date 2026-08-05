package id.walt.certificate.x509.profile

import id.walt.certificate.TestData.intermediateIssuerKeyPem
import id.walt.certificate.TestKeys.opensslHexFormat
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.IssuerAlternativeNameExtension.Companion.extensionIssuerAltName
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import id.walt.certificate.x509.model.GeneralName
import id.walt.certificate.x509.profile.IsoIaCaRootX509CertificateProfile.profileIaCaRootCertificate
import id.walt.certificate.x509.validation.ValidationResult
import id.walt.certificate.x509.validation.X509SingleCertificateValidator
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.x509.iso.IsoSharedTestHarnessValidResources
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.toHexString
import kotlin.test.*

class IsoIaCaRootX509CertificateProfileTest {

    @Test
    fun shouldCreateIaCaRootCertificate() = runTest {
        val key = JWKKey.importPEM(intermediateIssuerKeyPem).getOrThrow()
        val cert = X509CertificateUtil.createSelfSignedCertificate(key) {
            profileIaCaRootCertificate(
                issuerDnCountryCode = "at",
                issuerDnOrganizationName = "Walt ID",
                issuerDnCommonName = "walt.id",
                issuerDnSerialNumber = "1234567",
                issuerEmailAddress = "office@walt.id"
            )
        }
        assertEquals("CN=walt.id+SERIALNUMBER=1234567,O=Walt ID,C=AT", cert.data.subjectDn)
        assertEquals("CN=walt.id+SERIALNUMBER=1234567,O=Walt ID,C=AT", cert.data.issuerDn)
        val validationResult = validator.validate(cert)
        if (!validationResult.valid) {
            validationResult.log.forEach { println(it) }
        }
        assertTrue(validationResult.valid)
        assertFalse(
            validationResult.hasWarnings,
            "Warnings: ${validationResult.log.filter { it.severity == ValidationResult.Severity.WARNING }}"
        )
        assertFalse(validationResult.hasErrors)
    }

    @Test
    fun shouldFindIllegalIssuerDnCountryCodeInIaCaRootCertificate() = runTest {
        val key = JWKKey.importPEM(intermediateIssuerKeyPem).getOrThrow()
        val cert = X509CertificateUtil.createSelfSignedCertificate(key) {
            profileIaCaRootCertificate(
                issuerDn = "cn=Walt ID,C=Austria",
                issuerEmailAddress = "office@walt.id",
                issuerUri = "https://walt.id"
            )
        }
        assertEquals("CN=Walt ID,C=Austria", cert.data.subjectDn)
        assertEquals("CN=Walt ID,C=Austria", cert.data.issuerDn)
        val validationResult = validator.validate(cert)

        assertFalse(
            validationResult.hasWarnings,
            "Warnings: ${validationResult.log.filter { it.severity == ValidationResult.Severity.WARNING }}"
        )
        assertTrue(validationResult.hasErrors)
        assertTrue(validationResult.log.any {
            it.severity == ValidationResult.Severity.ERROR
                    && it.validatorId == "iso-iaca-root.issuerDn"
        })
        assertFalse(validationResult.valid)
    }


    @Test
    fun `build should succeed when IACA signing key is of valid keyType`() = runTest {
        IsoSharedTestHarnessValidResources
            .iacaSigningKeyMap()
            .values
            .forEach { validSigningKey ->
                val caCert = X509CertificateUtil.createSelfSignedCertificate(validSigningKey) {
                    profileIaCaRootCertificate(
                        issuerEmailAddress = "iaca@example.com",
                        issuerUri = "https://iaca.example.com",
                        issuerDnCountryCode = "US",
                        issuerDnCommonName = "Example IACA",
                    )
                }
                assertIaCaCertificateData(
                    "CN=Example IACA,C=US",
                    listOf(
                        GeneralName(GeneralName.NameType.rfc822Name, "iaca@example.com"),
                        GeneralName(GeneralName.NameType.uniformResourceIdentifier, "https://iaca.example.com"),
                    ),
                    caCert
                )
            }
    }

    @Test
    fun `build should be safe when called concurrently`() = runTest {
        val signingKey = IsoSharedTestHarnessValidResources.iacaSecp256r1SigningKey()
        val bundles = List(20) { index ->
            async {
                X509CertificateUtil.createSelfSignedCertificate(signingKey) {
                    profileIaCaRootCertificate(
                        issuerEmailAddress = "iaca@example.com",
                        issuerUri = "https://iaca.example.com",
                        issuerDnCountryCode = "US",
                        issuerDnCommonName = "Example IACA ${index}",
                    )
                }
            }
        }.awaitAll()

        assertTrue {
            bundles.all { it.encodedDer.size != 0 }
        }
        //all serial numbers are unique -> hence all generated certificates different
        assertEquals(
            bundles.size,
            bundles.map { it.data.serialNumberRaw }.toSet().size,
            "Not all serial numbers are unique."
        )
    }

    @Test
    fun `Validation should fail when IACA signing key is of invalid keyType`() = runTest {
        listOf(
            //KeyType.Ed25519, TODO: enable Ed25519 ... export PEM is not supported
            KeyType.RSA,
            KeyType.RSA3072,
            KeyType.RSA4096,
            KeyType.secp256k1,
        ).forEach { invalidKeyType ->
            val key = JWKKey.generate(invalidKeyType)
            runCatching {
                X509CertificateUtil.createSelfSignedCertificate(key) {
                    profileIaCaRootCertificate(
                        issuerEmailAddress = "illegal.key@example.com",
                        issuerUri = "https://illegal-key.iaca.example.com",
                        issuerDnCountryCode = "US",
                        issuerDnCommonName = "Example IACA Illegal Key",
                    )
                }
            }.getOrElse {
                if (it.message?.contains("Curve not supported") == true) {
                    println("${invalidKeyType}: '${it.message}'")
                    //Signum implementation doesn't support secp256k1 curve, so we expect this error.
                    assertEquals(KeyType.secp256k1, invalidKeyType)
                    null
                } else {
                    throw it
                }
            }?.let { cert ->
                val result = validator.validate(cert)
                assertTrue(
                    invalidKeyType == KeyType.secp256k1 || //EC is allowed but not the curve
                            result.log.any { it.validatorId == "iso-iaca-root.signatureAlgorithm" && it.severity == ValidationResult.Severity.ERROR },
                    "Key: ${invalidKeyType}"
                )
                assertTrue(result.log.any { it.validatorId == "iso-iaca-root.subjectPublicKeyInfo" && it.severity == ValidationResult.Severity.ERROR })
            }
        }
    }

    @Test
    fun shouldValidateCertificateFromLoadedPem() {
        val pem = """
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
            """.trimIndent()
        val issuingAuthority = "C=GR,CN=Η καλυτερότερη αρχή πιστοποίησης στον κόσμο,ST=Αττική,O=Υπουργείο Μεταφορών"
        val principalDnRawHex =
            "3081b0310b3009060355040613024752315a305806035504030c51ce9720cebaceb1cebbcf85cf84ceb5cf81cf8ccf84ceb5cf81ceb720ceb1cf81cf87ceae20cf80ceb9cf83cf84cebfcf80cebfceafceb7cf83ceb7cf8220cf83cf84cebfcebd20cebacf8ccf83cebccebf3115301306035504080c0cce91cf84cf84ceb9cebaceae312e302c060355040a0c25cea5cf80cebfcf85cf81ceb3ceb5ceafcebf20ce9cceb5cf84ceb1cf86cebfcf81cf8ecebd"
        val subjectHex =
            "98:AF:11:21:39:3A:86:F3:F9:42:B1:1F:08:57:CA:48:D2:15:5A:38"

        val cert = X509CertificateUtil.parseCertificatePem(pem)

        assertEquals(
            principalDnRawHex,
            cert.data.issuerDnRaw.toHexString()
        )

        assertEquals(
            principalDnRawHex,
            cert.data.subjectDnRaw.toHexString()
        )

        assertIaCaCertificateData(issuingAuthority, null, cert)
        assertNotNull(cert.data.extensionSubjectKeyIdentifier) { skid ->
            assertEquals(subjectHex, skid.keyIdentifier.toHexString(opensslHexFormat))
        }
    }

    @Test
    fun shouldValidateCertificateFromLoadedPem2() {
        val pem = """
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
        val issuingAuthority = "C=US,CN=Example IACA"
        val principalDnRawHex = "3024310b30090603550406130255533115301306035504030c0c4578616d706c652049414341"
        val subjectHex = "98:AF:11:21:39:3A:86:F3:F9:42:B1:1F:08:57:CA:48:D2:15:5A:38"

        val cert = X509CertificateUtil.parseCertificatePem(pem)

        assertEquals(
            principalDnRawHex,
            cert.data.issuerDnRaw.toHexString()
        )
        assertEquals(
            principalDnRawHex,
            cert.data.subjectDnRaw.toHexString()
        )

        assertIaCaCertificateData(issuingAuthority, null, cert)
        assertNotNull(cert.data.extensionSubjectKeyIdentifier) { skid ->
            assertEquals(subjectHex, skid.keyIdentifier.toHexString(opensslHexFormat))
        }
    }


    companion object {
        val validator = X509SingleCertificateValidator(listOf(IsoIaCaRootX509CertificateProfile))

        fun assertIaCaCertificateData(
            expectedPrincipalDn: String,
            expectedIssuerAltNames: List<GeneralName>?,
            cert: X509Certificate,
        ) {

            assertEquals(expectedPrincipalDn, cert.data.subjectDn)
            assertEquals(expectedPrincipalDn, cert.data.issuerDn)
            assertNotNull(cert.data.extensionBasicConstraints).also { bc ->
                assertTrue(bc.cA)
                assertEquals(0, bc.pathLenConstraint)
            }

            expectedIssuerAltNames?.also { expIssAltNames ->
                assertNotNull(cert.data.extensionIssuerAltName).also { issAltNames ->
                    assertEquals(expIssAltNames, issAltNames.alternativeNames)
                }
            }

            assertNotNull(cert.data.extensionKeyUsage).also { ku ->
                assertTrue(ku.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.cRLSign))
                assertTrue(ku.keyPurposeIdList.contains(KeyUsageExtension.KeyUsage.keyCertSign))
            }
        }
    }
}