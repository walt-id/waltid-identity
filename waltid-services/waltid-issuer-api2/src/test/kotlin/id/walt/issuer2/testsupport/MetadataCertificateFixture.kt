package id.walt.issuer2.testsupport

import id.walt.openid4vci.metadata.issuer.signing.MetadataSigningMethod
import id.walt.crypto2.CryptoRuntime
import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.certificate.x509.extension.KeyUsageExtension.KeyUsage
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.KeySpec
import kotlinx.coroutines.runBlocking
import java.security.cert.CertificateFactory
import java.security.interfaces.ECPublicKey
import java.security.interfaces.EdECPublicKey
import java.security.interfaces.RSAPublicKey
import javax.security.auth.x500.X500Principal
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage as CryptoKeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.decodePrivateKeyPem
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.certificate.x509.X509CertificateUtil
import kotlinx.io.bytestring.ByteString
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64

/** Generates fresh synthetic material. No deployment key or certificate is used by tests. */
class MetadataCertificateFixture(
    val now: Instant = Instant.now(),
    val notBefore: Instant = now.minusSeconds(60),
    val notAfter: Instant = now.plusSeconds(3600),
    leafUsage: KeyUsage? = KeyUsage.digitalSignature,
    intermediateIsCa: Boolean = true,
    intermediateUsage: KeyUsage = KeyUsage.keyCertSign,
    curve: String = "secp256r1",
    providedKeyPair: KeyPair? = null,
) {
    val keyPair: KeyPair = providedKeyPair ?: generateKey(curve)
    private val rootKey = generateKey()
    private val intermediateKey = generateKey()
    val root = certificate("Root", "Root", rootKey, rootKey, true, KeyUsage.keyCertSign)
    val intermediate = certificate("Intermediate", "Root", intermediateKey, rootKey, intermediateIsCa, intermediateUsage)
    val leaf = certificate("Metadata", "Intermediate", keyPair, intermediateKey, false, leafUsage)
    val chain = listOf(leaf, intermediate)

    suspend fun crypto2SigningKey() = CryptoRuntime(defaultSoftwareKeyProviders()).let { runtime ->
        val publicKey = X509CertificateUtil.parseCertificateDerEncoded(ByteString(leaf.encoded)).restoreSubjectPublicKey(runtime)
        runtime.restore(StoredKey.Software(
            version = StoredKey.CURRENT_VERSION,
            id = KeyId("kms/metadata-test-key"),
            spec = publicKey.spec,
            usages = setOf(CryptoKeyUsage.SIGN),
            material = pem("PRIVATE KEY", keyPair.private.encoded).decodePrivateKeyPem(),
        ))
    }

    fun config(certificates: List<X509Certificate> = chain, privateKey: KeyPair = keyPair) =
        MetadataSigningMethod.X509Chain(
            privateKeyPem = pem("PRIVATE KEY", privateKey.private.encoded),
            certificateChainPem = certificates.map { pem("CERTIFICATE", it.encoded) },
        )

    private fun certificate(
        subject: String, issuer: String, subjectKey: KeyPair, issuerKey: KeyPair, ca: Boolean, usage: KeyUsage?,
    ): X509Certificate = runBlocking {
        // Use the walt.id builder directly so negative tests can issue a leaf under an
        // intermediate with deliberately invalid CA/key-usage constraints.
        val builder = X509CertificateDataBuilder(
            serialNumberGenerator = X509CertificateUtil.services.serialNumberGenerator,
            issuerDnRaw = ByteString(X500Principal("CN=$issuer").encoded),
            subjectDnRaw = ByteString(X500Principal("CN=$subject").encoded),
            validity = id.walt.certificate.x509.X509Certificate.Validity(
                kotlin.time.Instant.fromEpochMilliseconds(notBefore.toEpochMilli()),
                kotlin.time.Instant.fromEpochMilliseconds(notAfter.toEpochMilli()),
            ),
        ).apply {
            subjectPublicKey(restoreKey(subjectKey))
            extensionBasicConstraints { critical = true; cA = ca }
            usage?.let { extensionKeyUsage { critical = true; addKeyUsage(it) } }
        }
        val certificate = X509CertificateUtil.services.certificateSigner.signCertificate(
            restoreKey(issuerKey),
            SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
            builder,
        )
        // JCA parses and verifies independently of the walt.id certificate/JWT APIs.
        CertificateFactory.getInstance("X.509").generateCertificate(
            certificate.encodedDer.toByteArray().inputStream(),
        ) as X509Certificate
    }

    private suspend fun restoreKey(pair: KeyPair) = CryptoRuntime(defaultSoftwareKeyProviders()).restore(
        StoredKey.Software(
            version = StoredKey.CURRENT_VERSION,
            id = KeyId("fixture-key"),
            spec = when (val public = pair.public) {
                is ECPublicKey -> KeySpec.Ec(when (public.params.curve.field.fieldSize) {
                    256 -> EcCurve.P256
                    384 -> EcCurve.P384
                    521 -> EcCurve.P521
                    else -> error("Unsupported fixture EC curve")
                })
                is RSAPublicKey -> KeySpec.Rsa(public.modulus.bitLength())
                is EdECPublicKey -> KeySpec.Edwards(when (public.params.name) {
                    "Ed25519" -> EdwardsCurve.ED25519
                    "Ed448" -> EdwardsCurve.ED448
                    else -> error("Unsupported fixture Edwards curve")
                })
                else -> error("Unsupported fixture key type")
            },
            usages = setOf(CryptoKeyUsage.SIGN, CryptoKeyUsage.VERIFY),
            material = pem("PRIVATE KEY", pair.private.encoded).decodePrivateKeyPem(),
        ),
    )

    companion object {
        fun generateKey(curve: String = "secp256r1"): KeyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec(curve))
        }.generateKeyPair()

        fun pem(label: String, der: ByteArray): String =
            "-----BEGIN $label-----\n${Base64.getEncoder().encodeToString(der).chunked(64).joinToString("\n")}\n-----END $label-----\n"

        fun verifies(jwt: String, certificate: X509Certificate, algorithm: String = "SHA256withECDSAinP1363Format"): Boolean = Signature.getInstance(algorithm).run {
            initVerify(certificate.publicKey)
            update(jwt.substringBeforeLast('.').toByteArray(Charsets.US_ASCII))
            verify(Base64.getUrlDecoder().decode(jwt.substringAfterLast('.')))
        }
    }
}
