package id.walt.x509

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.parsePEMEncodedJcaPublicKey
import okio.ByteString.Companion.toByteString
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1TaggedObject
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERTaggedObject
import org.bouncycastle.asn1.DERUTF8String
import org.bouncycastle.asn1.pkcs.Attribute
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extensions
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.DefaultAlgorithmNameFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import java.io.StringWriter

internal actual suspend fun platformGenerateCertificateSigningRequest(
    spec: X509CertificateSigningRequestSpec,
): CertificateSigningRequestDer {
    require(spec.signingKey.hasPrivateKey) {
        "CSR signing key must include a private key"
    }
    require(spec.csrData.attributes.none { it.oid == X509CsrAttributeOids.ExtensionRequest }) {
        "CSR extension request attribute is managed via requestedExtensions"
    }

    val publicKey = parsePEMEncodedJcaPublicKey(spec.signingKey.getPublicKey().exportPEM())
    val csrBuilder = JcaPKCS10CertificationRequestBuilder(
        spec.csrData.subject.toJcaX500Name(),
        publicKey,
    )

    spec.csrData.requestedExtensions
        .takeIf { it.isNotEmpty() }
        ?.let { requestedExtensions ->
            csrBuilder.addAttribute(
                PKCSObjectIdentifiers.pkcs_9_at_extensionRequest,
                requestedExtensions.toBcExtensions(),
            )
        }

    spec.csrData.attributes.forEach { attribute ->
        csrBuilder.addAttribute(
            ASN1ObjectIdentifier(attribute.oid),
            attribute.valuesDer.map { valueDer ->
                ASN1Primitive.fromByteArray(valueDer.toByteArray())
            }.toTypedArray(),
        )
    }

    val csr = csrBuilder.build(
        CsrKeyContentSignerWrapper(spec.signingKey),
    )

    return CertificateSigningRequestDer(csr.encoded.toByteString())
}

internal actual suspend fun platformParseCertificateSigningRequest(
    csr: CertificateSigningRequestDer,
): X509DecodedCertificateSigningRequest {
    val certificationRequest = PKCS10CertificationRequest(csr.bytes.toByteArray())
    val jcaCsr = JcaPKCS10CertificationRequest(certificationRequest)

    return X509DecodedCertificateSigningRequest(
        csrData = X509CertificateSigningRequestData(
            subject = certificationRequest.subject.toX509Subject(),
            requestedExtensions = certificationRequest.decodeRequestedExtensions(),
            attributes = certificationRequest.attributes.map { it.toX509CsrAttribute() },
        ),
        publicKey = JWKKey.importPEM(jcaCsr.publicKey.toPemEncodedString()).getOrThrow(),
        signatureAlgorithmOid = certificationRequest.signatureAlgorithm.algorithm.id,
        signatureAlgorithmName = DefaultAlgorithmNameFinder().getAlgorithmName(certificationRequest.signatureAlgorithm),
    )
}

internal actual suspend fun platformIsCertificateSigningRequestSignatureValid(
    csr: CertificateSigningRequestDer,
): Boolean = try {
    val certificationRequest = PKCS10CertificationRequest(csr.bytes.toByteArray())
    certificationRequest.isSignatureValid(
        JcaContentVerifierProviderBuilder().build(
            JcaPKCS10CertificationRequest(certificationRequest).publicKey,
        )
    )
} catch (exception: Exception) {
    throw X509ValidationException(
        "CSR signature validation failed: ${exception.message}",
        exception,
    )
}

private class CsrKeyContentSignerWrapper(
    key: id.walt.crypto.keys.Key,
) : ContentSigner by KeyContentSignerWrapper(
    key = key,
    algorithmIdentifier = getCertificateSigningRequestAlgorithmIdentifier(key.keyType),
)

private fun getCertificateSigningRequestAlgorithmIdentifier(
    keyType: KeyType,
) = DefaultSignatureAlgorithmIdentifierFinder().run {
    when (keyType) {
        KeyType.secp256r1 -> find("SHA256withECDSA")
        KeyType.secp384r1 -> find("SHA384withECDSA")
        KeyType.secp521r1 -> find("SHA512withECDSA")
        KeyType.RSA -> find("SHA256withRSA")
        KeyType.RSA3072 -> find("SHA384withRSA")
        KeyType.RSA4096 -> find("SHA512withRSA")
        else -> throw IllegalArgumentException("Unsupported key type $keyType for CSR signing")
    }
}

private fun List<X509RequestedExtension>.toBcExtensions(): Extensions = Extensions(
    map { requestedExtension ->
        Extension(
            ASN1ObjectIdentifier(requestedExtension.oid),
            requestedExtension.critical,
            requestedExtension.toExtensionValueDer().toByteArray(),
        )
    }.toTypedArray()
)

private fun X509RequestedExtension.toExtensionValueDer() = when {
    subjectAlternativeNames != null -> GeneralNames(subjectAlternativeNames.toGeneralNameArray()).encoded.toByteString()
    keyUsages != null -> keyUsages.toBouncyCastleKeyUsage().encoded.toByteString()
    extendedKeyUsages != null -> ExtendedKeyUsage(
        extendedKeyUsages.map { eku -> KeyPurposeId.getInstance(ASN1ObjectIdentifier(eku.oid)) }.toTypedArray()
    ).encoded.toByteString()

    basicConstraints != null -> (
            if (basicConstraints.isCA) {
                BasicConstraints(basicConstraints.pathLengthConstraint)
            } else {
                BasicConstraints(false)
            }
            ).encoded.toByteString()

    valueDer != null -> valueDer
    else -> error("Unreachable requested extension representation")
}

private fun PKCS10CertificationRequest.decodeRequestedExtensions(): List<X509RequestedExtension> =
    attributes.filter {
        it.attrType == PKCSObjectIdentifiers.pkcs_9_at_extensionRequest
    }.flatMap { extensionRequestAttribute ->
        require(extensionRequestAttribute.attrValues.size() == 1) {
            "CSR extension request attribute must contain exactly one Extensions value"
        }
        val extensions = Extensions.getInstance(extensionRequestAttribute.attrValues.getObjectAt(0))
        extensions.extensionOIDs.map { oid ->
            extensions.getExtension(oid).toX509RequestedExtension()
        }
    }

private fun Extension.toX509RequestedExtension(): X509RequestedExtension = when (extnId.id) {
    X509V3ExtensionOID.SubjectAlternativeName.oid -> X509RequestedExtension.subjectAlternativeNames(
        names = GeneralNames.getInstance(parsedValue).toX509SubjectAlternativeNames(),
        critical = isCritical,
    )

    X509V3ExtensionOID.KeyUsage.oid -> X509RequestedExtension.keyUsages(
        usages = org.bouncycastle.asn1.x509.KeyUsage.getInstance(parsedValue).toCertificateKeyUsages(),
        critical = isCritical,
    )

    X509V3ExtensionOID.ExtendedKeyUsage.oid -> X509RequestedExtension.extendedKeyUsages(
        usages = ExtendedKeyUsage.getInstance(parsedValue).usages.map { usageOid ->
            listOf(
                X509ExtendedKeyUsage.ServerAuth,
                X509ExtendedKeyUsage.ClientAuth,
                X509ExtendedKeyUsage.CodeSigning,
                X509ExtendedKeyUsage.EmailProtection,
                X509ExtendedKeyUsage.TimeStamping,
            ).find { it.oid == usageOid.id } ?: X509ExtendedKeyUsage(usageOid.id)
        }.toSet(),
        critical = isCritical,
    )

    X509V3ExtensionOID.BasicConstraints.oid -> {
        val constraints = BasicConstraints.getInstance(parsedValue)
        X509RequestedExtension.basicConstraints(
            constraints = X509BasicConstraints(
                isCA = constraints.isCA,
                pathLengthConstraint = constraints.pathLenConstraint?.intValueExact() ?: 0,
            ),
            critical = isCritical,
        )
    }

    else -> X509RequestedExtension.raw(
        oid = extnId.id,
        critical = isCritical,
        valueDer = extnValue.octets.toByteString(),
    )
}

private fun Attribute.toX509CsrAttribute() = X509CsrAttribute(
    oid = attrType.id,
    valuesDer = (0 until attrValues.size()).map { index ->
        attrValues.getObjectAt(index).toASN1Primitive().encoded.toByteString()
    },
)

private fun X509Subject.toJcaX500Name(): X500Name {
    val builder = X500NameBuilder(BCStyle.INSTANCE)
    attributes.forEach { attribute ->
        builder.addRDN(ASN1ObjectIdentifier(attribute.oid), attribute.value)
    }
    return builder.build()
}

private fun X500Name.toX509Subject() = X509Subject(
    attributes = getRDNs().flatMap { rdn ->
        rdn.typesAndValues.map { typeAndValue ->
            X509SubjectAttribute(
                oid = typeAndValue.type.id,
                value = typeAndValue.value.toString(),
                shortName = typeAndValue.type.id.toX509SubjectShortName(),
            )
        }
    },
)

private fun String.toX509SubjectShortName(): String? = when (this) {
    X509SubjectAttributeOids.CommonName -> "CN"
    X509SubjectAttributeOids.Surname -> "SN"
    X509SubjectAttributeOids.SerialNumber -> "SERIALNUMBER"
    X509SubjectAttributeOids.CountryName -> "C"
    X509SubjectAttributeOids.LocalityName -> "L"
    X509SubjectAttributeOids.StateOrProvinceName -> "ST"
    X509SubjectAttributeOids.OrganizationName -> "O"
    X509SubjectAttributeOids.OrganizationalUnitName -> "OU"
    else -> null
}

private fun Set<X509SubjectAlternativeName>.toGeneralNameArray(): Array<GeneralName> =
    map { alternativeName ->
        when (alternativeName) {
            is X509SubjectAlternativeName.DnsName ->
                GeneralName(GeneralName.dNSName, alternativeName.value)

            is X509SubjectAlternativeName.Uri ->
                GeneralName(GeneralName.uniformResourceIdentifier, alternativeName.value)

            is X509SubjectAlternativeName.EmailAddress ->
                GeneralName(GeneralName.rfc822Name, alternativeName.value)

            is X509SubjectAlternativeName.IpAddress ->
                GeneralName(GeneralName.iPAddress, alternativeName.value)

            is X509SubjectAlternativeName.RegisteredId ->
                GeneralName(GeneralName.registeredID, alternativeName.value)

            is X509SubjectAlternativeName.OtherName -> {
                val vector = ASN1EncodableVector().apply {
                    add(ASN1ObjectIdentifier(alternativeName.typeId))
                    add(DERTaggedObject(true, 0, DERUTF8String(alternativeName.value)))
                }
                GeneralName(GeneralName.otherName, DERSequence(vector))
            }
        }
    }.toTypedArray()

private fun GeneralNames.toX509SubjectAlternativeNames(): Set<X509SubjectAlternativeName> =
    names.map { it.toX509SubjectAlternativeName() }.toSet()

private fun GeneralName.toX509SubjectAlternativeName(): X509SubjectAlternativeName = when (tagNo) {
    GeneralName.dNSName -> X509SubjectAlternativeName.DnsName(name.toString())
    GeneralName.uniformResourceIdentifier -> X509SubjectAlternativeName.Uri(name.toString())
    GeneralName.rfc822Name -> X509SubjectAlternativeName.EmailAddress(name.toString())
    GeneralName.iPAddress -> X509SubjectAlternativeName.IpAddress(name.toString())
    GeneralName.registeredID -> X509SubjectAlternativeName.RegisteredId(name.toString())
    GeneralName.otherName -> {
        val sequence = ASN1Sequence.getInstance(name)
        val typeId = ASN1ObjectIdentifier.getInstance(sequence.getObjectAt(0)).id
        val valueObject = ASN1TaggedObject.getInstance(sequence.getObjectAt(1)).baseObject.toASN1Primitive()
        val value = when (valueObject) {
            is DERUTF8String -> valueObject.string
            else -> valueObject.toString()
        }
        X509SubjectAlternativeName.OtherName(typeId = typeId, value = value)
    }

    else -> throw IllegalArgumentException("Unsupported CSR general name tag: $tagNo")
}

private fun java.security.PublicKey.toPemEncodedString(): String = StringWriter().use { writer ->
    JcaPEMWriter(writer).use { pemWriter ->
        pemWriter.writeObject(this)
    }
    writer.toString()
}
