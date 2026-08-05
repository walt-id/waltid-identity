package id.walt.x509

import id.walt.crypto.keys.Key
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key as Crypto2Key
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
    @Deprecated("Use crypto2PublicKey().", ReplaceWith("crypto2PublicKey()"))
    val publicKey: Key,
) {
    suspend fun crypto2PublicKey(): EncodedKey.Jwk = publicKey.toCrypto2PublicJwk()
}

class GenericX509CertificateBuilder {
    @Deprecated("Use buildDer with crypto2 keys and an explicit SignatureAlgorithm.")
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
        profileData.validate()
        return platformBuildGenericX509Certificate(
            profileData = profileData,
            subjectPublicKey = subjectPublicKey,
            signingKey = signingKey,
        )
    }

    suspend fun buildDer(
        profileData: GenericX509CertificateProfileData,
        subjectPublicKey: Crypto2Key,
        signingKey: Crypto2Key,
        signatureAlgorithm: SignatureAlgorithm,
    ): CertificateDer {
        profileData.validate()
        return buildCrypto2GenericX509CertificateDer(
            profileData = profileData,
            subjectPublicKey = subjectPublicKey,
            signingKey = signingKey,
            signatureAlgorithm = signatureAlgorithm,
        )
    }
}

private fun GenericX509CertificateProfileData.validate() {
    require(validityPeriod.notAfter > validityPeriod.notBefore) {
        "Certificate notAfter must be after notBefore."
    }
    pathLengthConstraint?.let {
        require(isCertificateAuthority) {
            "pathLengthConstraint can only be used for CA certificates."
        }
        require(it >= 0) {
            "pathLengthConstraint must not be negative."
        }
    }
    crlDistributionPointUri?.let { requireHttpUrl(it, "CRL distribution point URI") }
}

@Deprecated("Use GenericX509CertificateBuilder.buildDer with crypto2 keys and an explicit SignatureAlgorithm.")
expect suspend fun platformBuildGenericX509Certificate(
    profileData: GenericX509CertificateProfileData,
    subjectPublicKey: Key,
    signingKey: Key,
): GenericX509CertificateBundle
