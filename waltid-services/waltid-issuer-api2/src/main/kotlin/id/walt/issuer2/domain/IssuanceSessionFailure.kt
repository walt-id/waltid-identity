package id.walt.issuer2.domain

import kotlinx.serialization.Serializable

/**
 * Why a stage rejected the request. Published with failure events; for terminal credential failures
 * it is also persisted on the session (alongside `statusReason`) and returned from session GET.
 *
 * Unlike the verifier's `SessionFailure` this is not a sealed hierarchy, because every issuance stage
 * reports the same shape and there is no per-stage payload to model.
 */
@Serializable
data class IssuanceSessionFailure(
    val errorCode: String,
    val reason: String,
)
