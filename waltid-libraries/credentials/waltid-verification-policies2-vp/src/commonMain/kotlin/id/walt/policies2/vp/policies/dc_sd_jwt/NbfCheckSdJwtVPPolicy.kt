@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.DcSdJwtPresentationValidationError
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequire
import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import id.walt.crypto.utils.JwsUtils.decodeJws
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant

private const val policyId = "dc+sd-jwt/nbf-check"

/**
 * Checks the `nbf` claim on the SD-JWT core (the issuer-signed JWT).
 *
 * Per draft-ietf-oauth-sd-jwt-vc §2.2.2 and RFC 7519 §4.1.5:
 * the credential MUST NOT be accepted before the not-before time.
 * If the `nbf` claim is absent this policy passes without error.
 */
@Serializable
@SerialName(policyId)
class NbfCheckSdJwtVPPolicy : DcSdJwtVPPolicy() {

    override val id = policyId
    override val description = "Check the SD-JWT core 'nbf' claim — reject not-yet-valid credentials"

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifySdJwtPolicy(
        presentation: DcSdJwtPresentation,
        verificationContext: VerificationSessionContext?
    ): Result<Unit> {
        val nbfSeconds = presentation.sdJwt.decodeJws().payload["nbf"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: return success() // absent nbf claim is allowed

        val now = Clock.System.now()
        val nbf = Instant.fromEpochSeconds(nbfSeconds)

        addResult("nbf", nbfSeconds.toString())
        addResult("now_epoch_seconds", now.epochSeconds.toString())

        presentationRequire(now >= nbf, DcSdJwtPresentationValidationError.SD_JWT_NOT_YET_VALID) {
            "SD-JWT credential is not yet valid: nbf=$nbfSeconds, now=${now.epochSeconds}"
        }
        log.trace { "SD-JWT nbf=$nbfSeconds is valid (now=${now.epochSeconds})" }
        return success()
    }
}
