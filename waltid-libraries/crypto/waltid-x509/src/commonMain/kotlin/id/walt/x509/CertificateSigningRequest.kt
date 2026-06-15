package id.walt.x509

import id.walt.crypto.keys.Key
import kotlinx.io.bytestring.ByteString
import kotlin.io.encoding.Base64

data class CertificateSigningRequestDer(
    val bytes: ByteString,
) {

    constructor(bytes: ByteArray) : this(ByteString(bytes))

    fun toPEMEncodedString() = "$PEM_HEADER\r\n" +
            Base64.Pem.encode(bytes.toByteArray()) +
            "\r\n$PEM_FOOTER"

    companion object {
        private const val PEM_HEADER = "-----BEGIN CERTIFICATE REQUEST-----"
        private const val PEM_FOOTER = "-----END CERTIFICATE REQUEST-----"

        fun fromPEMEncodedString(
            pemEncodedCertificateSigningRequest: String,
        ): CertificateSigningRequestDer {
            val trimmedPem = pemEncodedCertificateSigningRequest.trim()
            require(trimmedPem.startsWith(PEM_HEADER)) {
                "CSR PEM header not found."
            }
            require(trimmedPem.endsWith(PEM_FOOTER)) {
                "CSR PEM footer not found."
            }

            val base64Payload = trimmedPem
                .removePrefix(PEM_HEADER)
                .removeSuffix(PEM_FOOTER)
                .filterNot { it.isWhitespace() }

            require(base64Payload.isNotBlank()) {
                "CSR PEM payload is empty."
            }

            return CertificateSigningRequestDer(
                bytes = ByteString(Base64.Pem.decode(base64Payload)),
            )
        }
    }
}

data class X509DistinguishedName(
    val commonName: String,
    val country: String? = null,
    val stateOrProvinceName: String? = null,
    val organizationName: String? = null,
    val localityName: String? = null,
    val organizationalUnitName: String? = null,
)

data class X509SubjectAlternativeNames(
    val dnsNames: List<String> = emptyList(),
    val uris: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val ipAddresses: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = dnsNames.isEmpty() && uris.isEmpty() && emails.isEmpty() && ipAddresses.isEmpty()
}

data class CertificateSigningRequestProfileData(
    val subjectName: X509DistinguishedName,
    val subjectAlternativeNames: X509SubjectAlternativeNames? = null,
)

data class CertificateSigningRequestBundle(
    val csrDer: CertificateSigningRequestDer,
    val decodedCsr: DecodedCertificateSigningRequest,
)

data class DecodedCertificateSigningRequest(
    val subjectName: X509DistinguishedName,
    val subjectAlternativeNames: X509SubjectAlternativeNames? = null,
    val publicKey: Key,
)

class CertificateSigningRequestBuilder {
    suspend fun build(
        profileData: CertificateSigningRequestProfileData,
        signingKey: Key,
    ): CertificateSigningRequestBundle {
        require(signingKey.hasPrivateKey) {
            "CSR signing key must contain a private key."
        }
        return platformBuildCertificateSigningRequest(
            profileData = profileData,
            signingKey = signingKey,
        )
    }
}

expect suspend fun platformBuildCertificateSigningRequest(
    profileData: CertificateSigningRequestProfileData,
    signingKey: Key,
): CertificateSigningRequestBundle

expect suspend fun parseCertificateSigningRequest(
    csrDer: CertificateSigningRequestDer,
): DecodedCertificateSigningRequest
