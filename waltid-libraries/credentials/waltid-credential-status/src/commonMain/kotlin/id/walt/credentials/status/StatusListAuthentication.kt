package id.walt.credentials.status

import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizer
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.policies2.vc.policies.status.StatusListContent
import id.walt.policies2.vc.policies.status.model.IETFEntry
import id.walt.policies2.vc.policies.status.model.StatusEntry
import id.walt.policies2.vc.policies.status.model.StatusVerificationError
import id.walt.policies2.vc.policies.status.signature.IetfStatusListTokenClaims
import id.walt.policies2.vc.policies.status.signature.StatusListSignatureVerifier
import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizationRequest
import id.walt.policies2.vc.policies.status.signature.VerifiedStatusList
import id.walt.policies2.vc.policies.status.signature.authorizeMsoStatusListSigner
import id.walt.policies2.vc.policies.status.signature.authorizeStatusListSigner
import id.walt.policies2.vc.policies.status.signature.embeddedKeyReference
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import kotlinx.io.bytestring.ByteString
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// The published mdoc2 projection omits this optional DIS field. Read it from the authenticated
// MSO bytes, preserving that model's constructors/copy ABI and never trusting a caller projection.
@Serializable private data class MsoTrustProjection(val status: MsoStatusProjection? = null)
@Serializable private data class MsoStatusProjection(@SerialName("status_list") val list: MsoStatusListProjection? = null)
@Serializable private data class MsoStatusListProjection(val certificate: ByteArray? = null)
internal fun MdocsCredential.msoStatusTrustPoint(): ByteArray? =
    document.issuerSigned.issuerAuth.decodeIsoPayload<MsoTrustProjection>().status?.list?.certificate

/** Signed certificate bounds must also constrain a restored wallet observation. */
data class EvaluatedCredentialStatus(val value: UInt, val authorizationValidUntil: Instant? = null)

internal fun certificateDeadline(chain: List<String>, at: Instant): Instant? {
    return chain.map { encoded ->
        val body = encoded.lineSequence().map(String::trim).filter { !it.startsWith("-----") }.joinToString("")
        val certificate = X509CertificateUtil.parseCertificateDerEncoded(ByteString(body.decodeFromBase64()))
        val validity = certificate.data.validity
        if (at < validity.notBefore || at >= validity.notAfter) fail(CheckFailure.Reason.INVALID_VALIDITY)
        validity.notAfter
    }.minOrNull()
}

/** The policy adapter and wallet evaluator use this same authentication/authorization path. */
internal suspend fun authenticateStatusList(
    verifier: StatusListSignatureVerifier,
    content: StatusListContent,
    credential: DigitalCredential,
    entry: StatusEntry,
    authorizer: StatusListSignerAuthorizer?,
    nowEpochSeconds: Long,
    profile: StatusProtocol? = null,
): VerifiedStatusList<*> {
    val verified = when (content) {
        is StatusListContent.Text -> {
            if (!content.content.startsWith("ey") || content.content.count { it == '.' } != 2) {
                throw CredentialStatusException(CheckFailure.Reason.UNSUPPORTED_PROOF,
                    StatusVerificationError("Unsupported unsigned status-list text format"))
            }
            verifier.verifyJwtWithSigner(content.content, credential).getOrElse {
                throw CredentialStatusException(CheckFailure.Reason.INVALID_SIGNATURE,
                    StatusVerificationError("Status list JWT signature verification failed: ${it.message}"))
            }
        }
        is StatusListContent.Binary -> verifier.verifyCwtWithSigner(content.content, credential).getOrElse {
            throw CredentialStatusException(CheckFailure.Reason.INVALID_SIGNATURE,
                StatusVerificationError("Status list CWT signature verification failed: ${it.message}"))
        }
    }
    try {
        val authorization = StatusListSignerAuthorizationRequest(
            credential, entry.uri, verified.signer, verified.embeddedKeyReference,
        )
        if (profile == StatusProtocol.ISO_MSO_STATUS_LIST) authorizeMsoStatusListSigner(authorization, authorizer)
        else authorizeStatusListSigner(authorization, authorizer)
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Throwable) {
        throw CredentialStatusException(CheckFailure.Reason.UNAUTHORIZED_SIGNER, cause)
    }
    if (entry is IETFEntry) {
        try {
            when (content) {
                is StatusListContent.Text -> IetfStatusListTokenClaims.requireJwtType(content.content)
                is StatusListContent.Binary -> IetfStatusListTokenClaims.requireCwtType(content.content)
            }
            when (val payload = verified.payload) {
                is JsonObject -> IetfStatusListTokenClaims.validate(payload, entry.uri, nowEpochSeconds)
                is ByteArray -> IetfStatusListTokenClaims.validate(payload, entry.uri, nowEpochSeconds)
                else -> throw StatusVerificationError("Unsupported verified status-list payload")
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            throw CredentialStatusException(CheckFailure.Reason.INVALID_VALIDITY, cause)
        }
    }
    return verified
}
