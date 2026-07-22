package id.walt.x509

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.Key
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CertificateIssuerConstraintsTest {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
    private val signatureAlgorithm = SignatureAlgorithm.Ecdsa(
        DigestAlgorithm.SHA_256,
        EcdsaSignatureEncoding.DER,
    )

    @Test
    fun `end entity certificate cannot act as intermediate`() = runTest {
        val rootKey = key("root")
        val rootName = X509DistinguishedName(commonName = "Root CA")
        val root = certificate(
            subjectName = rootName,
            subjectKey = rootKey,
            signingKey = rootKey,
            isCa = true,
            pathLengthConstraint = 1,
        )
        val intermediateKey = key("intermediate")
        val intermediateName = X509DistinguishedName(commonName = "Not a CA")
        val intermediate = certificate(
            subjectName = intermediateName,
            issuerName = rootName,
            subjectKey = intermediateKey,
            signingKey = rootKey,
            isCa = false,
        )
        val leafKey = key("leaf")
        val leaf = certificate(
            subjectName = X509DistinguishedName(commonName = "Leaf"),
            issuerName = intermediateName,
            subjectKey = leafKey,
            signingKey = intermediateKey,
            isCa = false,
        )

        assertFailsWith<X509ValidationException> {
            validateCertificateChainWithExplicitTrust(
                leaf = leaf,
                chain = listOf(intermediate),
                trustAnchors = listOf(root),
                enableTrustedChainRoot = false,
            )
        }
    }

    @Test
    fun `CA certificate with keyCertSign can act as intermediate`() = runTest {
        val rootKey = key("valid-root")
        val rootName = X509DistinguishedName(commonName = "Valid Root")
        val root = certificate(rootName, subjectKey = rootKey, signingKey = rootKey, isCa = true, pathLengthConstraint = 1)
        val intermediateKey = key("valid-intermediate")
        val intermediateName = X509DistinguishedName(commonName = "Valid Intermediate")
        val intermediate = certificate(
            subjectName = intermediateName,
            issuerName = rootName,
            subjectKey = intermediateKey,
            signingKey = rootKey,
            isCa = true,
            pathLengthConstraint = 0,
        )
        val leaf = certificate(
            subjectName = X509DistinguishedName(commonName = "Valid Leaf"),
            issuerName = intermediateName,
            subjectKey = key("valid-leaf"),
            signingKey = intermediateKey,
            isCa = false,
        )

        validateCertificateChainWithExplicitTrust(
            leaf = leaf,
            chain = listOf(intermediate),
            trustAnchors = listOf(root),
            enableTrustedChainRoot = false,
        )
    }

    @Test
    fun `intermediate EKU must permit required client authentication purpose`() = runTest {
        val rootKey = key("eku-root")
        val rootName = X509DistinguishedName(commonName = "EKU Root")
        val root = certificate(rootName, subjectKey = rootKey, signingKey = rootKey, isCa = true, pathLengthConstraint = 1)
        val intermediateKey = key("eku-intermediate")
        val intermediateName = X509DistinguishedName(commonName = "Server Only Intermediate")
        val intermediate = certificate(
            subjectName = intermediateName,
            issuerName = rootName,
            subjectKey = intermediateKey,
            signingKey = rootKey,
            isCa = true,
            pathLengthConstraint = 0,
            extendedKeyUsageOids = setOf("1.3.6.1.5.5.7.3.1"),
        )
        val leaf = certificate(
            subjectName = X509DistinguishedName(commonName = "Client Leaf"),
            issuerName = intermediateName,
            subjectKey = key("eku-leaf"),
            signingKey = intermediateKey,
            isCa = false,
            extendedKeyUsageOids = setOf("1.3.6.1.5.5.7.3.2"),
        )

        assertFailsWith<X509ValidationException> {
            validateCertificateChainWithExplicitTrust(
                leaf = leaf,
                chain = listOf(intermediate),
                trustAnchors = listOf(root),
                enableTrustedChainRoot = false,
                requiredExtendedKeyUsageOid = "1.3.6.1.5.5.7.3.2",
            )
        }
    }

    private suspend fun key(id: String) = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )

    private suspend fun certificate(
        subjectName: X509DistinguishedName,
        issuerName: X509DistinguishedName? = null,
        subjectKey: Key,
        signingKey: Key,
        isCa: Boolean,
        pathLengthConstraint: Int? = null,
        extendedKeyUsageOids: Set<String> = emptySet(),
    ): CertificateDer = GenericX509CertificateBuilder().buildDer(
        profileData = GenericX509CertificateProfileData(
            subjectName = subjectName,
            issuerName = issuerName,
            isCertificateAuthority = isCa,
            pathLengthConstraint = pathLengthConstraint,
            extendedKeyUsageOids = extendedKeyUsageOids,
            keyUsage = if (isCa) {
                setOf(X509KeyUsage.KeyCertSign, X509KeyUsage.CRLSign)
            } else setOf(X509KeyUsage.DigitalSignature),
        ),
        subjectPublicKey = subjectKey,
        signingKey = signingKey,
        signatureAlgorithm = signatureAlgorithm,
    )
}
