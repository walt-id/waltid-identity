package id.walt.x509.id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509SigningAlgorithmInfo
import id.walt.certificate.x509.extension.Extension
import id.walt.x509.id.walt.certificate.x509.bouncycastle.extension.BouncyExtensionFactory
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.cert.X509CertificateHolder
import java.security.MessageDigest
import kotlin.time.toKotlinInstant

internal class BouncyX509Certificate(val certificate: X509CertificateHolder) : X509Certificate {

    override val data: X509Certificate.CertificateData = object : X509Certificate.CertificateData {

        override val version: Int = certificate.versionNumber

        override val serialNumberRaw: ByteString = ByteString(certificate.serialNumber.toByteArray())

        override val subjectDn: String = certificate.subject.toString()

        override val subjectPublicKeyInfo: X509Certificate.SubjectPublicKeyInfo =
            object : X509Certificate.SubjectPublicKeyInfo {
                override val algorithmName: String
                    get() = X509SigningAlgorithmInfo.algorithmNameByOid(algorithmOid)
                override val algorithmOid: String
                    get() = certificate.subjectPublicKeyInfo.algorithm.algorithm.toString()
                override val ellipticCurveOid: String?
                    get() = (certificate.subjectPublicKeyInfo.algorithm.parameters as? ASN1ObjectIdentifier)?.toString()
                override val publicKeyRaw: ByteString = ByteString(certificate.subjectPublicKeyInfo.publicKeyData.bytes)
            }

        override val issuerDn: String = certificate.issuer.toString()

        override val validity: X509Certificate.Validity = X509Certificate.Validity(
            notBefore = certificate.notBefore.toInstant().toKotlinInstant(),
            notAfter = certificate.notAfter.toInstant().toKotlinInstant()
        )

        override val extensions: Map<String, Extension>
            get() {
                return certificate.extensions?.extensionOIDs?.let { extensionOids ->
                    extensionOids
                        .associateWith { BouncyExtensionFactory.parseExtension(certificate.extensions.getExtension(it)) }
                        .mapKeys { it.key.toString() }
                } ?: emptyMap()
            }
    }

    override val signatureAlgorithmOid: String
        get() = certificate.signatureAlgorithm.algorithm.toString()

    override val signatureValueRaw: ByteString
        get() = ByteString(certificate.getSignature())

    override val encodedDer: ByteString = ByteString(certificate.encoded)


}