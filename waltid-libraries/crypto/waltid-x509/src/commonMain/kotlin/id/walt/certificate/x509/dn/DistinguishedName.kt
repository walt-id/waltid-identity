package id.walt.certificate.x509.dn

class DistinguishedName(val rdnList: List<RelativeDistinguishedName>) {

    typealias RelativeDistinguishedName = List<AttributeTypeAndValue>

    /**
     * Should have the same result as Bouncy Castle implementation
     * org.bouncycastle.asn1.x500.X500Name
     */
    override fun toString(): String {
        return rdnList.map {
            it.joinToString(separator = "+")
        }.joinToString(separator = ",")
    }

    companion object {
        fun ofString(str: String): DistinguishedName =
            DnStringParser(str).parse()
    }
}