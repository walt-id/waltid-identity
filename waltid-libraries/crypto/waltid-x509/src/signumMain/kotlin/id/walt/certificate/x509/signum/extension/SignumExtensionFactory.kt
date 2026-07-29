package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.Asn1EncapsulatingOctetString
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
            ExtendedKeyUsageExtension.OID -> SignumExtendedKeyUsageExtension(extension)
            SubjectAlternativeNameExtension.OID -> SignumSubjectAlternativeNameExtension(extension)
            AuthorityKeyIdentifierExtension.OID -> SignumAuthorityKeyIdentifierExtension(extension)
            IssuerAlternativeNameExtension.OID -> SignumIssuerAlternativeNameExtension(extension)
            SubjectKeyIdentifierExtension.OID -> SignumSubjectKeyIdentifierExtension(extension)
            CrlDistributionPointsExtension.OID -> SignumCrlDistributionPointsExtension(extension)
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

        ExtendedKeyUsageExtension.OID -> createExtension(
            extension,
            SignumExtendedKeyUsageExtension.createExtension(extension as ExtendedKeyUsageExtension)
        )

        SubjectAlternativeNameExtension.OID -> createExtension(
            extension,
            SignumSubjectAlternativeNameExtension.createExtension(extension as SubjectAlternativeNameExtension)
        )

        IssuerAlternativeNameExtension.OID -> createExtension(
            extension,
            SignumIssuerAlternativeNameExtension.createExtension(extension as IssuerAlternativeNameExtension)
        )

        CrlDistributionPointsExtension.OID -> createExtension(
            extension,
            SignumCrlDistributionPointsExtension.createExtension(extension as CrlDistributionPointsExtension)
        )


        else -> error("Unknown Signum Extension type OID: ${extension.oid}")
    }

    fun createAuthorityKeyIdentifierExtension(
        extension: Extension,
        authorityPublicKeyInfo: CryptoPublicKey
    ): X509CertificateExtension {
        check(extension.oid == AuthorityKeyIdentifierExtension.OID) { "Extension OID must be ${AuthorityKeyIdentifierExtension.OID}" }
        return createExtension(
            extension,
            SignumAuthorityKeyIdentifierExtension.createExtension(
                extension as AuthorityKeyIdentifierExtension,
                SignumPublicKeyInfo.ofCryptoPublicKey(authorityPublicKeyInfo)
            )
        )
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

    private fun createExtension(
        extension: Extension,
        extensionData: Asn1EncapsulatingOctetString
    ): X509CertificateExtension =
        X509CertificateExtension(
            ObjectIdentifier(extension.oid),
            extension.critical,
            extensionData
        )

}