package id.walt.x509

import id.walt.crypto.keys.Key
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

data class GenericX509CertificateProfileData(
    val subjectName: X509DistinguishedName,
    val issuerName: X509DistinguishedName? = null,
    val validityPeriod: X509ValidityPeriod = X509ValidityPeriod(
        notBefore = Clock.System.now(),
        notAfter = Clock.System.now().plus(365.days),
    ),
    val isCertificateAuthority: Boolean = false,
    val pathLengthConstraint: Int? = null,
    val keyUsage: Set<X509KeyUsage> = emptySet(),
    val extendedKeyUsageOids: Set<String> = emptySet(),
    val subjectAlternativeNames: X509SubjectAlternativeNames? = null,
    val crlDistributionPointUri: String? = null,
)

data class GenericX509CertificateBundle(
    val certificateDer: CertificateDer,
    val decodedCertificate: GenericX509DecodedCertificate,
)

data class GenericX509DecodedCertificate(
    val subjectName: X509DistinguishedName,
    val issuerName: X509DistinguishedName,
    val validityPeriod: X509ValidityPeriod,
    val isCertificateAuthority: Boolean,
    val pathLengthConstraint: Int?,
    val keyUsage: Set<X509KeyUsage>,
    val extendedKeyUsageOids: Set<String>,
    val subjectAlternativeNames: X509SubjectAlternativeNames? = null,
    val crlDistributionPointUri: String? = null,
    val publicKey: Key,
)

class GenericX509CertificateBuilder {
    suspend fun build(
        profileData: GenericX509CertificateProfileData,
        subjectPublicKey: Key,
        signingKey: Key,
    ): GenericX509CertificateBundle {
        require(!subjectPublicKey.hasPrivateKey) {
            "Certificate subject public key must not contain private key material."
        }
        require(signingKey.hasPrivateKey) {
            "Certificate signing key must contain a private key."
        }
        require(profileData.validityPeriod.notAfter > profileData.validityPeriod.notBefore) {
            "Certificate notAfter must be after notBefore."
        }
        profileData.pathLengthConstraint?.let {
            require(profileData.isCertificateAuthority) {
                "pathLengthConstraint can only be used for CA certificates."
            }
            require(it >= 0) {
                "pathLengthConstraint must not be negative."
            }
        }
        return platformBuildGenericX509Certificate(
            profileData = profileData,
            subjectPublicKey = subjectPublicKey,
            signingKey = signingKey,
        )
    }
}

expect suspend fun platformBuildGenericX509Certificate(
    profileData: GenericX509CertificateProfileData,
    subjectPublicKey: Key,
    signingKey: Key,
): GenericX509CertificateBundle
