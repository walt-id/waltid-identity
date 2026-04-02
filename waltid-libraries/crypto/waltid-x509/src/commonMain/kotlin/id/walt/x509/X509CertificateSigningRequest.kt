package id.walt.x509

import id.walt.crypto.keys.Key
import id.walt.x509.iso.blockingBridge
import okio.ByteString
import okio.ByteString.Companion.toByteString
import kotlin.io.encoding.Base64

/**
 * DER encoded PKCS#10 certificate signing request as platform-agnostic wrapper.
 */
data class CertificateSigningRequestDer(
    val bytes: ByteString,
) {
    fun toPEMEncodedString() = "$PEM_HEADER\r\n" +
            Base64.Pem.encode(bytes.toByteArray()) +
            "\r\n$PEM_FOOTER"

    companion object {
        private const val PEM_HEADER = "-----BEGIN CERTIFICATE REQUEST-----"
        private const val PEM_FOOTER = "-----END CERTIFICATE REQUEST-----"

        fun fromPEMEncodedString(
            pemEncodedCertificateSigningRequest: String,
        ): CertificateSigningRequestDer {
            val base64Payload = extractPemBase64Payload(
                pemEncodedCertificate = pemEncodedCertificateSigningRequest,
                pemHeader = PEM_HEADER,
                pemFooter = PEM_FOOTER,
            )
            return CertificateSigningRequestDer(
                bytes = Base64.Pem.decode(
                    source = base64Payload,
                ).toByteString(),
            )
        }
    }
}

data class X509CertificateSigningRequestData(
    val subject: X509Subject,
    val requestedExtensions: List<X509RequestedExtension> = emptyList(),
    val attributes: List<X509CsrAttribute> = emptyList(),
) {
    init {
        require(requestedExtensions.map { it.oid }.distinct().size == requestedExtensions.size) {
            "CSR requested extensions must not contain duplicate OIDs"
        }
    }

    val subjectAlternativeNames: Set<X509SubjectAlternativeName>
        get() = requestedExtensions.firstOrNull {
            it.oid == X509V3ExtensionOID.SubjectAlternativeName.oid
        }?.subjectAlternativeNames ?: emptySet()

    val keyUsages: Set<X509KeyUsage>
        get() = requestedExtensions.firstOrNull {
            it.oid == X509V3ExtensionOID.KeyUsage.oid
        }?.keyUsages ?: emptySet()

    val extendedKeyUsages: Set<X509ExtendedKeyUsage>
        get() = requestedExtensions.firstOrNull {
            it.oid == X509V3ExtensionOID.ExtendedKeyUsage.oid
        }?.extendedKeyUsages ?: emptySet()

    val basicConstraints: X509BasicConstraints?
        get() = requestedExtensions.firstOrNull {
            it.oid == X509V3ExtensionOID.BasicConstraints.oid
        }?.basicConstraints
}

data class X509CertificateSigningRequestSpec(
    val csrData: X509CertificateSigningRequestData,
    val signingKey: Key,
)

data class X509DecodedCertificateSigningRequest(
    val csrData: X509CertificateSigningRequestData,
    val publicKey: Key,
    val signatureAlgorithmOid: String,
    val signatureAlgorithmName: String? = null,
) {
    val subject: X509Subject
        get() = csrData.subject

    val requestedExtensions: List<X509RequestedExtension>
        get() = csrData.requestedExtensions

    val attributes: List<X509CsrAttribute>
        get() = csrData.attributes

    val subjectAlternativeNames: Set<X509SubjectAlternativeName>
        get() = csrData.subjectAlternativeNames

    val keyUsages: Set<X509KeyUsage>
        get() = csrData.keyUsages

    val extendedKeyUsages: Set<X509ExtendedKeyUsage>
        get() = csrData.extendedKeyUsages

    val basicConstraints: X509BasicConstraints?
        get() = csrData.basicConstraints
}

data class X509RequestedExtension(
    val oid: String,
    val critical: Boolean = false,
    val subjectAlternativeNames: Set<X509SubjectAlternativeName>? = null,
    val keyUsages: Set<X509KeyUsage>? = null,
    val extendedKeyUsages: Set<X509ExtendedKeyUsage>? = null,
    val basicConstraints: X509BasicConstraints? = null,
    val valueDer: ByteString? = null,
) {
    init {
        require(oid.isNotBlank()) { "CSR requested extension OID must not be blank" }
        subjectAlternativeNames?.let {
            require(it.isNotEmpty()) { "CSR subject alternative names request must not be empty" }
        }
        keyUsages?.let {
            require(it.isNotEmpty()) { "CSR key usage request must not be empty" }
        }
        extendedKeyUsages?.let {
            require(it.isNotEmpty()) { "CSR extended key usage request must not be empty" }
        }
        val representedValues = listOf(
            subjectAlternativeNames != null,
            keyUsages != null,
            extendedKeyUsages != null,
            basicConstraints != null,
            valueDer != null,
        ).count { it }
        require(representedValues == 1) {
            "CSR requested extension must contain exactly one value representation"
        }
    }

    companion object {
        fun subjectAlternativeNames(
            names: Set<X509SubjectAlternativeName>,
            critical: Boolean = false,
        ) = X509RequestedExtension(
            oid = X509V3ExtensionOID.SubjectAlternativeName.oid,
            critical = critical,
            subjectAlternativeNames = names,
        )

        fun keyUsages(
            usages: Set<X509KeyUsage>,
            critical: Boolean = true,
        ) = X509RequestedExtension(
            oid = X509V3ExtensionOID.KeyUsage.oid,
            critical = critical,
            keyUsages = usages,
        )

        fun extendedKeyUsages(
            usages: Set<X509ExtendedKeyUsage>,
            critical: Boolean = false,
        ) = X509RequestedExtension(
            oid = X509V3ExtensionOID.ExtendedKeyUsage.oid,
            critical = critical,
            extendedKeyUsages = usages,
        )

        fun basicConstraints(
            constraints: X509BasicConstraints,
            critical: Boolean = true,
        ) = X509RequestedExtension(
            oid = X509V3ExtensionOID.BasicConstraints.oid,
            critical = critical,
            basicConstraints = constraints,
        )

        fun raw(
            oid: String,
            valueDer: ByteString,
            critical: Boolean = false,
        ) = X509RequestedExtension(
            oid = oid,
            critical = critical,
            valueDer = valueDer,
        )
    }
}

data class X509CsrAttribute(
    val oid: String,
    val valuesDer: List<ByteString>,
) {
    init {
        require(oid.isNotBlank()) { "CSR attribute OID must not be blank" }
        require(valuesDer.isNotEmpty()) { "CSR attribute values must not be empty" }
    }

    companion object {
        fun singleValue(
            oid: String,
            valueDer: ByteString,
        ) = X509CsrAttribute(
            oid = oid,
            valuesDer = listOf(valueDer),
        )
    }
}

object X509CsrAttributeOids {
    const val ChallengePassword = "1.2.840.113549.1.9.7"
    const val ExtensionRequest = "1.2.840.113549.1.9.14"
}

class X509CertificateSigningRequestBuilder(
    private val subject: X509Subject,
) {
    private val subjectAlternativeNames = linkedSetOf<X509SubjectAlternativeName>()
    private val keyUsages = linkedSetOf<X509KeyUsage>()
    private val extendedKeyUsages = linkedSetOf<X509ExtendedKeyUsage>()
    private var basicConstraints: X509BasicConstraints? = null
    private val additionalRequestedExtensions = linkedMapOf<String, X509RequestedExtension>()
    private val attributes = mutableListOf<X509CsrAttribute>()

    fun addSubjectAlternativeName(name: X509SubjectAlternativeName) = apply {
        subjectAlternativeNames += name
    }

    fun addSubjectAlternativeNames(names: Iterable<X509SubjectAlternativeName>) = apply {
        subjectAlternativeNames += names
    }

    fun addKeyUsage(usage: X509KeyUsage) = apply {
        keyUsages += usage
    }

    fun addKeyUsages(usages: Iterable<X509KeyUsage>) = apply {
        keyUsages += usages
    }

    fun addExtendedKeyUsage(usage: X509ExtendedKeyUsage) = apply {
        extendedKeyUsages += usage
    }

    fun addExtendedKeyUsages(usages: Iterable<X509ExtendedKeyUsage>) = apply {
        extendedKeyUsages += usages
    }

    fun basicConstraints(constraints: X509BasicConstraints?) = apply {
        basicConstraints = constraints
    }

    fun addRequestedExtension(extension: X509RequestedExtension) = apply {
        when (extension.oid) {
            X509V3ExtensionOID.SubjectAlternativeName.oid -> {
                subjectAlternativeNames += requireNotNull(extension.subjectAlternativeNames)
            }

            X509V3ExtensionOID.KeyUsage.oid -> {
                keyUsages += requireNotNull(extension.keyUsages)
            }

            X509V3ExtensionOID.ExtendedKeyUsage.oid -> {
                extendedKeyUsages += requireNotNull(extension.extendedKeyUsages)
            }

            X509V3ExtensionOID.BasicConstraints.oid -> {
                basicConstraints = requireNotNull(extension.basicConstraints)
            }

            else -> additionalRequestedExtensions[extension.oid] = extension
        }
    }

    fun addAttribute(attribute: X509CsrAttribute) = apply {
        require(attribute.oid != X509CsrAttributeOids.ExtensionRequest) {
            "CSR extension request attribute is managed via requested extensions"
        }
        attributes += attribute
    }

    fun build(): X509CertificateSigningRequestData {
        val requestedExtensions = buildList {
            if (subjectAlternativeNames.isNotEmpty()) {
                add(X509RequestedExtension.subjectAlternativeNames(subjectAlternativeNames.toSet()))
            }
            if (keyUsages.isNotEmpty()) {
                add(X509RequestedExtension.keyUsages(keyUsages.toSet()))
            }
            if (extendedKeyUsages.isNotEmpty()) {
                add(X509RequestedExtension.extendedKeyUsages(extendedKeyUsages.toSet()))
            }
            basicConstraints?.let {
                add(X509RequestedExtension.basicConstraints(it))
            }
            addAll(additionalRequestedExtensions.values)
        }
        return X509CertificateSigningRequestData(
            subject = subject,
            requestedExtensions = requestedExtensions,
            attributes = attributes.toList(),
        )
    }
}

class X509CertificateSigningRequestGenerator {
    suspend fun generate(
        spec: X509CertificateSigningRequestSpec,
    ): CertificateSigningRequestDer = platformGenerateCertificateSigningRequest(spec)

    fun generateBlocking(
        spec: X509CertificateSigningRequestSpec,
    ): CertificateSigningRequestDer = blockingBridge {
        generate(spec)
    }
}

class X509CertificateSigningRequestParser {
    suspend fun parse(
        csr: CertificateSigningRequestDer,
    ): X509DecodedCertificateSigningRequest = platformParseCertificateSigningRequest(csr)

    fun parseBlocking(
        csr: CertificateSigningRequestDer,
    ): X509DecodedCertificateSigningRequest = blockingBridge {
        parse(csr)
    }
}

data class X509CsrProfileCompatibility(
    val isCompatible: Boolean,
    val issues: List<String> = emptyList(),
)

fun X509CertificateSigningRequestData.checkCompatibility(
    profile: X509CertificateProfile,
): X509CsrProfileCompatibility {
    val issues = mutableListOf<String>()

    profile.subject?.attributes?.forEach { profileAttribute ->
        val csrValues = subject.getAttributeValues(profileAttribute.oid)
        if (csrValues.isEmpty()) {
            issues += "CSR subject is missing profile attribute '${profileAttribute.oid}'"
        } else if (profileAttribute.value !in csrValues) {
            issues += "CSR subject attribute '${profileAttribute.oid}' does not match profile value '${profileAttribute.value}'"
        }
    }

    if (profile.subjectAlternativeNames.isNotEmpty()) {
        val unsupportedSanRequests = subjectAlternativeNames - profile.subjectAlternativeNames
        if (unsupportedSanRequests.isNotEmpty()) {
            issues += "CSR requests subject alternative names outside profile allowance: ${unsupportedSanRequests.joinToString()}"
        }
    }

    if (profile.keyUsages.isNotEmpty()) {
        val unsupportedKeyUsages = keyUsages - profile.keyUsages
        if (unsupportedKeyUsages.isNotEmpty()) {
            issues += "CSR requests key usages outside profile allowance: ${unsupportedKeyUsages.joinToString()}"
        }
    }

    if (profile.extendedKeyUsages.isNotEmpty()) {
        val unsupportedExtendedKeyUsages = extendedKeyUsages - profile.extendedKeyUsages
        if (unsupportedExtendedKeyUsages.isNotEmpty()) {
            issues += "CSR requests extended key usages outside profile allowance: ${unsupportedExtendedKeyUsages.joinToString { it.oid }}"
        }
    }

    profile.basicConstraints?.let { profileBasicConstraints ->
        basicConstraints?.let { requestedBasicConstraints ->
            if (requestedBasicConstraints != profileBasicConstraints) {
                issues += "CSR basic constraints $requestedBasicConstraints do not match profile basic constraints $profileBasicConstraints"
            }
        }
    }

    return X509CsrProfileCompatibility(
        isCompatible = issues.isEmpty(),
        issues = issues,
    )
}

fun X509DecodedCertificateSigningRequest.checkCompatibility(
    profile: X509CertificateProfile,
): X509CsrProfileCompatibility = csrData.checkCompatibility(profile)

@Throws(X509ValidationException::class)
suspend fun validateCertificateSigningRequestSignature(
    csr: CertificateSigningRequestDer,
) {
    if (!platformIsCertificateSigningRequestSignatureValid(csr)) {
        throw X509ValidationException("Certificate signing request signature is invalid")
    }
}

fun validateCertificateSigningRequestSignatureBlocking(
    csr: CertificateSigningRequestDer,
) = blockingBridge {
    validateCertificateSigningRequestSignature(csr)
}

suspend fun isCertificateSigningRequestSignatureValid(
    csr: CertificateSigningRequestDer,
): Boolean = platformIsCertificateSigningRequestSignatureValid(csr)

internal expect suspend fun platformGenerateCertificateSigningRequest(
    spec: X509CertificateSigningRequestSpec,
): CertificateSigningRequestDer

internal expect suspend fun platformParseCertificateSigningRequest(
    csr: CertificateSigningRequestDer,
): X509DecodedCertificateSigningRequest

internal expect suspend fun platformIsCertificateSigningRequestSignatureValid(
    csr: CertificateSigningRequestDer,
): Boolean
