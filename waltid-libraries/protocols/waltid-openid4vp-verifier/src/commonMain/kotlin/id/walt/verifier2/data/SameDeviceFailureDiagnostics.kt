package id.walt.verifier2.data

import id.walt.policies2.vc.CredentialPolicyResult
import id.walt.policies2.vp.policies.VPPolicy2
import id.walt.verifier2.verification2.Verifier2PolicyResults

/** Diagnostic outcomes only: policy payloads and exception messages may contain presented claims. */
internal fun CredentialPolicyResult.diagnosticView() = copy(
    policy = policy.publicView(),
    result = null,
    error = if (success) null else "Policy '${policy.id}' failed.",
)

internal fun VPPolicy2.PolicyRunResult.diagnosticView() = copy(
    results = emptyMap(),
    errors = errors.map { it.copy(message = "Policy '${policyExecuted.id}' failed.", cause = null) },
)

internal fun Verifier2PolicyResults.diagnosticView() = copy(
    vpPolicies = vpPolicies.mapValues { (_, results) -> results.mapValues { (_, result) -> result.diagnosticView() } },
    vcPolicies = vcPolicies.map { it.diagnosticView() },
    specificVcPolicies = specificVcPolicies.mapValues { (_, results) -> results.map { it.diagnosticView() } },
)

internal fun SessionFailure.diagnosticView(): SessionFailure = when (this) {
    is SessionFailure.VcPolicyViolations -> copy(
        reason = "Credential policy verification failed. Failed VC policies: " + violations.map { it.policy.id }.distinct().joinToString(", "),
        violations = violations.map { it.diagnosticView() },
    )
    is SessionFailure.PresentationValidation -> copy(
        reason = "Presentation validation failed." + failedPolicies.values.flatMap { it.keys }.distinct()
            .takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = " Failed VP policies: ").orEmpty(),
        failedPolicies = failedPolicies.mapValues { (_, results) -> results.mapValues { (_, result) -> result.diagnosticView() } },
    )
    is SessionFailure.DcqlFulfillment -> copy(reason = "Presentation does not satisfy the DCQL query.")
    is SessionFailure.WalletErrorResponse -> copy(
        reason = "Wallet returned an error response.",
        error = "wallet_error",
        errorDescription = null,
        state = null,
    )
}
