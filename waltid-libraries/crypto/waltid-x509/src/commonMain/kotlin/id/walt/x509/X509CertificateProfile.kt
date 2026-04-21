package id.walt.x509

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Duration

/**
 * Identifier for a reusable X.509 certificate profile.
 */
@Serializable
@JvmInline
value class X509ProfileId(val value: String) {
    init {
        require(value.isNotBlank()) { "X509 profile id must not be blank" }
    }

    override fun toString() = value
}

/**
 * Lightweight, generic profile foundation for future X.509 issuance flows.
 */
data class X509CertificateProfile(
    val profileId: X509ProfileId,
    val subject: X509Subject? = null,
    val subjectAlternativeNames: Set<X509SubjectAlternativeName> = emptySet(),
    val keyUsages: Set<X509KeyUsage> = emptySet(),
    val extendedKeyUsages: Set<X509ExtendedKeyUsage> = emptySet(),
    val basicConstraints: X509BasicConstraints? = null,
    val validityPolicy: X509ValidityPolicy? = null,
    val description: String? = null,
) {
    @Deprecated(
        message = "Use profileId",
        replaceWith = ReplaceWith("profileId"),
    )
    val id: X509ProfileId
        get() = profileId
}

/**
 * Generic representation of a certificate subject as an ordered list of X.500 attributes.
 */
@Serializable
data class X509Subject(
    val attributes: List<X509SubjectAttribute>,
) {
    init {
        require(attributes.isNotEmpty()) { "X509 subject must contain at least one attribute" }
    }

    fun getAttributeValues(oid: String): List<String> =
        attributes.filter { it.oid == oid }.map { it.value }

    fun getFirstAttributeValue(oid: String): String? = getAttributeValues(oid).firstOrNull()
}

@Serializable
data class X509SubjectAttribute(
    val oid: String,
    val value: String,
    val shortName: String? = null,
) {
    init {
        require(oid.isNotBlank()) { "X509 subject attribute OID must not be blank" }
        require(value.isNotBlank()) { "X509 subject attribute value must not be blank" }
    }
}

/**
 * Lightweight SAN model with the most common name forms needed by certificate profiles.
 */
@Serializable
sealed interface X509SubjectAlternativeName {
    val value: String

    @Serializable
    @SerialName("dns-name")
    data class DnsName(override val value: String) : X509SubjectAlternativeName

    @Serializable
    @SerialName("uri")
    data class Uri(override val value: String) : X509SubjectAlternativeName

    @Serializable
    @SerialName("email-address")
    data class EmailAddress(override val value: String) : X509SubjectAlternativeName

    @Serializable
    @SerialName("ip-address")
    data class IpAddress(override val value: String) : X509SubjectAlternativeName

    @Serializable
    @SerialName("registered-id")
    data class RegisteredId(override val value: String) : X509SubjectAlternativeName

    @Serializable
    @SerialName("other-name")
    data class OtherName(
        val typeId: String,
        override val value: String,
    ) : X509SubjectAlternativeName
}

/**
 * Generic representation of an extended key usage entry.
 */
@Serializable
data class X509ExtendedKeyUsage(
    val oid: String,
    val name: String? = null,
) {
    init {
        require(oid.isNotBlank()) { "Extended key usage OID must not be blank" }
    }

    companion object {
        val Any = X509ExtendedKeyUsage("2.5.29.37.0", "anyExtendedKeyUsage")
        val ServerAuth = X509ExtendedKeyUsage("1.3.6.1.5.5.7.3.1", "serverAuth")
        val ClientAuth = X509ExtendedKeyUsage("1.3.6.1.5.5.7.3.2", "clientAuth")
        val CodeSigning = X509ExtendedKeyUsage("1.3.6.1.5.5.7.3.3", "codeSigning")
        val EmailProtection = X509ExtendedKeyUsage("1.3.6.1.5.5.7.3.4", "emailProtection")
        val TimeStamping = X509ExtendedKeyUsage("1.3.6.1.5.5.7.3.8", "timeStamping")
        val OcspSigning = X509ExtendedKeyUsage("1.3.6.1.5.5.7.3.9", "OCSPSigning")
    }
}

/**
 * Simple validity policy input for future issuance flows.
 */
data class X509ValidityPolicy(
    val maximumValidity: Duration,
    val backdate: Duration = Duration.ZERO,
    val renewBeforeExpiry: Duration? = null,
    val alignWithIssuerValidity: Boolean = true,
) {
    init {
        require(!maximumValidity.isNegative()) { "maximumValidity must not be negative" }
        require(!backdate.isNegative()) { "backdate must not be negative" }
        require(renewBeforeExpiry == null || !renewBeforeExpiry.isNegative()) {
            "renewBeforeExpiry must not be negative"
        }
    }
}

object X509SubjectAttributeOids {
    const val CommonName = "2.5.4.3"
    const val Surname = "2.5.4.4"
    const val SerialNumber = "2.5.4.5"
    const val CountryName = "2.5.4.6"
    const val LocalityName = "2.5.4.7"
    const val StateOrProvinceName = "2.5.4.8"
    const val OrganizationName = "2.5.4.10"
    const val OrganizationalUnitName = "2.5.4.11"
}

object X509SubjectAttributes {
    fun commonName(value: String) =
        X509SubjectAttribute(X509SubjectAttributeOids.CommonName, value, shortName = "CN")

    fun country(value: String) =
        X509SubjectAttribute(X509SubjectAttributeOids.CountryName, value, shortName = "C")

    fun locality(value: String) =
        X509SubjectAttribute(X509SubjectAttributeOids.LocalityName, value, shortName = "L")

    fun stateOrProvince(value: String) =
        X509SubjectAttribute(X509SubjectAttributeOids.StateOrProvinceName, value, shortName = "ST")

    fun organization(value: String) =
        X509SubjectAttribute(X509SubjectAttributeOids.OrganizationName, value, shortName = "O")

    fun organizationalUnit(value: String) =
        X509SubjectAttribute(X509SubjectAttributeOids.OrganizationalUnitName, value, shortName = "OU")
}

fun x509SubjectOf(vararg attributes: X509SubjectAttribute) = X509Subject(attributes.toList())
