package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.asn1.Asn1Element
import at.asitplus.signum.indispensable.asn1.Asn1PrimitiveOctetString
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import id.walt.certificate.x509.extension.Extension
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension

object SignumExtensionFactory {

    fun parseExtension(extensionElement: Asn1Element): Extension =
        extensionElement.let { element ->
            val extension = X509CertificateExtension.decodeFromTlv(element.asSequence())
            when (extension.oid.toString()) {
                //BasicConstraintsExtension.OID -> BouncyBasicConstraintsExtension(extension)
                //KeyUsageExtension.OID -> BouncyKeyUsageExtension(extension)
                //ExtendedKeyUsageExtension.OID -> BouncyExtendedKeyUsageExtension(extension)
                SubjectAlternativeNameExtension.OID -> SignumSubjectAlternativeNameExtension(extension)
                //IssuerAlternativeNameExtension.OID -> BouncyIssuerAlternativeNameExtension(extension)
                //SubjectKeyIdentifierExtension.OID -> BouncySubjectKeyIdentifierExtension(extension)
                //CrlDistributionPointsExtension.OID -> BouncyCrlDistributionPointsExtension(extension)
                else -> SignumGenericExtension(extension)
            }
        }

    fun createExtension(extension: Extension): X509CertificateExtension = when (extension.oid) {
        //is BasicConstraintsExtension -> id.walt.x509.id.walt.certificate.x509.bouncycastle.extension.BouncyExtensionFactory.createExtension(
        //    extension,
        //    BouncyBasicConstraintsExtension.createExtension(extension)
        // )

        //is KeyUsageExtension -> id.walt.x509.id.walt.certificate.x509.bouncycastle.extension.BouncyExtensionFactory.createExtension(
        //    extension,
        //    BouncyKeyUsageExtension.createExtension(extension)
        // )
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