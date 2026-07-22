package id.walt.vical

import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
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
import id.walt.x509.GenericX509CertificateBuilder
import id.walt.x509.GenericX509CertificateProfileData
import id.walt.x509.X509DistinguishedName
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock

class Crypto2VicalTest {
    @Test
    fun `crypto2 signs verifies and resolves VICAL certificate key`() = runTest {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("vical-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val certificate = GenericX509CertificateBuilder().buildDer(
            profileData = GenericX509CertificateProfileData(
                subjectName = X509DistinguishedName(commonName = "VICAL signer"),
            ),
            subjectPublicKey = key,
            signingKey = key,
            signatureAlgorithm = SignatureAlgorithm.Ecdsa(
                DigestAlgorithm.SHA_256,
                EcdsaSignatureEncoding.DER,
            ),
        )
        val certificateInfo = CertificateInfo(
            certificate = certificate.bytes.toByteArray(),
            serialNumber = byteArrayOf(1),
            ski = byteArrayOf(2),
            docType = listOf("org.example.mdoc"),
        )
        val signed = Vical.createAndSign(
            vicalData = VicalData(
                vicalProvider = "example",
                date = Clock.System.now(),
                certificateInfos = listOf(certificateInfo),
            ),
            key = key,
            algorithmId = Cose.Algorithm.ES256,
            signerCertificateChain = listOf(CoseCertificate(certificate.bytes.toByteArray())),
        )

        assertTrue(signed.verify(key, setOf(Cose.Algorithm.ES256)))
        val certificateKey = certificateInfo.getCrypto2Key()
        assertTrue(signed.verify(certificateKey, setOf(Cose.Algorithm.ES256)))
        assertTrue(
            VicalService.validateVical(
                Base64.Default.encode(signed.toTaggedCbor()),
                certificateKey,
                setOf(Cose.Algorithm.ES256),
            ).vicalValid
        )
    }
}
