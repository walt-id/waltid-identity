package id.walt.policies2.vc

import id.walt.credentials.formats.DigitalCredential
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.models.ClaimsQuery

object DcqlClaimValidator {

    /**
     * TODO: use or remove
     * A helper to check if the claims in a validated credential match the original DCQL claims query.
     */
    fun validateClaimsAgainstCredential(
        credential: DigitalCredential,
        claimsQuery: List<ClaimsQuery>
    ): Result<Unit> {
        for (claimQuery in claimsQuery) {
            // Use a helper to resolve the path in the credential's data
            val resolvedValue = DcqlMatcher.resolveClaimPath(credential.credentialData, claimQuery.path)
            if (resolvedValue == null) {
                return Result.failure(
                    DcqlClaimValidationFailureException(
                        "Claim validation failed: Requested claim path ${claimQuery.path} not found in the presented credential.",
                        claimQuery.path,
                        null
                    )
                )
            }
            // Optionally, re-check value constraints if they were present in the query
            if (!claimQuery.values.isNullOrEmpty()) {
                if (claimQuery.values!!.none { it == resolvedValue }) {
                    return Result.failure(
                        DcqlClaimValidationFailureException(
                            "Claim validation failed: Value for claim ${claimQuery.path} does not match any of the required values.",
                            claimQuery.path,
                            resolvedValue
                        )
                    )
                }
            }
        }
        return Result.success(Unit)
    }

}
