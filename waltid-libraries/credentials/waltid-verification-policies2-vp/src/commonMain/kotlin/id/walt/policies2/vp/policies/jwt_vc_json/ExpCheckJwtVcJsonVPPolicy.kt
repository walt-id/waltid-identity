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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val policyId = "jwt_vc_json/exp-check"

/**
 * Checks the `exp` claim on the jwt_vc_json VP JWT envelope.
 *
 * Per RFC 7519 §4.1.4: the JWT MUST NOT be accepted on or after the expiration time.
 * If the `exp` claim is absent this policy passes without error.
 *
 * [clockSkewSeconds] allows a small tolerance for clock differences between issuer and verifier
 * machines (default: 2 seconds).
 */
@Serializable
@SerialName(policyId)
class ExpCheckJwtVcJsonVPPolicy(
    val clockSkewSeconds: Long = 2L
) : JwtVcJsonVPPolicy() {

    override val id = policyId
    override val description = "Check the VP JWT 'exp' claim — reject presentations that have expired"

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifyJwtVcJsonPolicy(
        presentation: JwtVcJsonPresentation,
        verificationContext: VerificationSessionContext?
    ): Result<Unit> {
        val expSeconds = presentation.payload["exp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: return success() // absent exp claim is allowed

        val now = Clock.System.now()
        val exp = Instant.fromEpochSeconds(expSeconds)

        addResult("exp", expSeconds.toString())
        addResult("now_epoch_seconds", now.epochSeconds.toString())
        addResult("clock_skew_seconds", clockSkewSeconds.toString())

        // Allow clock_skew_seconds tolerance: presentation is valid if now <= exp + skew
        presentationRequire(now <= exp + clockSkewSeconds.seconds, W3CPresentationValidationError.PRESENTATION_EXPIRED) {
            "VP JWT is expired: exp=$expSeconds, now=${now.epochSeconds}, skew=${clockSkewSeconds}s"
        }
        log.trace { "VP JWT exp=$expSeconds is valid (now=${now.epochSeconds}, skew=${clockSkewSeconds}s)" }
        return success()
    }
}
