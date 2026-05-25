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

private const val policyId = "dc+sd-jwt/expiry-check"

/**
 * Checks `exp` and `nbf` claims on the SD-JWT core (the issuer-signed JWT).
 *
 * Per draft-ietf-oauth-sd-jwt-vc §2.2.2 and RFC 7519 §4.1.4-4.1.5:
 * - `exp`: the credential MUST NOT be accepted after this time.
 * - `nbf`: the credential MUST NOT be accepted before this time.
 *
 * Both claims are optional; if absent they are not checked.
 * Note: `iat` on the SD-JWT core is informational and not checked here for freshness
 * (the KB-JWT `iat` freshness is checked separately by [KbJwtIatCheckSdJwtVPPolicy]).
 */
@Serializable
@SerialName(policyId)
class ExpCheckSdJwtVPPolicy : DcSdJwtVPPolicy() {

    override val id = policyId
    override val description = "Check exp and nbf claims on the SD-JWT core to reject expired or not-yet-valid credentials"

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifySdJwtPolicy(
        presentation: DcSdJwtPresentation,
        verificationContext: VerificationSessionContext?
    ): Result<Unit> {
        val sdJwtPayload = presentation.sdJwt.decodeJws().payload
        val now = Clock.System.now()

        val expSeconds = sdJwtPayload["exp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        val nbfSeconds = sdJwtPayload["nbf"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

        addResult("now_epoch_seconds", now.epochSeconds.toString())

        if (expSeconds != null) {
            val exp = Instant.fromEpochSeconds(expSeconds)
            addResult("exp", expSeconds.toString())
            presentationRequire(now <= exp, DcSdJwtPresentationValidationError.SD_JWT_EXPIRED) {
                "SD-JWT credential is expired: exp=$expSeconds, now=${now.epochSeconds}"
            }
            log.trace { "SD-JWT exp=$expSeconds is valid (now=${now.epochSeconds})" }
        }

        if (nbfSeconds != null) {
            val nbf = Instant.fromEpochSeconds(nbfSeconds)
            addResult("nbf", nbfSeconds.toString())
            presentationRequire(now >= nbf, DcSdJwtPresentationValidationError.SD_JWT_NOT_YET_VALID) {
                "SD-JWT credential is not yet valid: nbf=$nbfSeconds, now=${now.epochSeconds}"
            }
            log.trace { "SD-JWT nbf=$nbfSeconds is valid (now=${now.epochSeconds})" }
        }

        return success()
    }
}
