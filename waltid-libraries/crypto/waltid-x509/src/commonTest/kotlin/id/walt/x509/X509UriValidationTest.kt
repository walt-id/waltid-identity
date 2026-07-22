package id.walt.x509

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class X509UriValidationTest {
    @Test
    fun acceptsAbsoluteSubjectAlternativeNameUris() {
        X509SubjectAlternativeNames(uris = listOf("https://example.com/path", "urn:example:test", "did:example:123"))
    }

    @Test
    fun rejectsMalformedSubjectAlternativeNameUris() {
        listOf("relative/path", "https://", "https://user@example.com", "urn:with whitespace").forEach {
            assertFailsWith<IllegalArgumentException> { X509SubjectAlternativeNames(uris = listOf(it)) }
        }
    }

    @Test
    fun rejectsMalformedCrlDistributionPoint() = runTest {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("issuer"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )

        assertFailsWith<IllegalArgumentException> {
            GenericX509CertificateBuilder().buildDer(
                profileData = GenericX509CertificateProfileData(
                    subjectName = X509DistinguishedName(commonName = "Issuer"),
                    crlDistributionPointUri = "not-a-url",
                ),
                subjectPublicKey = key,
                signingKey = key,
                signatureAlgorithm = SignatureAlgorithm.Ecdsa(
                    DigestAlgorithm.SHA_256,
                    EcdsaSignatureEncoding.DER,
                ),
            )
        }
    }
}
