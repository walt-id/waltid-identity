package id.walt.policies2.vc.policies

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.SubjectAlternativeNameExtension.Companion.extensionSan
import id.walt.certificate.x509.model.GeneralName
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.keyresolver.resolvers.X5cTrustAnchors
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Trusts the credential issuer when the certificate whose key signed the credential is one of
 * [trustRoots] or chains to one of them.
 *
 * The chain is the `x5c` the signing key was taken from: the JWT or COSE header, or the mdoc
 * `x5chain`. Certificates inside the credential are never used as anchors, and no system trust store
 * is consulted. Supports SD-JWT VC, W3C VCs secured as JWT or COSE, and mdoc. For ETSI trust lists,
 * use [ETSITrustListPolicy].
 *
 * The signing certificate must also name the credential's claimed issuer, see [bindIssuer].
 * It does not verify the credential signature; run it together with [CredentialSignaturePolicy].
 */
@Serializable
@SerialName("x509-trust")
data class X509TrustPolicy(
    /** Trusted root or pinned signing certificates, as PEM or base64-encoded DER. */
    val trustRoots: List<String>,
) : CredentialVerificationPolicy2() {
    override val id = ID

    init {
        require(trustRoots.isNotEmpty()) { "$ID: 'trustRoots' must not be empty" }
        trustRoots.forEachIndexed { index, root ->
            require(X5cTrustAnchors.isParsable(root)) {
                "$ID: 'trustRoots'[$index] is not an X.509 certificate in PEM or base64-encoded DER"
            }
        }
    }

    override suspend fun verify(
        credential: DigitalCredential,
        context: PolicyExecutionContext,
    ): Result<JsonElement> = try {
        val x5c = credential.resolveIssuerKey().x5c
        require(x5c.isNotEmpty()) { "The credential signing key was not taken from an x5c certificate chain" }
        X5cTrustAnchors.validate(x5c, trustRoots).getOrThrow()

        val signingCertificate = X509CertificateUtil.parseCertificateDerEncoded(ByteString(x5c.first().decodeFromBase64()))
        val issuer = credential.issuer
        val issuerBinding = bindIssuer(issuer, signingCertificate)
        Result.success(buildJsonObject {
            put("trusted", true)
            put("signing_certificate", signingCertificate.data.subjectDn)
            put("chain_length", x5c.size)
            issuer?.let { put("issuer", it) }
            put("issuer_binding", issuerBinding.name)
        })
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Throwable) {
        Result.failure(cause)
    }

    /**
     * Ties the claimed [issuer] to the signing certificate, so a certificate trusted for one party
     * cannot sign in the name of another. An `https` issuer must equal a URI SAN, or its host must
     * equal a DNS SAN (no wildcards). Without an issuer, the certificate subject is the issuer
     * (SD-JWT VC, ISO mdoc). Other issuer forms, DIDs included, cannot be bound.
     */
    private fun bindIssuer(issuer: String?, certificate: X509Certificate): IssuerBinding {
        if (issuer == null) return IssuerBinding.CERTIFICATE_SUBJECT
        require(issuer.startsWith("https://")) {
            "Issuer '$issuer' cannot be bound to the signing certificate: only https issuers are matched against its subject alternative names"
        }
        val names = certificate.data.extensionSan?.alternativeNames.orEmpty()
        if (names.any { it.type == GeneralName.NameType.uniformResourceIdentifier && it.value == issuer }) {
            return IssuerBinding.URI
        }
        val host = Url(issuer).host
        if (names.any { it.type == GeneralName.NameType.dNSName && it.value.equals(host, ignoreCase = true) }) {
            return IssuerBinding.DNS
        }
        throw IllegalArgumentException(
            "The signing certificate does not name issuer '$issuer': no URI SAN equals it and no DNS SAN equals '$host'"
        )
    }

    private enum class IssuerBinding { URI, DNS, CERTIFICATE_SUBJECT }

    companion object {
        const val ID = "x509-trust"
    }
}
