package id.walt.certificate.x509.builder

import id.walt.certificate.x509.Pkcs10CertificateSigningRequest
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateSerialNumberGenerator
import id.walt.certificate.x509.extension.Extension
import id.walt.certificate.x509.extension.MutableExtensionContainer
import id.walt.crypto2.keys.Key
import kotlinx.io.bytestring.ByteString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import id.walt.crypto.keys.Key as Crypto1Key

open class X509CertificateDataBuilder(
    private val serialNumberGenerator: X509CertificateSerialNumberGenerator,
    override val version: Int = 3,
    override var serialNumberRaw: ByteString = serialNumberGenerator.next(),
    override var issuerDnRaw: ByteString,
    override var subjectDn: String,
    override var validity: X509Certificate.Validity = X509Certificate.Validity(
        notBefore = Clock.System.now(),
        notAfter = Clock.System.now() + 30.days,
    ),
    override var subjectPublicKeyInfo: Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo = WaltIdKeySubjectPublicKeyInfoBuilder()
) : X509Certificate.CertificateData, MutableExtensionContainer {

    override val issuerDn: String
        get() = error("Not allowed, raw value must be provided")

    override val subjectDnRaw: ByteString
        get() = error("Not available in builder")

    override val extensions: MutableMap<String, Extension> = mutableMapOf()

    fun subjectPublicKeySelfSigned(): Unit {
        subjectPublicKeyInfo = WaltIdKeySubjectPublicKeyInfoBuilder()
    }

    fun subjectPublicKey(key: Key): Unit {
        subjectPublicKeyInfo = WaltIdKeySubjectPublicKeyInfoBuilder(key)
    }

    fun subjectPublicKey(key: Crypto1Key): Unit {
        subjectPublicKeyInfo = WaltIdKeySubjectPublicKeyInfoBuilder(key)
    }

    class WaltIdKeySubjectPublicKeyInfoBuilder private constructor(
        val selfSigned: Boolean,
        val crypto1key: Crypto1Key?,
        val key: Key?
    ) : X509Certificate.SubjectPublicKeyInfo {

        constructor(crypto1key: Crypto1Key) : this(false, crypto1key, null)
        constructor(key: Key) : this(false, null, key)
        constructor() : this(true, null, null)

        override val algorithmName: String
            get() = error("needs to be taken from issuer key")
        override val algorithmOid: String
            get() = error("needs to be taken from issuer key")
        override val ellipticCurveOid: String
            get() = error("needs to be taken from issuer key")
        override val rsaKeyLengthBits: Int?
            get() = error("needs to be taken from issuer key")
        override val keyValueRaw: ByteString
            get() = error("needs to be taken from issuer key")
        override val encodedDer: ByteString
            get() = error("needs to be taken from issuer key")
    }
}