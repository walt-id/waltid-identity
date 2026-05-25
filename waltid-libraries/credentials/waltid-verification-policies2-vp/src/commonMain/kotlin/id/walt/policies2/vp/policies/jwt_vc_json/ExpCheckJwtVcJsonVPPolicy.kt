@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.W3CPresentationValidationError
import id.walt.credentials.presentations.PresentationValidationExceptionFunctions.presentationRequire
import id.walt.credentials.presentations.formats.JwtVcJsonPresentation
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant

private const val policyId = "jwt_vc_json/expiry-check"

/**
 * Checks `exp` and `nbf` claims on the jwt_vc_json VP JWT envelope.
 *
 * Per RFC 7519 §4.1.4-4.1.5:
 * - `exp`: the JWT MUST NOT be accepted after this time.
 * - `nbf`: the JWT MUST NOT be accepted before this time.
 *
 * Both claims are optional; if absent they are not checked.
 */
@Serializable
@SerialName(policyId)
class ExpCheckJwtVcJsonVPPolicy : JwtVcJsonVPPolicy() {

    override val id = policyId
    override val description = "Check exp and nbf claims on the VP JWT to reject expired or not-yet-valid presentations"

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifyJwtVcJsonPolicy(
        presentation: JwtVcJsonPresentation,
        verificationContext: VerificationSessionContext?
    ): Result<Unit> {
        val payload = presentation.payload
        val now = Clock.System.now()

        val expSeconds = payload["exp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        val nbfSeconds = payload["nbf"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

        addResult("now_epoch_seconds", now.epochSeconds.toString())

        if (expSeconds != null) {
            val exp = Instant.fromEpochSeconds(expSeconds)
            addResult("exp", expSeconds.toString())
            presentationRequire(now <= exp, W3CPresentationValidationError.PRESENTATION_EXPIRED) {
                "VP JWT is expired: exp=$expSeconds, now=${now.epochSeconds}"
            }
            log.trace { "VP JWT exp=$expSeconds is valid (now=${now.epochSeconds})" }
        }

        if (nbfSeconds != null) {
            val nbf = Instant.fromEpochSeconds(nbfSeconds)
            addResult("nbf", nbfSeconds.toString())
            presentationRequire(now >= nbf, W3CPresentationValidationError.PRESENTATION_NOT_YET_VALID) {
                "VP JWT is not yet valid: nbf=$nbfSeconds, now=${now.epochSeconds}"
            }
            log.trace { "VP JWT nbf=$nbfSeconds is valid (now=${now.epochSeconds})" }
        }

        return success()
    }
}
