package id.walt.issuer2.domain

import kotlinx.serialization.Serializable

/**
 * Why a stage rejected the request. Published with failure events only; the terminal credential
 * failure already records its reason in `statusReason`, so this is not stored on the session.
 *
 * Unlike the verifier's `SessionFailure` this is not a sealed hierarchy, because every issuance stage
 * reports the same shape and there is no per-stage payload to model.
 */
@Serializable
data class IssuanceSessionFailure(
    val errorCode: String,
    val reason: String,
)
