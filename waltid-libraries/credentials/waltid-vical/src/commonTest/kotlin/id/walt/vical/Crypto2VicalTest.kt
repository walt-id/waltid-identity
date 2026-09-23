package id.walt.vical

import id.walt.certificate.x509.X509CertificateUtil
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
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock

class Crypto2VicalTest {
    @Test
    fun `crypto2 signs verifies and resolves VICAL certificate key`() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("vical-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val sigAlg = SignatureAlgorithm.Ecdsa(
            DigestAlgorithm.SHA_256,
            EcdsaSignatureEncoding.DER,
        )
        val certificate = X509CertificateUtil.createSelfSignedCertificate(key, sigAlg) {
            subjectDn = "CN=VICAL signer"
        }
        val certificateInfo = CertificateInfo(
            certificate = certificate.encodedDer.toByteArray(),
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
            signerCertificateChain = listOf(CoseCertificate(certificate.encodedDer.toByteArray())),
        )

        assertTrue(signed.verify(key, setOf(Cose.Algorithm.ES256)))
        val certificateKey = certificateInfo.getCrypto2Key()
        assertTrue(signed.verify(certificateKey, setOf(Cose.Algorithm.ES256)))
        assertTrue(
            VicalService.validateVical(
                Base64.encode(signed.toTaggedCbor()),
                certificateKey,
                setOf(Cose.Algorithm.ES256),
            ).vicalValid
        )
    }
}
