package id.walt.gradle.x509

import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.CRLDistPoint
import org.bouncycastle.asn1.x509.DistributionPoint
import org.bouncycastle.asn1.x509.DistributionPointName
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.gradle.api.InvalidUserDataException
import java.math.BigInteger
import java.net.URI
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/** ISO/IEC 18013-5 Document Signer extended key usage. */
internal const val DOCUMENT_SIGNER_EKU_OID = "1.0.18013.5.1.2"
internal const val IACA_MAX_VALIDITY_DAYS = 20 * 365
internal const val DOCUMENT_SIGNER_MAX_VALIDITY_DAYS = 457

internal enum class KeyAlgorithm(val id: String, val jwsAlgorithm: String) {
    P256("P-256", "ES256"),
    P384("P-384", "ES384"),
    P521("P-521", "ES512"),
    RSA2048("RSA-2048", "RS256"),
    RSA3072("RSA-3072", "RS256"),
    RSA4096("RSA-4096", "RS256"),
    ED25519("Ed25519", "EdDSA");

    val isEc get() = this == P256 || this == P384 || this == P521

    /** Signature algorithm used when a key of this type signs a certificate. */
    val signatureAlgorithm
        get() = when (this) {
            P256 -> "SHA256withECDSA"
            P384 -> "SHA384withECDSA"
            P521 -> "SHA512withECDSA"
            RSA2048, RSA3072, RSA4096 -> "SHA256withRSA"
            ED25519 -> "Ed25519"
        }

    fun generate(random: SecureRandom): KeyPair = when (this) {
        P256, P384, P521 -> KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp${id.removePrefix("P-")}r1"), random)
        }
        RSA2048, RSA3072, RSA4096 -> KeyPairGenerator.getInstance("RSA").apply {
            initialize(id.removePrefix("RSA-").toInt(), random)
        }
        ED25519 -> KeyPairGenerator.getInstance("Ed25519")
    }.generateKeyPair()

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
            ?: throw InvalidUserDataException("Unknown key algorithm '$id'; use one of ${entries.map { it.id }}")
    }
}

internal class GeneratedCertificate(
    val request: X509CertificateRequest,
    val keyAlgorithm: KeyAlgorithm,
    val keyPair: KeyPair,
    val certificate: X509CertificateHolder,
    /** This certificate followed by its issuers up to the root. */
    val chain: List<X509CertificateHolder>,
)

internal object X509CertificateGenerator {
    private val extensionUtils = JcaX509ExtensionUtils()
    private val isoCountries = Locale.getISOCountries().toSet()

    private val keyUsageBits = mapOf(
        "digitalSignature" to KeyUsage.digitalSignature,
        "nonRepudiation" to KeyUsage.nonRepudiation,
        "contentCommitment" to KeyUsage.nonRepudiation,
        "keyEncipherment" to KeyUsage.keyEncipherment,
        "dataEncipherment" to KeyUsage.dataEncipherment,
        "keyAgreement" to KeyUsage.keyAgreement,
        "keyCertSign" to KeyUsage.keyCertSign,
        "cRLSign" to KeyUsage.cRLSign,
        "encipherOnly" to KeyUsage.encipherOnly,
        "decipherOnly" to KeyUsage.decipherOnly,
    )

    private val namedKeyPurposes = mapOf(
        "serverAuth" to KeyPurposeId.id_kp_serverAuth,
        "clientAuth" to KeyPurposeId.id_kp_clientAuth,
        "codeSigning" to KeyPurposeId.id_kp_codeSigning,
        "emailProtection" to KeyPurposeId.id_kp_emailProtection,
        "timeStamping" to KeyPurposeId.id_kp_timeStamping,
        "OCSPSigning" to KeyPurposeId.id_kp_OCSPSigning,
    )

    /** Generates all [requests], issuers before the certificates they sign. */
    fun generate(
        requests: List<X509CertificateRequest>,
        now: Instant = Instant.now(),
        random: SecureRandom = SecureRandom(),
    ): Map<String, GeneratedCertificate> {
        val byName = requests.associateBy { it.name }
        val generated = linkedMapOf<String, GeneratedCertificate>()
        val inProgress = mutableSetOf<String>()

        fun resolve(request: X509CertificateRequest): GeneratedCertificate = generated[request.name] ?: run {
            if (!inProgress.add(request.name)) throw InvalidUserDataException("x509 issuer cycle involving '${request.name}'")
            val issuer = request.issuer?.let { issuerName ->
                resolve(byName[issuerName] ?: throw InvalidUserDataException(
                    "x509 certificate '${request.name}' references unknown issuer '$issuerName'"))
            }
            create(request, issuer, now.truncatedTo(ChronoUnit.SECONDS), random).also { generated[request.name] = it }
        }

        requests.forEach(::resolve)
        return generated
    }

    private fun create(
        request: X509CertificateRequest,
        issuer: GeneratedCertificate?,
        notBefore: Instant,
        random: SecureRandom,
    ): GeneratedCertificate {
        val name = request.name
        fun invalid(message: String): Nothing = throw InvalidUserDataException("x509 certificate '$name': $message")

        val keyAlgorithm = KeyAlgorithm.of(request.keyAlgorithm)
        val subject = try {
            X500Name(BCStyle.INSTANCE, request.subject)
        } catch (e: IllegalArgumentException) {
            invalid("invalid subject '${request.subject}': ${e.message}")
        }
        if (request.validityDays <= 0) invalid("validityDays must be positive")
        val notAfter = notBefore.plus(Duration.ofDays(request.validityDays.toLong()))
        if (issuer != null) {
            if (issuer.request.profile != X509Profile.CA && issuer.request.profile != X509Profile.IACA) {
                invalid("issuer '${issuer.request.name}' is a ${issuer.request.profile} and cannot sign certificates")
            }
            if (notAfter.isAfter(issuer.certificate.notAfter.toInstant())) {
                invalid("validity ends after its issuer '${issuer.request.name}'; lower validityDays")
            }
        }

        val isIso = request.profile == X509Profile.IACA || request.profile == X509Profile.DOCUMENT_SIGNER
        if (isIso) validateIsoRequest(request, issuer, keyAlgorithm, subject, ::invalid)

        val keyPair = keyAlgorithm.generate(random)
        val signerKeyPair = issuer?.keyPair ?: keyPair
        val signerAlgorithm = issuer?.keyAlgorithm ?: keyAlgorithm
        val builder = JcaX509v3CertificateBuilder(
            issuer?.certificate?.subject ?: subject,
            serialNumber(random),
            Date.from(notBefore),
            Date.from(notAfter),
            subject,
            keyPair.public,
        )
        fun add(oid: ASN1ObjectIdentifier, critical: Boolean, value: ASN1Encodable) {
            builder.addExtension(oid, critical, value)
        }

        add(Extension.subjectKeyIdentifier, false, extensionUtils.createSubjectKeyIdentifier(keyPair.public))
        if (issuer != null) {
            add(Extension.authorityKeyIdentifier, false, extensionUtils.createAuthorityKeyIdentifier(issuer.keyPair.public))
        }
        when (request.profile) {
            X509Profile.CA -> {
                add(Extension.basicConstraints, true, request.pathLength?.let(::BasicConstraints) ?: BasicConstraints(true))
                add(Extension.keyUsage, true, keyUsage(request.keyUsages.ifEmpty { listOf("keyCertSign", "cRLSign") }, ::invalid))
            }
            X509Profile.LEAF -> {
                if (request.pathLength != null) invalid("pathLength only applies to CA certificates")
                add(Extension.basicConstraints, true, BasicConstraints(false))
                add(Extension.keyUsage, true, keyUsage(request.keyUsages.ifEmpty { listOf("digitalSignature") }, ::invalid))
            }
            X509Profile.IACA -> {
                add(Extension.basicConstraints, true, BasicConstraints(0))
                add(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
            }
            X509Profile.DOCUMENT_SIGNER -> {
                add(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
                add(Extension.extendedKeyUsage, true, ExtendedKeyUsage(KeyPurposeId.getInstance(ASN1ObjectIdentifier(DOCUMENT_SIGNER_EKU_OID))))
            }
        }
        if (!isIso) {
            if (request.extendedKeyUsages.isNotEmpty()) {
                add(Extension.extendedKeyUsage, false, ExtendedKeyUsage(request.extendedKeyUsages.map { keyPurpose(it, ::invalid) }.toTypedArray()))
            }
            subjectAlternativeNames(request, ::invalid)?.let { add(Extension.subjectAlternativeName, false, it) }
        }
        issuerAlternativeName(request, issuer)?.let { add(Extension.issuerAlternativeName, false, it) }
        request.crlDistributionPointUri?.let { add(Extension.cRLDistributionPoints, false, crlDistributionPoint(it)) }

        val certificate = builder.build(JcaContentSignerBuilder(signerAlgorithm.signatureAlgorithm).build(signerKeyPair.private))
        return GeneratedCertificate(request, keyAlgorithm, keyPair, certificate, listOf(certificate) + issuer?.chain.orEmpty())
    }

    /** Mirrors the ISO 18013-5 profile checks of waltid-x509's IACAValidator and DocumentSignerValidator. */
    private fun validateIsoRequest(
        request: X509CertificateRequest,
        issuer: GeneratedCertificate?,
        keyAlgorithm: KeyAlgorithm,
        subject: X500Name,
        invalid: (String) -> Nothing,
    ) {
        val profile = request.profile
        if (!keyAlgorithm.isEc) invalid("$profile certificates require an EC key (P-256, P-384 or P-521)")
        val country = subject.attribute(BCStyle.C) ?: invalid("$profile subject must contain C (country)")
        if (country !in isoCountries) invalid("'$country' is not an ISO 3166-1 alpha-2 country code")
        if (subject.attribute(BCStyle.CN) == null) invalid("$profile subject must contain CN")
        if (request.pathLength != null || request.keyUsages.isNotEmpty() || request.extendedKeyUsages.isNotEmpty() ||
            request.dnsNames.isNotEmpty() || request.ipAddresses.isNotEmpty() || request.uris.isNotEmpty() || request.emails.isNotEmpty()
        ) invalid("$profile extensions are fixed by ISO 18013-5; remove pathLength, keyUsages, extendedKeyUsages and SANs")
        request.issuerAlternativeNameUri?.let { if (!URI(it).isAbsolute) invalid("issuerAlternativeNameUri must be an absolute URI") }
        request.crlDistributionPointUri?.let {
            if (URI(it).scheme?.lowercase() !in setOf("http", "https")) invalid("crlDistributionPointUri must be an http(s) URL")
        }

        when (profile) {
            X509Profile.IACA -> {
                if (issuer != null) invalid("IACA certificates are self-signed; remove issuer")
                if (request.issuerAlternativeNameUri == null && request.issuerAlternativeNameEmail == null) {
                    invalid("IACA needs issuerAlternativeNameUri or issuerAlternativeNameEmail")
                }
                if (request.validityDays > IACA_MAX_VALIDITY_DAYS) invalid("IACA validity must not exceed 20 years")
            }
            X509Profile.DOCUMENT_SIGNER -> {
                if (issuer?.request?.profile != X509Profile.IACA) invalid("Document Signer issuer must be an IACA certificate")
                if (request.crlDistributionPointUri == null) invalid("Document Signer needs crlDistributionPointUri")
                if (request.validityDays > DOCUMENT_SIGNER_MAX_VALIDITY_DAYS) invalid("Document Signer validity must not exceed 457 days")
                val iacaSubject = issuer.certificate.subject
                if (country != iacaSubject.attribute(BCStyle.C)) invalid("country must match IACA '${issuer.request.name}'")
                if (subject.attribute(BCStyle.ST) != iacaSubject.attribute(BCStyle.ST)) {
                    invalid("stateOrProvinceName (ST) must match IACA '${issuer.request.name}'")
                }
            }
            else -> Unit
        }
    }

    private fun X500Name.attribute(type: ASN1ObjectIdentifier) = getRDNs(type).firstOrNull()?.first?.value?.toString()

    /** ISO 18013-5 compatible: positive, 16 octets (>= 63 bits entropy, <= 20 octets). */
    private fun serialNumber(random: SecureRandom): BigInteger {
        val bytes = ByteArray(16).also(random::nextBytes)
        bytes[0] = ((bytes[0].toInt() and 0x7f) or 0x40).toByte()
        return BigInteger(1, bytes)
    }

    private fun keyUsage(names: List<String>, invalid: (String) -> Nothing) = KeyUsage(names.fold(0) { bits, name ->
        bits or (keyUsageBits[name] ?: invalid("unknown key usage '$name'; use one of ${keyUsageBits.keys}"))
    })

    private fun keyPurpose(value: String, invalid: (String) -> Nothing): KeyPurposeId = namedKeyPurposes[value]
        ?: if (value.matches(Regex("""\d+(\.\d+)+"""))) KeyPurposeId.getInstance(ASN1ObjectIdentifier(value))
        else invalid("unknown extended key usage '$value'; use one of ${namedKeyPurposes.keys} or a dotted OID")

    private fun subjectAlternativeNames(request: X509CertificateRequest, invalid: (String) -> Nothing): GeneralNames? {
        val names = request.dnsNames.map { GeneralName(GeneralName.dNSName, it) } +
            request.ipAddresses.map {
                try {
                    GeneralName(GeneralName.iPAddress, it)
                } catch (e: IllegalArgumentException) {
                    invalid("invalid IP address '$it'")
                }
            } +
            request.uris.map { GeneralName(GeneralName.uniformResourceIdentifier, it) } +
            request.emails.map { GeneralName(GeneralName.rfc822Name, it) }
        return names.takeIf { it.isNotEmpty() }?.let { GeneralNames(it.toTypedArray()) }
    }

    /** A Document Signer without its own IssuerAlternativeName reuses its IACA's, as the ISO profile requires one. */
    private fun issuerAlternativeName(request: X509CertificateRequest, issuer: GeneratedCertificate?): GeneralNames? {
        val source = if (request.profile == X509Profile.DOCUMENT_SIGNER &&
            request.issuerAlternativeNameUri == null && request.issuerAlternativeNameEmail == null
        ) issuer!!.request else request
        val names = listOfNotNull(
            source.issuerAlternativeNameUri?.let { GeneralName(GeneralName.uniformResourceIdentifier, it) },
            source.issuerAlternativeNameEmail?.let { GeneralName(GeneralName.rfc822Name, it) },
        )
        return names.takeIf { it.isNotEmpty() }?.let { GeneralNames(it.toTypedArray()) }
    }

    private fun crlDistributionPoint(uri: String) = CRLDistPoint(arrayOf(DistributionPoint(
        DistributionPointName(GeneralNames(GeneralName(GeneralName.uniformResourceIdentifier, uri))), null, null,
    )))
}
