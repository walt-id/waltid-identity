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

private const val policyId = "jwt_vc_json/nbf-check"

/**
 * Checks the `nbf` claim on the jwt_vc_json VP JWT envelope.
 *
 * Per RFC 7519 §4.1.5: the JWT MUST NOT be accepted before the not-before time.
 * If the `nbf` claim is absent this policy passes without error.
 *
 * [clockSkewSeconds] allows a small tolerance for clock differences between issuer and verifier
 * machines (default: 2 seconds).
 */
@Serializable
@SerialName(policyId)
class NbfCheckJwtVcJsonVPPolicy(
    val clockSkewSeconds: Long = 2L
) : JwtVcJsonVPPolicy() {

    override val id = policyId
    override val description = "Check the VP JWT 'nbf' claim — reject presentations that are not yet valid"

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifyJwtVcJsonPolicy(
        presentation: JwtVcJsonPresentation,
        verificationContext: VerificationSessionContext?
    ): Result<Unit> {
        val nbfSeconds = presentation.payload["nbf"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: return success() // absent nbf claim is allowed

        val now = Clock.System.now()
        val nbf = Instant.fromEpochSeconds(nbfSeconds)

        addResult("nbf", nbfSeconds.toString())
        addResult("now_epoch_seconds", now.epochSeconds.toString())
        addResult("clock_skew_seconds", clockSkewSeconds.toString())

        // Allow clock_skew_seconds tolerance: presentation is valid if now + skew >= nbf
        presentationRequire(now + clockSkewSeconds.seconds >= nbf, W3CPresentationValidationError.PRESENTATION_NOT_YET_VALID) {
            "VP JWT is not yet valid: nbf=$nbfSeconds, now=${now.epochSeconds}, skew=${clockSkewSeconds}s"
        }
        log.trace { "VP JWT nbf=$nbfSeconds is valid (now=${now.epochSeconds}, skew=${clockSkewSeconds}s)" }
        return success()
    }
}
