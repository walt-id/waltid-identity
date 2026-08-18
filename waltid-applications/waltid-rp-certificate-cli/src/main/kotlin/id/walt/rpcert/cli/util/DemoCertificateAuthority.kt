package id.walt.rpcert.cli.util

import com.nimbusds.jose.jwk.JWK
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.Date

/**
 * Generates an ephemeral X.509 chain (root CA -> leaf) around a freshly generated walt.id [JWKKey],
 * as a throwaway stand-in for a real Wallet-Relying Party access certificate chain. For `--generate-demo-ca`
 * only - never use this for anything that needs a trusted registrar.
 */
object DemoCertificateAuthority {

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    data class GeneratedChain(
        /** Private signing key of the relying party (matches the leaf certificate). */
        val signingKey: JWKKey,
        /** Base64 (not base64url) DER certificates, leaf first — the JWT x5c header value. */
        val x5c: List<String>,
        /** DER bytes of the root CA certificate (the trust anchor). */
        val rootCertificateDer: ByteArray,
    )

    suspend fun generate(
        notBefore: Date = Date(System.currentTimeMillis() - 1000),
        notAfter: Date = Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000),
    ): GeneratedChain {
        val signingKey = JWKKey.generate(KeyType.secp256r1)
        val leafPublicKey = JWK.parse(signingKey.getPublicKey().exportJWK()).toECKey().toPublicKey()

        val rootKeyPair = KeyPairGenerator.getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .genKeyPair()

        val rootCert = selfSignedCa(rootKeyPair, "CN=Demo RP Registrar Root CA", notBefore, notAfter)
        val leafCert = signedLeaf(rootCert, rootKeyPair.private, "CN=Demo Relying Party", leafPublicKey, notBefore, notAfter)

        val base64 = Base64.getEncoder()
        return GeneratedChain(
            signingKey = signingKey,
            x5c = listOf(leafCert, rootCert).map { base64.encodeToString(it.encoded) },
            rootCertificateDer = rootCert.encoded,
        )
    }

    private fun selfSignedCa(
        keyPair: KeyPair,
        dn: String,
        notBefore: Date,
        notAfter: Date,
    ): X509Certificate {
        val name = X500Name(dn)
        val builder = JcaX509v3CertificateBuilder(name, serial(), notBefore, notAfter, name, keyPair.public)
        val extensionUtils = JcaX509ExtensionUtils()
        builder.addExtension(Extension.subjectKeyIdentifier, false, extensionUtils.createSubjectKeyIdentifier(keyPair.public))
        builder.addExtension(Extension.authorityKeyIdentifier, false, extensionUtils.createAuthorityKeyIdentifier(keyPair.public))
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
        return sign(keyPair.private, builder)
    }

    private fun signedLeaf(
        issuerCert: X509Certificate,
        issuerKey: PrivateKey,
        dn: String,
        subjectKey: PublicKey,
        notBefore: Date,
        notAfter: Date,
    ): X509Certificate {
        val builder = JcaX509v3CertificateBuilder(issuerCert, serial(), notBefore, notAfter, X500Name(dn), subjectKey)
        val extensionUtils = JcaX509ExtensionUtils()
        builder.addExtension(Extension.subjectKeyIdentifier, false, extensionUtils.createSubjectKeyIdentifier(subjectKey))
        builder.addExtension(Extension.authorityKeyIdentifier, false, extensionUtils.createAuthorityKeyIdentifier(issuerCert.publicKey))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        return sign(issuerKey, builder)
    }

    private fun sign(key: PrivateKey, builder: JcaX509v3CertificateBuilder): X509Certificate =
        JcaX509CertificateConverter().getCertificate(
            builder.build(JcaContentSignerBuilder("SHA256withECDSA").build(key))
        )

    private fun serial(): BigInteger = BigInteger(160, SecureRandom())
}
