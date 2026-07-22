package id.walt.crypto2.pkcs11

import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyStore
import java.security.MessageDigest
import java.security.AuthProvider
import java.security.Provider
import java.security.Security
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

internal class Pkcs11SessionFactory(
    private val pinResolver: Pkcs11PinResolver,
) {
    suspend fun open(options: Pkcs11Options): Pkcs11Session {
        require(Files.isRegularFile(Path.of(options.libraryPath))) {
            "PKCS11 library does not exist: ${options.libraryPath}"
        }
        val config = Files.createTempFile("waltid-pkcs11-", ".cfg")
        val provider = try {
            Files.writeString(config, options.sunPkcs11Configuration())
            requireNotNull(Security.getProvider("SunPKCS11")) { "SunPKCS11 provider is unavailable" }
                .configure(config.toString())
        } finally {
            Files.deleteIfExists(config)
        }
        val pin = pinResolver.resolve(options.pinReference).copy()
        val keyStore = try {
            KeyStore.getInstance("PKCS11", provider).apply { load(null, pin) }
        } finally {
            pin.fill('\u0000')
        }
        return Pkcs11Session(provider, keyStore)
    }
}

internal data class Pkcs11Session(
    val provider: Provider,
    val keyStore: KeyStore,
) : AutoCloseable {
    override fun close() {
        (provider as? AuthProvider)?.logout()
    }
}

internal fun createPkcs11Certificate(
    alias: String,
    spec: KeySpec,
    keyPair: KeyPair,
    provider: Provider,
): X509Certificate {
    val now = Instant.now()
    val name = X500Name("CN=${alias.replace("\\", "\\\\").replace(",", "\\,")}")
    val builder = JcaX509v3CertificateBuilder(
        name,
        BigInteger(160, SecureRandom()).abs(),
        Date.from(now.minus(1, ChronoUnit.MINUTES)),
        Date.from(now.plus(3650, ChronoUnit.DAYS)),
        name,
        keyPair.public,
    )
    val signer = if (spec is KeySpec.Ec) {
        object : ContentSigner {
            private val output = ByteArrayOutputStream()
            override fun getAlgorithmIdentifier() =
                DefaultSignatureAlgorithmIdentifierFinder().find(spec.certificateSignatureAlgorithm())

            override fun getOutputStream(): OutputStream = output

            override fun getSignature(): ByteArray {
                val digest = MessageDigest.getInstance(spec.certificateDigest()).digest(output.toByteArray())
                return Signature.getInstance("NONEwithECDSA", provider).apply {
                    initSign(keyPair.private)
                    update(digest)
                }.sign()
            }
        }
    } else {
        JcaContentSignerBuilder(spec.certificateSignatureAlgorithm())
            .setProvider(provider)
            .build(keyPair.private)
    }
    return JcaX509CertificateConverter()
        .setProvider(BouncyCastleProvider())
        .getCertificate(builder.build(signer))
}

private fun Pkcs11Options.sunPkcs11Configuration(): String = """
    name = Waltid${(libraryPath + slotListIndex).hashCode().toUInt()}
    library = $libraryPath
    slotListIndex = $slotListIndex
    attributes(generate, CKO_PRIVATE_KEY, *) = {
      CKA_TOKEN = true
      CKA_PRIVATE = true
      CKA_SENSITIVE = true
      CKA_EXTRACTABLE = false
    }
    attributes(generate, CKO_PUBLIC_KEY, *) = {
      CKA_TOKEN = true
    }
""".trimIndent()

private fun KeySpec.certificateSignatureAlgorithm(): String = when (this) {
    KeySpec.Ec(EcCurve.P256) -> "SHA256withECDSA"
    KeySpec.Ec(EcCurve.P384) -> "SHA384withECDSA"
    KeySpec.Ec(EcCurve.P521) -> "SHA512withECDSA"
    is KeySpec.Rsa -> "SHA256withRSA"
    else -> throw IllegalArgumentException("Unsupported PKCS11 certificate key specification: $this")
}

private fun KeySpec.Ec.certificateDigest(): String = when (curve) {
    EcCurve.P256 -> "SHA-256"
    EcCurve.P384 -> "SHA-384"
    EcCurve.P521 -> "SHA-512"
    else -> throw IllegalArgumentException("Unsupported PKCS11 EC curve: ${curve.name}")
}
