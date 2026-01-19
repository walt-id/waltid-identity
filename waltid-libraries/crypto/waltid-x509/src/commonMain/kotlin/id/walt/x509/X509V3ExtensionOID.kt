package id.walt.x509

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Standard X.509 v3 certificate extension OIDs.
 *
 * This enum provides a curated set of commonly used extension OIDs. Entries use
 * human-readable names and serialize to their dotted-decimal OID string.
 *
 * Note: This list is intentionally not exhaustive.
 */
@Serializable
enum class X509V3ExtensionOID(val oid: String) {

    @SerialName("2.5.29.35")
    AuthorityKeyIdentifier("2.5.29.35"),

    @SerialName("2.5.29.14")
    SubjectKeyIdentifier("2.5.29.14"),

    @SerialName("2.5.29.15")
    KeyUsage("2.5.29.15"),

    @SerialName("2.5.29.32")
    CertificatePolicies("2.5.29.32"),

    @SerialName("2.5.29.33")
    PolicyMappings("2.5.29.33"),

    @SerialName("2.5.29.17")
    SubjectAlternativeName("2.5.29.17"),

    @SerialName("2.5.29.18")
    IssuerAlternativeName("2.5.29.18"),

    @SerialName("2.5.29.9")
    SubjectDirectoryAttributes("2.5.29.9"),

    @SerialName("2.5.29.19")
    BasicConstraints("2.5.29.19"),

    @SerialName("2.5.29.30")
    NameConstraints("2.5.29.30"),

    @SerialName("2.5.29.36")
    PolicyConstraints("2.5.29.36"),

    @SerialName("2.5.29.37")
    ExtendedKeyUsage("2.5.29.37"),

    @SerialName("2.5.29.31")
    CrlDistributionPoints("2.5.29.31"),

    @SerialName("2.5.29.54")
    InhibitAnyPolicy("2.5.29.54"),

    @SerialName("2.5.29.46")
    FreshestCrl("2.5.29.46");

    companion object {
        /**
         * Resolve a known enum entry from a dotted-decimal OID string.
         *
         * Returns null when the OID is not part of this curated set.
         */
        fun fromOID(oid: String) = entries.firstOrNull { it.oid == oid }
    }
}
