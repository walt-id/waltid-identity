package id.walt.ades.test

import id.walt.ades.crypto.AdesSigningCredentials
import id.walt.ades.transport.AdesTransport
import id.walt.ades.transport.AdesTransportException
import id.walt.ades.x509.X509Certificate as AdesCertificate
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AccessDescription
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.AuthorityInformationAccess
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.CRLNumber
import org.bouncycastle.asn1.x509.CRLReason
import org.bouncycastle.asn1.x509.DistributionPoint
import org.bouncycastle.asn1.x509.DistributionPointName
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage as BcKeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v2CRLBuilder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder
import org.bouncycastle.cert.ocsp.CertificateStatus
import org.bouncycastle.cert.ocsp.OCSPReq
import org.bouncycastle.cert.ocsp.OCSPRespBuilder
import org.bouncycastle.cert.ocsp.RespID
import org.bouncycastle.cert.ocsp.RevokedStatus
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.tsp.TSPAlgorithms
import org.bouncycastle.tsp.TimeStampRequest
import org.bouncycastle.tsp.TimeStampResponseGenerator
import org.bouncycastle.tsp.TimeStampTokenGenerator

/** One issued certificate with its key pair. */
class TestEntity(val name: String, val keyPair: KeyPair, val holder: X509CertificateHolder) {
    val certificate: AdesCertificate = AdesCertificate(holder.encoded)
    val der: ByteArray get() = holder.encoded
}

/**
 * Self-contained test PKI: Root CA -> Issuing CA -> {signer, revoked signer, expired signer, TSA, OCSP responder},
 * plus a second TSA. [transport] serves the TSA, OCSP, CRL and caIssuers endpoints referenced in the certificates.
 */
class TestPki(
    /** Time-stamp generation time; defaults to the system clock. */
    var tsaClock: () -> Instant = { Clock.System.now() },
    private val keyType: KeyType = KeyType.EC_P256,
) {
    enum class KeyType { EC_P256, RSA_2048 }

    private val serial = AtomicLong(1000)
    private val baseUrl = "http://pki.test.walt.id"
    val now: Instant = Clock.System.now()

    val root: TestEntity = issue("CN=walt.id Test Root CA,O=walt.id,C=AT", null, ca = true)
    val issuingCa: TestEntity = issue("CN=walt.id Test Issuing CA,O=walt.id,C=AT", root, ca = true, withRevocationInfo = true)
    val signer: TestEntity = issue("CN=Test Signer,O=walt.id,C=AT", issuingCa, withRevocationInfo = true)
    val secondSigner: TestEntity = issue("CN=Second Test Signer,O=walt.id,C=AT", issuingCa, withRevocationInfo = true)
    val revokedSigner: TestEntity = issue("CN=Revoked Test Signer,O=walt.id,C=AT", issuingCa, withRevocationInfo = true)
    val expiredSigner: TestEntity = issue(
        "CN=Expired Test Signer,O=walt.id,C=AT", issuingCa, withRevocationInfo = true,
        notBefore = now - 400.days, notAfter = now - 30.days,
    )
    val tsa: TestEntity = issue("CN=walt.id Test TSA,O=walt.id,C=AT", issuingCa, eku = KeyPurposeId.id_kp_timeStamping, withRevocationInfo = true)
    val tsa2: TestEntity = issue("CN=walt.id Test TSA 2,O=walt.id,C=AT", issuingCa, eku = KeyPurposeId.id_kp_timeStamping, withRevocationInfo = true)
    val ocspResponder: TestEntity = issue("CN=walt.id Test OCSP Responder,O=walt.id,C=AT", issuingCa, eku = KeyPurposeId.id_kp_OCSPSigning, ocspNoCheck = true)

    /** Revocation time of [revokedSigner]. */
    val revocationTime: Instant = now - 1.days
    private val revoked = mutableMapOf(revokedSigner.holder.serialNumber to revocationTime)

    val tsaUrl = "$baseUrl/tsa"
    val tsa2Url = "$baseUrl/tsa2"

    /** Additional certificates that are revoked from a given instant (e.g. to revoke the TSA). */
    fun revoke(entity: TestEntity, at: Instant) {
        revoked[entity.holder.serialNumber] = at
    }

    val requests = mutableListOf<String>()

    val transport: AdesTransport = object : AdesTransport {
        override suspend fun get(url: String): ByteArray {
            requests += "GET $url"
            return when (url) {
                "$baseUrl/crl/root.crl" -> crl(root)
                "$baseUrl/crl/issuing.crl" -> crl(issuingCa)
                "$baseUrl/ca/root.crt" -> root.der
                "$baseUrl/ca/issuing.crt" -> issuingCa.der
                else -> throw AdesTransportException("404 $url")
            }
        }

        override suspend fun post(url: String, contentType: String, body: ByteArray, headers: Map<String, String>): ByteArray {
            requests += "POST $url"
            return when (url) {
                tsaUrl -> timestamp(body, tsa)
                tsa2Url -> timestamp(body, tsa2)
                "$baseUrl/ocsp" -> ocsp(body)
                else -> throw AdesTransportException("404 $url")
            }
        }
    }

    suspend fun credentials(entity: TestEntity = signer, includeRoot: Boolean = true): AdesSigningCredentials {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val spec = if (keyType == KeyType.EC_P256) KeySpec.Ec(EcCurve.P256) else KeySpec.Rsa(2048)
        val key = runtime.restore(
            StoredKey.Software(
                StoredKey.CURRENT_VERSION, KeyId(entity.name.hashCode().toString()), spec, setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                EncodedKey.Pkcs8Der(BinaryData(entity.keyPair.private.encoded)),
            ),
        )
        val chain = listOf(entity.certificate, issuingCa.certificate) + if (includeRoot) listOf(root.certificate) else emptyList()
        return AdesSigningCredentials(key, chain)
    }

    private fun sigAlg() = if (keyType == KeyType.EC_P256) "SHA256withECDSA" else "SHA256withRSA"

    private fun newKeyPair(): KeyPair = if (keyType == KeyType.EC_P256) {
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    } else KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun issue(
        dn: String,
        issuer: TestEntity?,
        ca: Boolean = false,
        eku: KeyPurposeId? = null,
        ocspNoCheck: Boolean = false,
        withRevocationInfo: Boolean = false,
        notBefore: Instant = now - 365.days,
        notAfter: Instant = now + 365.days,
    ): TestEntity {
        val kp = newKeyPair()
        val issuerName = issuer?.holder?.subject ?: X500Name(dn)
        val signingKey = issuer?.keyPair ?: kp
        val utils = JcaX509ExtensionUtils()
        val builder = X509v3CertificateBuilder(
            issuerName, BigInteger.valueOf(serial.incrementAndGet()), Date.from(notBefore.toJavaInstant()), Date.from(notAfter.toJavaInstant()),
            X500Name(dn), SubjectPublicKeyInfo.getInstance(kp.public.encoded),
        )
        builder.addExtension(Extension.subjectKeyIdentifier, false, utils.createSubjectKeyIdentifier(kp.public))
        issuer?.let { builder.addExtension(Extension.authorityKeyIdentifier, false, utils.createAuthorityKeyIdentifier(it.keyPair.public)) }
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(ca))
        builder.addExtension(
            Extension.keyUsage, true,
            BcKeyUsage(if (ca) BcKeyUsage.keyCertSign or BcKeyUsage.cRLSign else BcKeyUsage.digitalSignature or BcKeyUsage.nonRepudiation),
        )
        eku?.let { builder.addExtension(Extension.extendedKeyUsage, it == KeyPurposeId.id_kp_timeStamping, ExtendedKeyUsage(it)) }
        if (ocspNoCheck) builder.addExtension(ASN1ObjectIdentifier("1.3.6.1.5.5.7.48.1.5"), false, DERNull.INSTANCE)
        if (withRevocationInfo && issuer != null) {
            val crlName = if (issuer.name.contains("Root")) "root" else "issuing"
            val caName = if (issuer.name.contains("Root")) "root" else "issuing"
            builder.addExtension(
                Extension.cRLDistributionPoints, false,
                CRLDistPoint(arrayOf(DistributionPoint(DistributionPointName(GeneralNames(GeneralName(GeneralName.uniformResourceIdentifier, "$baseUrl/crl/$crlName.crl"))), null, null))),
            )
            val aia = mutableListOf(AccessDescription(AccessDescription.id_ad_caIssuers, GeneralName(GeneralName.uniformResourceIdentifier, "$baseUrl/ca/$caName.crt")))
            if (!ca) aia += AccessDescription(AccessDescription.id_ad_ocsp, GeneralName(GeneralName.uniformResourceIdentifier, "$baseUrl/ocsp"))
            builder.addExtension(Extension.authorityInfoAccess, false, AuthorityInformationAccess(aia.toTypedArray()))
        }
        val holder = builder.build(JcaContentSignerBuilder(sigAlg()).build(signingKey.private))
        return TestEntity(dn, kp, holder)
    }

    /** CRL issued by [ca] listing all revoked certificates it issued. */
    fun crl(ca: TestEntity, thisUpdate: Instant = Clock.System.now()): ByteArray {
        val b = X509v2CRLBuilder(ca.holder.subject, Date.from(thisUpdate.toJavaInstant()))
        b.setNextUpdate(Date.from((thisUpdate + 7.days).toJavaInstant()))
        revoked.forEach { (sn, at) -> if (at <= thisUpdate) b.addCRLEntry(sn, Date.from(at.toJavaInstant()), CRLReason.keyCompromise) }
        b.addExtension(Extension.cRLNumber, false, CRLNumber(BigInteger.valueOf(serial.incrementAndGet())))
        return b.build(JcaContentSignerBuilder(sigAlg()).build(ca.keyPair.private)).encoded
    }

    private fun ocsp(body: ByteArray): ByteArray {
        val req = OCSPReq(body)
        val builder = BasicOCSPRespBuilder(RespID(ocspResponder.holder.subject))
        val produced = Clock.System.now()
        for (r in req.requestList) {
            val at = revoked[r.certID.serialNumber]
            val status: CertificateStatus? = if (at != null && at <= produced) RevokedStatus(Date.from(at.toJavaInstant()), CRLReason.keyCompromise) else CertificateStatus.GOOD
            builder.addResponse(r.certID, status, Date.from(produced.toJavaInstant()), Date.from((produced + 1.days).toJavaInstant()))
        }
        val basic = builder.build(JcaContentSignerBuilder(sigAlg()).build(ocspResponder.keyPair.private), arrayOf(ocspResponder.holder), Date.from(produced.toJavaInstant()))
        return OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL, basic).encoded
    }

    private fun timestamp(body: ByteArray, authority: TestEntity): ByteArray {
        val request = TimeStampRequest(body)
        val signerInfo = JcaSimpleSignerInfoGeneratorBuilder().build(sigAlg(), authority.keyPair.private, CertificateFactory.getInstance("X.509")
            .generateCertificate(authority.der.inputStream()) as X509Certificate)
        val gen = TimeStampTokenGenerator(signerInfo, JcaDigestCalculatorProviderBuilder().build().get(AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256)), ASN1ObjectIdentifier("1.2.3.4.1"))
        gen.addCertificates(JcaCertStore(listOf(CertificateFactory.getInstance("X.509").generateCertificate(authority.der.inputStream()))))
        val responder = TimeStampResponseGenerator(gen, TSPAlgorithms.ALLOWED)
        return responder.generate(request, BigInteger.valueOf(serial.incrementAndGet()), Date.from(tsaClock().toJavaInstant())).encoded
    }

}
