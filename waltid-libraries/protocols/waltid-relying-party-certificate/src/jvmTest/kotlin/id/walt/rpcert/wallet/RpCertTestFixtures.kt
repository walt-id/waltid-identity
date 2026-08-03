package id.walt.rpcert.wallet

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.dcql.models.CredentialFormat
import id.walt.rpcert.issuance.RelyingPartyRegistrationCertificateIssuer
import id.walt.rpcert.models.MultiLangString
import id.walt.rpcert.models.RegistrationCertificateCredential
import id.walt.rpcert.models.RelyingPartyRegistrationCertificate
import id.walt.rpcert.models.SupervisoryAuthority
import id.walt.x509.GenericX509CertificateBuilder
import id.walt.x509.GenericX509CertificateProfileData
import id.walt.x509.X509DistinguishedName
import kotlin.time.Clock

/** Shared fixtures for wallet-side registration certificate tests. */
object RpCertTestFixtures {

    fun sampleCertificate(
        credentials: List<RegistrationCertificateCredential> = listOf(
            RegistrationCertificateCredential(format = CredentialFormat.DC_SD_JWT),
        ),
        iat: Long = Clock.System.now().epochSeconds,
        exp: Long? = null,
    ): RelyingPartyRegistrationCertificate = RelyingPartyRegistrationCertificate(
        name = "Example Relying Party",
        sub = "did:example:rp",
        country = "US",
        registryUri = "https://registry.example.com/rp",
        srvDescription = listOf(listOf(MultiLangString("en", "Example service"))),
        entitlements = listOf("entitlement1"),
        privacyPolicy = "https://example.com/privacy",
        supervisoryAuthority = SupervisoryAuthority(email = "authority@example.com"),
        iat = iat,
        exp = exp,
        purpose = listOf(MultiLangString("en", "Identity verification")),
        credentials = credentials,
    )

    suspend fun selfSignedLeafCertificateX5c(key: JWKKey): List<String> {
        val bundle = GenericX509CertificateBuilder().build(
            profileData = GenericX509CertificateProfileData(
                subjectName = X509DistinguishedName(commonName = "Example Leaf", country = "US"),
                isCertificateAuthority = false,
            ),
            subjectPublicKey = key.getPublicKey(),
            signingKey = key,
        )
        return listOf(bundle.certificateDer.bytes.toByteArray().encodeToBase64())
    }

    /** A self-signed leaf certificate signed with [key] (or a freshly generated one), issued via [RelyingPartyRegistrationCertificateIssuer]. */
    suspend fun signedCertificateJwt(
        key: JWKKey? = null,
        payload: RelyingPartyRegistrationCertificate = sampleCertificate(),
    ): String {
        val signingKey = key ?: JWKKey.generate(KeyType.secp256r1)
        val x5c = selfSignedLeafCertificateX5c(signingKey)
        return RelyingPartyRegistrationCertificateIssuer.issue(signingKey, x5c, payload)
    }
}
