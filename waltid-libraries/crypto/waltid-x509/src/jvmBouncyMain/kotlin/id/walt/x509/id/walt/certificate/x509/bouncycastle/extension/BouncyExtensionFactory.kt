package id.walt.x509.id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.extension.*
import org.bouncycastle.asn1.ASN1BitString
import org.bouncycastle.asn1.ASN1Object
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.Extension as BouncyCastleExtension

object BouncyExtensionFactory {


    fun parseExtension(extension: BouncyCastleExtension): Extension = when (extension.extnId.id) {
        AuthorityKeyIdentifierExtension.OID -> BouncyAuthorityKeyIdentifierExtension(extension)
        BasicConstraintsExtension.OID -> BouncyBasicConstraintsExtension(extension)
        KeyUsageExtension.OID -> BouncyKeyUsageExtension(extension)
        ExtendedKeyUsageExtension.OID -> BouncyExtendedKeyUsageExtension(extension)
        SubjectAlternativeNameExtension.OID -> BouncySubjectAlternativeNameExtension(extension)
        IssuerAlternativeNameExtension.OID -> BouncyIssuerAlternativeNameExtension(extension)
        SubjectKeyIdentifierExtension.OID -> BouncySubjectKeyIdentifierExtension(extension)
        CrlDistributionPointsExtension.OID -> BouncyCrlDistributionPointsExtension(extension)
        else -> BouncyGenericExtension(extension)
    }

    fun createExtension(extension: Extension): BouncyCastleExtension = when (extension) {
        is BasicConstraintsExtension -> createExtension(
            extension,
            BouncyBasicConstraintsExtension.createExtension(extension)
        )

        is KeyUsageExtension -> createExtension(extension, BouncyKeyUsageExtension.createExtension(extension))
        is ExtendedKeyUsageExtension -> createExtension(
            extension,
            BouncyExtendedKeyUsageExtension.createExtension(extension)
        )

        is SubjectAlternativeNameExtension -> createExtension(
            extension,
            BouncySubjectAlternativeNameExtension.createExtension(extension)
        )

        is IssuerAlternativeNameExtension -> createExtension(
            extension,
            BouncyIssuerAlternativeNameExtension.createExtension(extension)
        )

        is CrlDistributionPointsExtension -> createExtension(
            extension,
            BouncyCrlDistributionPointsExtension.createExtension(extension)
        )


        else -> error("Unknown BouncyCastleExtension type: ${extension::class.qualifiedName}")
    }

    fun createSubjectKeyIdentifierExtension(
        extension: SubjectKeyIdentifierExtension,
        subjectPublicKey: ASN1BitString
    ): BouncyCastleExtension =
        createExtension(extension, BouncySubjectKeyIdentifierExtension.createExtension(extension, subjectPublicKey))

    fun createExtension(extension: Extension, extensionData: ASN1Object): BouncyCastleExtension {
        val id = ASN1ObjectIdentifier(extension.oid)
        return BouncyCastleExtension(id, extension.critical, extensionData.encoded)
    }
}