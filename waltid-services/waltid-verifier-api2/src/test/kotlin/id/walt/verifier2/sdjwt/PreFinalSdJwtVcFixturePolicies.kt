package id.walt.verifier2.sdjwt

import id.walt.policies2.vp.policies.ExpCheckSdJwtVPPolicy
import id.walt.policies2.vp.policies.VPPolicyList
import id.walt.policies2.vp.policies.VPVerificationPolicyManager

/**
 * Default VP policies without the SD-JWT `exp-check`, for the pre-final community SD-JWT VC fixture.
 *
 * The fixture's `exp` (1791514870) passed on 2026-10-09, and it cannot be re-signed. These tests assert
 * that the fixture is rejected for its stale `sd_hash` / x5c chain, so the presentation-level expiry
 * check must not reject it first and turn them into date-dependent tests.
 */
internal val preFinalFixtureVpPolicies = VPPolicyList(
    jwtVcJson = VPVerificationPolicyManager.defaultJwtVcJsonPolicies,
    dcSdJwt = VPVerificationPolicyManager.defaultDcSdJwtPolicies.filterNot { it is ExpCheckSdJwtVPPolicy },
    msoMdoc = VPVerificationPolicyManager.defaultMsoMdocPolicies,
)
