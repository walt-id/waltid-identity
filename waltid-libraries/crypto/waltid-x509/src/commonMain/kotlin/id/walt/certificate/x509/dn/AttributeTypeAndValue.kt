package id.walt.certificate.x509.dn

import id.walt.certificate.x509.dn.RelativeDistinguishedNameEscapingUtil.escapeString

class AttributeTypeAndValue(
    val type: AttributeType,
    val value: String
) {
    /**
     * Should have the same result as Bouncy Castle implementation
     * org.bouncycastle.asn1.x500.RDN
     */
    override fun toString(): String {
        val string =  "${type.name.uppercase()}=${escapeString(value)}"
        return string
    }
}