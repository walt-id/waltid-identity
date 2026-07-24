package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.Asn1PrimitiveOctetString
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import id.walt.certificate.x509.extension.*
import id.walt.certificate.x509.signum.SignumPublicKeyInfo

object SignumExtensionFactory {

    fun parseExtension(extensionElement: Asn1Element): Extension =
        parseExtension(X509CertificateExtension.decodeFromTlv(extensionElement.asSequence()))

    fun parseExtension(extension: X509CertificateExtension): Extension =
        when (extension.oid.toString()) {
            BasicConstraintsExtension.OID -> SignumBasicConstraintsExtension(extension)
            KeyUsageExtension.OID -> SignumKeyUsageExtension(extension)
            //ExtendedKeyUsageExtension.OID -> BouncyExtendedKeyUsageExtension(extension)
            SubjectAlternativeNameExtension.OID -> SignumSubjectAlternativeNameExtension(extension)
            AuthorityKeyIdentifierExtension.OID -> SignumAuthorityKeyIdentifierExtension(extension)
            //IssuerAlternativeNameExtension.OID -> BouncyIssuerAlternativeNameExtension(extension)
            SubjectKeyIdentifierExtension.OID -> SignumSubjectKeyIdentifierExtension(extension)
            //CrlDistributionPointsExtension.OID -> BouncyCrlDistributionPointsExtension(extension)
            else -> SignumGenericExtension(extension)
        }

    fun createExtension(extension: Extension): X509CertificateExtension = when (extension.oid) {
        BasicConstraintsExtension.OID -> createExtension(
            extension,
            SignumBasicConstraintsExtension.createExtension(extension as BasicConstraintsExtension)
        )

        KeyUsageExtension.OID -> createExtension(
            extension,
            SignumKeyUsageExtension.createExtension(extension as KeyUsageExtension)
        )
        //is ExtendedKeyUsageExtension -> id.walt.x509.id.walt.certificate.x509.bouncycastle.extension.BouncyExtensionFactory.createExtension(
        //   extension,
        //  BouncyExtendedKeyUsageExtension.createExtension(extension)
        //)

        SubjectAlternativeNameExtension.OID -> createExtension(
            extension,
            SignumSubjectAlternativeNameExtension.createExtension(extension as SubjectAlternativeNameExtension)
        )

        //is IssuerAlternativeNameExtension -> id.walt.x509.id.walt.certificate.x509.bouncycastle.extension.BouncyExtensionFactory.createExtension(
        //    extension,
        //    BouncyIssuerAlternativeNameExtension.createExtension(extension)
        //)

        //is CrlDistributionPointsExtension -> id.walt.x509.id.walt.certificate.x509.bouncycastle.extension.BouncyExtensionFactory.createExtension(
        //    extension,
        //   BouncyCrlDistributionPointsExtension.createExtension(extension)
        // )


        else -> error("Unknown Signum Extension type OID: ${extension.oid}")
    }

    fun createSubjectKeyIdentifierExtension(
        extension: Extension,
        subjectPublicKeyInfo: CryptoPublicKey
    ): X509CertificateExtension {
        check(extension.oid == SubjectKeyIdentifierExtension.OID) { "Extension OID must be ${SubjectKeyIdentifierExtension.OID}" }
        return createExtension(
            extension,
            SignumSubjectKeyIdentifierExtension.createExtension(
                extension as SubjectKeyIdentifierExtension,
                SignumPublicKeyInfo.ofCryptoPublicKey(subjectPublicKeyInfo)
            )
        )
    }

    private fun createExtension(
        extension: Extension,
        extensionData: Asn1PrimitiveOctetString
    ): X509CertificateExtension =
        X509CertificateExtension(
            ObjectIdentifier(extension.oid),
            extension.critical,
            extensionData
        )
}