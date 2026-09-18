package id.walt.certificate.x509.extension

/**
 * certificatePolicies ::= SEQUENCE SIZE (1..MAX) OF PolicyInformation
 *
 * PolicyInformation ::= SEQUENCE {
 *      policyIdentifier   CertPolicyId,
 *      policyQualifiers   SEQUENCE SIZE (1..MAX) OF PolicyQualifierInfo OPTIONAL }
 *
 * CertPolicyId ::= OBJECT IDENTIFIER
 *
 * Only the policy identifier OIDs are modelled - policy qualifiers (CPS pointers, user notices)
 * are not required by any profile this extension currently backs.
 */
interface CertificatePoliciesExtension : Extension {

    val policyOids: List<String>

    companion object {

        const val OID = "2.5.29.32"
        const val NAME = "Certificate Policies"

        fun MutableExtensionContainer.extensionCertificatePolicies(block: Builder.() -> Unit) {
            val builder = Builder()
            builder.block()
            this.extensions[OID] = builder
        }

        val ExtensionContainer.extensionCertificatePolicies: CertificatePoliciesExtension?
            get() {
                return this.extensions[OID] as? CertificatePoliciesExtension?
            }
    }

    data class Builder(
        override var critical: Boolean = false,
    ) : CertificatePoliciesExtension {
        override val oid: String = OID
        override val policyOids: MutableList<String> = mutableListOf()

        fun addPolicy(oid: String) {
            policyOids.add(oid)
        }
    }
}
