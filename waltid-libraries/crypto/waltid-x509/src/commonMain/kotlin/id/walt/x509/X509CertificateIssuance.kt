package id.walt.x509

import id.walt.crypto.keys.Key
import kotlinx.serialization.Serializable

@Serializable
data class X509CertificateBuildData(
    val subject: X509Subject,
    val validityPeriod: X509ValidityPeriod,
    val subjectAlternativeNames: Set<X509SubjectAlternativeName> = emptySet(),
    val issuerAlternativeNames: Set<X509SubjectAlternativeName> = emptySet(),
    val crlDistributionPointUri: String? = null,
)

sealed interface X509CertificateIssuanceSpec {
    val profileId: X509ProfileId
    val certificateData: X509CertificateBuildData
}

data class X509SelfSignedCertificateIssuanceSpec(
    override val profileId: X509ProfileId,
    override val certificateData: X509CertificateBuildData,
    val signingKey: Key,
) : X509CertificateIssuanceSpec

data class X509IssuerSignedCertificateIssuanceSpec(
    override val profileId: X509ProfileId,
    override val certificateData: X509CertificateBuildData,
    val publicKey: Key,
    val issuer: X509CertificateSignerSpec,
) : X509CertificateIssuanceSpec

data class X509CertificateSignerSpec(
    val profileId: X509ProfileId,
    val certificateData: X509CertificateBuildData,
    val signingKey: Key,
)

@Serializable
data class X509IssuedCertificateData(
    val subject: X509Subject,
    val issuer: X509Subject,
    val validityPeriod: X509ValidityPeriod,
    val subjectAlternativeNames: Set<X509SubjectAlternativeName> = emptySet(),
    val issuerAlternativeNames: Set<X509SubjectAlternativeName> = emptySet(),
    val keyUsages: Set<X509KeyUsage> = emptySet(),
    val extendedKeyUsages: Set<X509ExtendedKeyUsage> = emptySet(),
    val basicConstraints: X509BasicConstraints? = null,
    val crlDistributionPointUri: String? = null,
)

data class X509IssuedCertificateBundle(
    val profile: X509CertificateProfile,
    val certificateDer: CertificateDer,
    val certificateData: X509IssuedCertificateData,
) {
    val certificatePem: String
        get() = certificateDer.toPEMEncodedString()
}

fun interface X509CertificateProfileResolver {
    fun resolve(profileId: X509ProfileId): X509CertificateProfile?
}

class StaticX509CertificateProfileRegistry(
    profiles: Collection<X509CertificateProfile>,
) : X509CertificateProfileResolver {
    private val profilesById = profiles.associateBy { it.profileId }

    override fun resolve(profileId: X509ProfileId): X509CertificateProfile? = profilesById[profileId]

    fun supportedProfiles(): Set<X509CertificateProfile> = profilesById.values.toSet()
}

interface X509ProfileDrivenIssuer {
    suspend fun issue(spec: X509CertificateIssuanceSpec): X509IssuedCertificateBundle
}
