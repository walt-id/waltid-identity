package id.walt.etsi.cbades

import id.walt.ades.crypto.AdesSigningCredentials
import id.walt.ades.crypto.PublicKeyInfo
import id.walt.ades.tsp.TimestampSource
import id.walt.ades.x509.X509Certificate
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate as JcaCertificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaInstant
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.DistributionPoint
import org.bouncycastle.asn1.x509.DistributionPointName
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage as BcKeyUsage
import org.bouncycastle.asn1.x509.RoleSyntax
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.AttributeCertificateHolder
import org.bouncycastle.cert.AttributeCertificateIssuer
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.X509v2AttributeCertificateBuilder
import org.bouncycastle.cert.X509v2CRLBuilder
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.tsp.TSPAlgorithms
import org.bouncycastle.tsp.TimeStampRequest
import org.bouncycastle.tsp.TimeStampRequestGenerator
import org.bouncycastle.tsp.TimeStampResponseGenerator
import org.bouncycastle.tsp.TimeStampTokenGenerator

object CbAdesCredentials {
    private val runtime by lazy { CryptoRuntime(defaultSoftwareKeyProviders()) }

    suspend fun of(privateKey: PrivateKey, chain: List<X509Certificate>, id: String = "signer"): AdesSigningCredentials {
        val spec = PublicKeyInfo(chain.first().publicKey.encoded).keySpec
        val key = runtime.restore(
            StoredKey.Software(StoredKey.CURRENT_VERSION, KeyId(id), spec, setOf(KeyUsage.SIGN, KeyUsage.VERIFY), EncodedKey.Pkcs8Der(BinaryData(privateKey.encoded))),
        )
        return AdesSigningCredentials(key, chain)
    }

    /** Loads the first key entry of a PKCS#12 file (e.g. the P12 issued by the ETSI plugtest PKI). */
    suspend fun fromPkcs12(file: File, password: CharArray, extraChain: List<X509Certificate> = emptyList()): AdesSigningCredentials {
        val ks = KeyStore.getInstance("PKCS12").apply { file.inputStream().use { load(it, password) } }
        val alias = ks.aliases().toList().first { ks.isKeyEntry(it) }
        val key = ks.getKey(alias, password) as PrivateKey
        val chain = ks.getCertificateChain(alias).map { X509Certificate(it.encoded) }
        val completed = (chain + extraChain.filter { e -> chain.none { it == e } && chain.last().isIssuedByCandidate(e) }).distinct()
        return of(key, completed, alias)
    }

    private fun X509Certificate.isIssuedByCandidate(c: X509Certificate) = issuer.matches(c.subject)
}

/**
 * Persistent local test PKI (root -> issuing CA -> signers, TSA, attribute authority) used for the second signers,
 * counter-signers and attribute certificates required by some plugtest cases, and as a full offline fallback.
 * The root certificate and CRLs are written to `<dir>/trust/` so they can be shared with other participants.
 */
class LocalPlugtestPki(private val dir: File, private val participant: String) {
    private val cf = CertificateFactory.getInstance("X.509")
    private val now = Clock.System.now()

    class Entity(val keyPair: KeyPair, val cert: JcaCertificate) {
        val certificate get() = X509Certificate(cert.encoded)
        val holder get() = X509CertificateHolder(cert.encoded)
    }

    val root: Entity = load("root") { issue("CN=$participant CB-AdES Plugtests 2026 Test Root CA,O=$participant TEST,C=AT", null, ca = true, years = 10) }
    val ca: Entity = load("issuing-ca") { issue("CN=$participant CB-AdES Plugtests 2026 Test Issuing CA,O=$participant TEST,C=AT", root, ca = true, years = 5) }
    val signer: Entity = load("signer-1") { issue("CN=$participant CB-AdES Test Signer 1,O=$participant TEST,C=AT", ca) }
    val signer2: Entity = load("signer-2") { issue("CN=$participant CB-AdES Test Signer 2,O=$participant TEST,C=AT", ca) }
    val counter1: Entity = load("counter-1") { issue("CN=$participant CB-AdES Test Counter Signer 1,O=$participant TEST,C=AT", ca) }
    val counter2: Entity = load("counter-2") { issue("CN=$participant CB-AdES Test Counter Signer 2,O=$participant TEST,C=AT", ca) }
    val tsa: Entity = load("tsa") { issue("CN=$participant CB-AdES Test TSA,O=$participant TEST,C=AT", ca, eku = KeyPurposeId.id_kp_timeStamping) }
    val aa: Entity = load("aa") { issue("CN=$participant CB-AdES Test Attribute Authority,O=$participant TEST,C=AT", ca) }

    init {
        val trust = File(dir, "trust").apply { mkdirs() }
        File(trust, "${participant.lowercase()}-test-root.cer").writeBytes(root.cert.encoded)
        File(trust, "${participant.lowercase()}-test-issuing-ca.cer").writeBytes(ca.cert.encoded)
        File(trust, "${participant.lowercase()}-test-aa.cer").writeBytes(aa.cert.encoded)
        File(trust, "${participant.lowercase()}-test-tsa.cer").writeBytes(tsa.cert.encoded)
        File(trust, "${participant.lowercase()}-test-root.crl").writeBytes(crl(root))
        File(trust, "${participant.lowercase()}-test-issuing-ca.crl").writeBytes(crl(ca))
    }

    suspend fun credentials(e: Entity, name: String) = CbAdesCredentials.of(e.keyPair.private, listOf(e.certificate, ca.certificate, root.certificate), name)

    /** In-process RFC 3161 TSA (offline dry runs only; real plugtest signatures should use a public TSA). */
    val timestampSource = TimestampSource { alg, digest ->
        val reqGen = TimeStampRequestGenerator().apply { setCertReq(true) }
        val request: TimeStampRequest = reqGen.generate(ASN1ObjectIdentifier(alg.oid), digest)
        val signerInfo = JcaSimpleSignerInfoGeneratorBuilder().build("SHA256withECDSA", tsa.keyPair.private, tsa.cert)
        val gen = TimeStampTokenGenerator(signerInfo, JcaDigestCalculatorProviderBuilder().build().get(AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256)), ASN1ObjectIdentifier("1.3.6.1.4.1.99999.1"))
        gen.addCertificates(JcaCertStore(listOf(tsa.cert)))
        TimeStampResponseGenerator(gen, TSPAlgorithms.ALLOWED)
            .generate(request, BigInteger.valueOf(System.nanoTime()), Date()).timeStampToken.encoded
    }

    /** X.509 attribute certificate (RFC 5755) with a role attribute, issued by the local AA to [holder]. */
    fun attributeCertificate(holder: X509Certificate, role: String = "CB-AdES plugtests participant"): ByteArray {
        val b = X509v2AttributeCertificateBuilder(
            AttributeCertificateHolder(X509CertificateHolder(holder.encoded)),
            AttributeCertificateIssuer(X500Name.getInstance(aa.cert.subjectX500Principal.encoded)),
            BigInteger.valueOf(System.currentTimeMillis()),
            Date.from((now - 1.days).toJavaInstant()), Date.from((now + 365.days).toJavaInstant()),
        )
        b.addAttribute(ASN1ObjectIdentifier("2.5.4.72"), RoleSyntax(GeneralName(GeneralName.uniformResourceIdentifier, "urn:$participant:role:${role.replace(' ', '-')}")))
        return b.build(JcaContentSignerBuilder("SHA256withECDSA").build(aa.keyPair.private)).encoded
    }

    private fun crl(issuer: Entity): ByteArray {
        val b = X509v2CRLBuilder(X500Name.getInstance(issuer.cert.subjectX500Principal.encoded), Date.from((now - 1.days).toJavaInstant()))
        b.setNextUpdate(Date.from((now + 60.days).toJavaInstant()))
        return b.build(JcaContentSignerBuilder("SHA256withECDSA").build(issuer.keyPair.private)).encoded
    }

    private fun load(name: String, create: () -> Entity): Entity {
        val certFile = File(dir, "$name.cer")
        val keyFile = File(dir, "$name.key")
        if (certFile.exists() && keyFile.exists()) {
            val cert = cf.generateCertificate(certFile.inputStream()) as JcaCertificate
            val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            return Entity(KeyPair(cert.publicKey, key), cert)
        }
        dir.mkdirs()
        return create().also {
            certFile.writeBytes(it.cert.encoded)
            keyFile.writeBytes(it.keyPair.private.encoded)
            keyFile.setReadable(false, false); keyFile.setReadable(true, true)
        }
    }

    private var serial = System.currentTimeMillis()

    private fun issue(dn: String, issuer: Entity?, ca: Boolean = false, eku: KeyPurposeId? = null, years: Int = 2): Entity {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val utils = JcaX509ExtensionUtils()
        val subject = X500Name(dn)
        val b = X509v3CertificateBuilder(
            issuer?.let { X500Name.getInstance(it.cert.subjectX500Principal.encoded) } ?: subject, BigInteger.valueOf(serial++),
            Date.from((now - 1.days).toJavaInstant()), Date.from((now + (365 * years).days).toJavaInstant()),
            subject, SubjectPublicKeyInfo.getInstance(kp.public.encoded),
        )
        b.addExtension(Extension.subjectKeyIdentifier, false, utils.createSubjectKeyIdentifier(kp.public))
        issuer?.let { b.addExtension(Extension.authorityKeyIdentifier, false, utils.createAuthorityKeyIdentifier(it.cert)) }
        b.addExtension(Extension.basicConstraints, true, BasicConstraints(ca))
        b.addExtension(Extension.keyUsage, true, BcKeyUsage(if (ca) BcKeyUsage.keyCertSign or BcKeyUsage.cRLSign else BcKeyUsage.digitalSignature or BcKeyUsage.nonRepudiation))
        eku?.let { b.addExtension(Extension.extendedKeyUsage, true, ExtendedKeyUsage(it)) }
        if (issuer != null) {
            val name = if (issuer === root || issuer.cert.subjectX500Principal == issuer.cert.issuerX500Principal) "root" else "issuing-ca"
            b.addExtension(Extension.cRLDistributionPoints, false, CRLDistPoint(arrayOf(DistributionPoint(
                DistributionPointName(GeneralNames(GeneralName(GeneralName.uniformResourceIdentifier, "http://pki.cbades-plugtests.walt.example/$participant/$name.crl"))), null, null))))
        }
        val signingKey = issuer?.keyPair?.private ?: kp.private
        val holder = b.build(JcaContentSignerBuilder("SHA256withECDSA").build(signingKey))
        return Entity(kp, cf.generateCertificate(holder.encoded.inputStream()) as JcaCertificate)
    }
}
