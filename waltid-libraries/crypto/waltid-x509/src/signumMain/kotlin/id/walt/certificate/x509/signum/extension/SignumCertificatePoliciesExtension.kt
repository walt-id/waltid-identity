package id.walt.certificate.x509.signum.extension

import at.asitplus.signum.indispensable.asn1.Asn1EncapsulatingOctetString
import at.asitplus.signum.indispensable.asn1.ObjectIdentifier
import at.asitplus.signum.indispensable.asn1.encoding.Asn1
import at.asitplus.signum.indispensable.pki.X509CertificateExtension
import id.walt.certificate.x509.extension.CertificatePoliciesExtension

/**
 * certificatePolicies ::= SEQUENCE SIZE (1..MAX) OF PolicyInformation
 * PolicyInformation ::= SEQUENCE { policyIdentifier CertPolicyId, policyQualifiers ... OPTIONAL }
 *
 * Only policyIdentifier is modelled - see [CertificatePoliciesExtension].
 */
class SignumCertificatePoliciesExtension(extension: X509CertificateExtension) :
    SignumExtension(extension),
    CertificatePoliciesExtension {

    override val policyOids: List<String>
        get() = extension.content.asSequence().children.map { policyInformation ->
            val policyIdentifier = policyInformation.asSequence().children.toList().first()
            ObjectIdentifier.decodeFromTlv(policyIdentifier.asPrimitive()).toString()
        }

    companion object {
        fun createExtension(ext: CertificatePoliciesExtension): Asn1EncapsulatingOctetString =
            Asn1.OctetStringEncapsulating {
                +Asn1.Sequence {
                    ext.policyOids.forEach { oid ->
                        +Asn1.Sequence {
                            +ObjectIdentifier(oid)
                        }
                    }
                }
            }
    }
}
