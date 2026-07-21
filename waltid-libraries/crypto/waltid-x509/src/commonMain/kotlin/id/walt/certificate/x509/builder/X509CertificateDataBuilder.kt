package id.walt.certificate.x509.builder

import id.walt.certificate.x509.Pkcs10CertificateSigningRequest
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateSerialNumberGenerator
import id.walt.certificate.x509.extension.Extension
import id.walt.certificate.x509.extension.MutableExtensionContainer
import id.walt.crypto.keys.Key
import kotlinx.io.bytestring.ByteString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

open class X509CertificateDataBuilder(
    private val serialNumberGenerator: X509CertificateSerialNumberGenerator,
    override val version: Int = 3,
    override var serialNumberRaw: ByteString = serialNumberGenerator.next(),
    override var issuerDn: String,
    override var subjectDn: String,
    override var validity: X509Certificate.Validity = X509Certificate.Validity(
        notBefore = Clock.System.now(),
        notAfter = Clock.System.now() + 30.days,
    ),
    override var subjectPublicKeyInfo: Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo = SelfSignedSubjectPublicKeyInfo()
) : X509Certificate.CertificateData, MutableExtensionContainer {

    override val extensions: MutableMap<String, Extension> = mutableMapOf()

    fun subjectPublicKey(key: Key): Unit {
        subjectPublicKeyInfo = WaltIdKeySubjectPublicKeyInfo(key)
    }

    class WaltIdKeySubjectPublicKeyInfo(val key: Key) : X509Certificate.SubjectPublicKeyInfo {
        override val algorithmName: String
            get() = error("needs to be taken from issuer key")
        override val algorithmOid: String
            get() = error("needs to be taken from issuer key")
        override val ellipticCurveOid: String
            get() = error("needs to be taken from issuer key")
        override val publicKeyRaw: ByteString
            get() = error("needs to be taken from issuer key")
    }

    class SelfSignedSubjectPublicKeyInfo : X509Certificate.SubjectPublicKeyInfo {
        override val algorithmName: String
            get() = error("needs to be taken from issuer key")
        override val algorithmOid: String
            get() = error("needs to be taken from issuer key")
        override val ellipticCurveOid: String
            get() = error("needs to be taken from issuer key")
        override val publicKeyRaw: ByteString
            get() = error("needs to be taken from issuer key")
    }
}