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

open class X509CertificateDataBuilder private constructor(
    private val serialNumberGenerator: X509CertificateSerialNumberGenerator,
    override val version: Int = 3,
    override var serialNumberRaw: ByteString = serialNumberGenerator.next(),
    override var issuerDnRaw: ByteString,
    private var _subjectDn: String,
    private var _subjectDnRaw: ByteString,
    override var validity: X509Certificate.Validity,
    override var subjectPublicKeyInfo: Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo = WaltIdKeySubjectPublicKeyInfoBuilder()
) : X509Certificate.CertificateData, MutableExtensionContainer {

    constructor(
        serialNumberGenerator: X509CertificateSerialNumberGenerator,
        version: Int = 3,
        serialNumberRaw: ByteString = serialNumberGenerator.next(),
        issuerDnRaw: ByteString,
        subjectDn: String,
        validity: X509Certificate.Validity = defaultValidity(),
        subjectPublicKeyInfo: Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo = WaltIdKeySubjectPublicKeyInfoBuilder()
    ) : this(
        serialNumberGenerator,
        version,
        serialNumberRaw,
        issuerDnRaw,
        subjectDn,
        ByteString(),
        validity,
        subjectPublicKeyInfo
    )

    constructor(
        serialNumberGenerator: X509CertificateSerialNumberGenerator,
        version: Int = 3,
        serialNumberRaw: ByteString = serialNumberGenerator.next(),
        issuerDnRaw: ByteString,
        subjectDnRaw: ByteString,
        validity: X509Certificate.Validity = defaultValidity(),
        subjectPublicKeyInfo: Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo = WaltIdKeySubjectPublicKeyInfoBuilder()
    ) : this(
        serialNumberGenerator,
        version,
        serialNumberRaw,
        issuerDnRaw,
        "",
        subjectDnRaw,
        validity,
        subjectPublicKeyInfo
    )

    override var subjectDn: String
        get() = _subjectDn
        set(value) {
            _subjectDn = value
            _subjectDnRaw = ByteString()
        }

    override var subjectDnRaw: ByteString
        get() = _subjectDnRaw
        set(value) {
            _subjectDn = ""
            _subjectDnRaw = value
        }

    override val issuerDn: String
        get() = error("Not allowed, raw value must be provided")

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

    fun subjectPublicKey(spki: Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo): Unit {
        subjectPublicKeyInfo = WaltIdKeySubjectPublicKeyInfoBuilder(spki)
    }

    class WaltIdKeySubjectPublicKeyInfoBuilder private constructor(
        val selfSigned: Boolean,
        val crypto1key: Crypto1Key?,
        val key: Key?,
        val spki: Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo?
    ) : X509Certificate.SubjectPublicKeyInfo {

        constructor(spki: Pkcs10CertificateSigningRequest.SubjectPublicKeyInfo) : this(false, null, null, spki)
        constructor(crypto1key: Crypto1Key) : this(false, crypto1key, null, null)
        constructor(key: Key) : this(false, null, key, null)
        constructor() : this(true, null, null, null)

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

    companion object {
        fun defaultValidity(): X509Certificate.Validity = X509Certificate.Validity(
            notBefore = Clock.System.now(),
            notAfter = Clock.System.now() + 30.days,
        )
    }
}