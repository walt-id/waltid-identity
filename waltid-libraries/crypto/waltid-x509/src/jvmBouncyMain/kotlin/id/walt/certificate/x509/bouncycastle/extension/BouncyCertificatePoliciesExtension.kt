package id.walt.certificate.x509.bouncycastle.extension

import id.walt.certificate.x509.extension.CertificatePoliciesExtension
import org.bouncycastle.asn1.ASN1Object
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.CertificatePolicies
import org.bouncycastle.asn1.x509.PolicyInformation
import org.bouncycastle.asn1.x509.Extension as BouncyCastleExtension

class BouncyCertificatePoliciesExtension(extension: BouncyCastleExtension) : BouncyExtension(extension),
    CertificatePoliciesExtension {

    private val policies = CertificatePolicies.getInstance(extension.parsedValue)

    override val policyOids: List<String>
        get() = policies.policyInformation.map { it.policyIdentifier.id }

    companion object {
        fun createExtension(ext: CertificatePoliciesExtension): ASN1Object {
            val policyInformation = ext.policyOids.map { oid ->
                PolicyInformation(ASN1ObjectIdentifier(oid))
            }.toTypedArray()
            return CertificatePolicies(policyInformation)
        }
    }
}
