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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val policyId = "dc+sd-jwt/exp-check"

/**
 * Checks the `exp` claim on the SD-JWT core (the issuer-signed JWT).
 *
 * Per draft-ietf-oauth-sd-jwt-vc §2.2.2 and RFC 7519 §4.1.4:
 * the credential MUST NOT be accepted on or after the expiration time.
 * If the `exp` claim is absent this policy passes without error.
 *
 * [clockSkewSeconds] allows a small tolerance for clock differences between issuer and verifier
 * machines (default: 2 seconds).
 */
@Serializable
@SerialName(policyId)
class ExpCheckSdJwtVPPolicy(
    val clockSkewSeconds: Long = 2L
) : DcSdJwtVPPolicy() {

    override val id = policyId
    override val description = "Check the SD-JWT core 'exp' claim — reject expired credentials"

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifySdJwtPolicy(
        presentation: DcSdJwtPresentation,
        verificationContext: VerificationSessionContext?
    ): Result<Unit> {
        val expSeconds = presentation.sdJwt.decodeJws().payload["exp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?: return success() // absent exp claim is allowed

        val now = Clock.System.now()
        val exp = Instant.fromEpochSeconds(expSeconds)

        addResult("exp", expSeconds.toString())
        addResult("now_epoch_seconds", now.epochSeconds.toString())
        addResult("clock_skew_seconds", clockSkewSeconds.toString())

        // Allow clock_skew_seconds tolerance: credential is valid if now <= exp + skew
        presentationRequire(now <= exp + clockSkewSeconds.seconds, DcSdJwtPresentationValidationError.SD_JWT_EXPIRED) {
            "SD-JWT credential is expired: exp=$expSeconds, now=${now.epochSeconds}, skew=${clockSkewSeconds}s"
        }
        log.trace { "SD-JWT exp=$expSeconds is valid (now=${now.epochSeconds}, skew=${clockSkewSeconds}s)" }
        return success()
    }
}
