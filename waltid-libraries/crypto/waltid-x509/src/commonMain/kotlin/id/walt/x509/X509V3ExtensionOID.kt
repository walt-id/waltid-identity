package id.walt.x509

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Standard X.509 v3 Certificate Extension OIDs.
 *
 * Enum entries use human-readable names and serialize as their OID string.
 *
 * This is **not an exhaustive list** of every single OID out there (nor is it meant to be).
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
        fun fromOID(oid: String) = entries.firstOrNull { it.oid == oid }
    }
}
