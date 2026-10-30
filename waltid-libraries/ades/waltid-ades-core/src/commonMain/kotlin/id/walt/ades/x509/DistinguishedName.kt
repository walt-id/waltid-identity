package id.walt.ades.x509

import id.walt.ades.asn1.Der
import id.walt.ades.asn1.DerElement
import id.walt.ades.asn1.Oids

/** X.500 Name kept as raw DER (for exact matching) with an RFC 4514-style rendering. */
class DistinguishedName(val encoded: ByteArray) {
    private val rdns: List<List<Pair<String, String>>> = Der.parse(encoded).children.map { rdn ->
        rdn.children.map { atv -> atv.child(0).asOid() to runCatching { atv.child(1).asString() }.getOrElse { "#" + atv.child(1).encoded.toHexString() } }
    }

    val attributes: List<Pair<String, String>> get() = rdns.flatten()

    fun get(oid: String): String? = attributes.firstOrNull { it.first == oid }?.second
    val commonName: String? get() = get(Oids.CN)

    /** RFC 4514 order (most specific first). */
    override fun toString(): String = rdns.reversed().joinToString(",") { rdn ->
        rdn.joinToString("+") { (oid, value) -> "${shortNames[oid] ?: oid}=${escape(value)}" }
    }

    /** Equality on DER bytes; X.509 name matching per RFC 5280 7.1 is approximated by a normalized comparison fallback. */
    fun matches(other: DistinguishedName): Boolean =
        encoded.contentEquals(other.encoded) || normalized() == other.normalized()

    private fun normalized(): List<List<Pair<String, String>>> =
        rdns.map { rdn -> rdn.map { it.first to it.second.trim().lowercase().replace(Regex("\\s+"), " ") }.sortedBy { it.first } }

    override fun equals(other: Any?): Boolean = other is DistinguishedName && matches(other)
    override fun hashCode(): Int = normalized().hashCode()

    private fun escape(v: String) = v.replace("\\", "\\\\").replace(",", "\\,").replace("+", "\\+").replace("\"", "\\\"")

    companion object {
        private val shortNames = mapOf(
            Oids.CN to "CN", Oids.C to "C", Oids.L to "L", Oids.ST to "ST", Oids.O to "O", Oids.OU to "OU",
            Oids.STREET to "STREET", Oids.SERIAL_NUMBER to "SERIALNUMBER", Oids.SURNAME to "SURNAME",
            Oids.GIVEN_NAME to "GIVENNAME", Oids.TITLE to "T", Oids.EMAIL to "E", Oids.DC to "DC", Oids.UID to "UID",
            Oids.ORGANIZATION_IDENTIFIER to "organizationIdentifier", Oids.DN_QUALIFIER to "DNQ", Oids.PSEUDONYM to "PSEUDONYM",
        )

        fun of(element: DerElement) = DistinguishedName(element.encoded)
    }
}
