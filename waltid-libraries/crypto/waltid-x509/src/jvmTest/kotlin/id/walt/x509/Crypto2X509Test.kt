package id.walt.x509

import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.x509.iso.IssuerAlternativeName
import id.walt.x509.iso.documentsigner.builder.Crypto2IACASignerSpecification
import id.walt.x509.iso.documentsigner.builder.DocumentSignerCertificateBuilder
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerCertificateProfileData
import id.walt.x509.iso.documentsigner.certificate.DocumentSignerPrincipalName
import id.walt.x509.iso.documentsigner.parser.DocumentSignerCertificateParser
import id.walt.x509.iso.iaca.builder.IACACertificateBuilder
import id.walt.x509.iso.iaca.certificate.IACACertificateProfileData
import id.walt.x509.iso.iaca.certificate.IACAPrincipalName
import id.walt.x509.iso.iaca.parser.IACACertificateParser
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.security.AlgorithmParameters
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class Crypto2X509Test {
    private val provider = CryptographySoftwareKeyProvider()

    @Test
    fun `crypto2 ECDSA builds verifiable certificate and CSR`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P256))
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        val subjectName = X509DistinguishedName(
            commonName = "Crypto2 EC",
            country = "AT",
            organizationName = "walt.id",
        )
        val certificateDer = GenericX509CertificateBuilder().buildDer(
            profileData = GenericX509CertificateProfileData(
                subjectName = subjectName,
                isCertificateAuthority = true,
                keyUsage = setOf(X509KeyUsage.KeyCertSign, X509KeyUsage.CRLSign),
            ),
            subjectPublicKey = key,
            signingKey = key,
            signatureAlgorithm = algorithm,
        )
        val certificate = certificateDer.toJcaX509Certificate()

        certificate.verify(certificate.publicKey)
        assertEquals("1.2.840.10045.4.3.2", certificate.sigAlgOID)
        assertFalse(certificateDer.crypto2PublicJwk().privateMaterial)

        val csrDer = CertificateSigningRequestBuilder().buildDer(
            profileData = CertificateSigningRequestProfileData(
                subjectName = subjectName,
                subjectAlternativeNames = X509SubjectAlternativeNames(dnsNames = listOf("crypto2.example")),
            ),
            signingKey = key,
            signatureAlgorithm = algorithm,
        )
        val parsed = parseCertificateSigningRequest(csrDer)
        assertEquals(subjectName.commonName, parsed.subjectName.commonName)
        assertEquals(listOf("crypto2.example"), assertNotNull(parsed.subjectAlternativeNames).dnsNames)
        assertEquals(
            Json.parseToJsonElement(csrDer.crypto2PublicJwk().data.toByteArray().decodeToString()),
            Json.parseToJsonElement(parsed.crypto2PublicKey().data.toByteArray().decodeToString()),
        )
    }

    @Test
    fun `crypto2 RSA emits matching PKCS1 and PSS algorithm identifiers`() = runTest {
        val key = generate(KeySpec.Rsa(2048))
        val algorithms = listOf(
            SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256) to "1.2.840.113549.1.1.11",
            SignatureAlgorithm.RsaPss(DigestAlgorithm.SHA_256, saltLengthBytes = 32) to "1.2.840.113549.1.1.10",
        )

        algorithms.forEach { (algorithm, expectedOid) ->
            val certificate = GenericX509CertificateBuilder().buildDer(
                profileData = GenericX509CertificateProfileData(
                    subjectName = X509DistinguishedName(commonName = "Crypto2 RSA"),
                ),
                subjectPublicKey = key,
                signingKey = key,
                signatureAlgorithm = algorithm,
            ).toJcaX509Certificate()

            certificate.verify(certificate.publicKey)
            assertEquals(expectedOid, certificate.sigAlgOID)
            if (algorithm is SignatureAlgorithm.RsaPss) {
                val parameters = AlgorithmParameters.getInstance("RSASSA-PSS").apply {
                    init(certificate.sigAlgParams)
                }.getParameterSpec(PSSParameterSpec::class.java)
                assertEquals("SHA-256", parameters.digestAlgorithm)
                assertEquals(MGF1ParameterSpec.SHA256, parameters.mgfParameters)
                assertEquals(32, parameters.saltLength)
            }
        }
    }

    @Test
    fun `X509 rejects non-DER ECDSA and inconsistent PSS parameters`() = runTest {
        val ec = generate(KeySpec.Ec(EcCurve.P256))
        val rsa = generate(KeySpec.Rsa(2048))
        val profile = GenericX509CertificateProfileData(
            subjectName = X509DistinguishedName(commonName = "Invalid X509"),
        )

        assertFailsWith<IllegalArgumentException> {
            GenericX509CertificateBuilder().buildDer(
                profile,
                ec,
                ec,
                SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.IEEE_P1363),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GenericX509CertificateBuilder().buildDer(
                profile,
                rsa,
                rsa,
                SignatureAlgorithm.RsaPss(
                    digest = DigestAlgorithm.SHA_256,
                    mgfDigest = DigestAlgorithm.SHA_384,
                    saltLengthBytes = 32,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GenericX509CertificateBuilder().buildDer(
                profile,
                rsa,
                rsa,
                SignatureAlgorithm.RsaPss(DigestAlgorithm.SHA_256),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GenericX509CertificateBuilder().buildDer(
                profile,
                rsa,
                rsa,
                SignatureAlgorithm.RsaPss(DigestAlgorithm.SHA_256, saltLengthBytes = 20),
            )
        }
    }

    @Test
    fun `crypto2 builds ISO IACA and Document Signer certificates`() = runTest {
        val iacaKey = generate(KeySpec.Ec(EcCurve.P256))
        val documentSignerKey = generate(KeySpec.Ec(EcCurve.P256))
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        val now = Clock.System.now()
        val iacaProfile = IACACertificateProfileData(
            principalName = IACAPrincipalName(country = "AT", commonName = "Crypto2 IACA"),
            validityPeriod = X509ValidityPeriod(now - 1.days, now + 365.days),
            issuerAlternativeName = IssuerAlternativeName(uri = "https://iaca.example"),
        )
        val iacaDer = IACACertificateBuilder().buildDer(iacaProfile, iacaKey, algorithm)
        val iacaCertificate = iacaDer.toJcaX509Certificate()
        iacaCertificate.verify(iacaCertificate.publicKey)
        assertFalse(IACACertificateParser().parse(iacaDer).crypto2PublicKey.privateMaterial)

        val documentSignerDer = DocumentSignerCertificateBuilder().buildDer(
            profileData = DocumentSignerCertificateProfileData(
                principalName = DocumentSignerPrincipalName(country = "AT", commonName = "Crypto2 DS"),
                validityPeriod = X509ValidityPeriod(now, now + 30.days),
                crlDistributionPointUri = "https://iaca.example/crl",
            ),
            publicKey = documentSignerKey,
            iacaSignerSpec = Crypto2IACASignerSpecification(iacaProfile, iacaKey, algorithm),
        )
        documentSignerDer.toJcaX509Certificate().verify(iacaCertificate.publicKey)
        assertFalse(
            DocumentSignerCertificateParser().parse(documentSignerDer).crypto2PublicKey.privateMaterial
        )
    }

    private suspend fun generate(spec: KeySpec): SoftwareKey = provider.generate(
        GenerateSoftwareKeyRequest(
            id = KeyId("x509-${spec.hashCode()}"),
            spec = spec,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )
}
