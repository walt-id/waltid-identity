@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private const val policyId = "dc+sd-jwt/kb-jwt_iat-check"

/**
 * Checks that the Key Binding JWT `iat` claim is within an acceptable freshness window.
 *
 * The OID4VP spec requires `iat` to be present in the KB-JWT. Verifiers should reject
 * presentations where the `iat` is outside an acceptable window to prevent replay attacks.
 * This policy uses a ±[maxAgeMinutes] minute window (default: 5 minutes).
 */
@Serializable
@SerialName(policyId)
class KbJwtIatCheckSdJwtVPPolicy(
    val maxAgeMinutes: Long = 5L
) : DcSdJwtVPPolicy() {

    override val id = policyId
    override val description = "Check that the KB-JWT 'iat' claim is within the acceptable freshness window to prevent replay attacks"

    companion object {
        val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifySdJwtPolicy(
        presentation: DcSdJwtPresentation,
        verificationContext: VerificationSessionContext?
    ): Result<Unit> {
        val now = verificationContext?.verificationTime ?: Clock.System.now()
        val maxSkew = maxAgeMinutes.minutes
        val iatSeconds = requireFreshKbJwtIssuedAt(presentation.keyBindingJwt, now, maxSkew)

        addResult("kb_jwt_iat", iatSeconds.toString())
        addResult("now_epoch_seconds", now.epochSeconds.toString())
        addResult("max_age_minutes", maxAgeMinutes.toString())

        log.trace { "KB-JWT iat=$iatSeconds is within the ±${maxAgeMinutes}m freshness window" }
        return success()
    }
}
