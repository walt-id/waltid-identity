package id.walt.openid4vp.verifier.verification

import id.walt.dcql.models.DcqlQuery
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable

object DcqlFulfillmentChecker { // Encapsulating in an object

    private val log = KotlinLogging.logger("DcqlFulfillmentChecker")

    @Serializable
    class DcqlFulfillmentException(override val message: String) : IllegalArgumentException(message)

    /**
     * Checks if the set of successfully validated presentations (identified by their query IDs)
     * fulfills all the requirements of the original DCQL query.
     *
     * @param dcqlQuery The original DCQL query sent to the Wallet.
     * @param successfullyValidatedQueryIds A set of `id`s from `CredentialQuery` for which
     *                                      at least one valid presentation was received.
     * @return True if all DCQL requirements are met, false otherwise.
     */
    fun checkOverallDcqlFulfillment(
        dcqlQuery: DcqlQuery,
        successfullyValidatedQueryIds: Set<String>
    ): Result<Unit> {
        log.debug {
            "Checking overall DCQL fulfillment. Required query IDs from DCQL: ${dcqlQuery.credentials.map { it.id }}, " +
                    "Successfully validated query IDs: $successfullyValidatedQueryIds, " +
                    "Credential Sets: ${dcqlQuery.credentialSets?.size ?: 0}"
        }

        // Case 1: No credential_sets are defined in the DCQL query
        if (dcqlQuery.credentialSets.isNullOrEmpty()) {
            // All individual CredentialQuery entries are implicitly required.
            // We need to ensure that for every CredentialQuery in the original request,
            // we have a successfully validated presentation.
            val allOriginalQueryIds = dcqlQuery.credentials.map { it.id }.toSet()
            val missingRequiredQueries = allOriginalQueryIds - successfullyValidatedQueryIds

            if (missingRequiredQueries.isNotEmpty()) {
                log.warn {
                    "DCQL Fulfillment Failed (no credential_sets): Missing presentations for required query IDs: $missingRequiredQueries. " +
                            "All individual queries are considered required when no credential_sets are defined."
                }

                return Result.failure(
                    DcqlFulfillmentException(
                        "DCQL Fulfillment Failed (no credential_sets): Missing presentations for required query IDs: $missingRequiredQueries. " +
                                "All individual queries are considered required when no credential_sets are defined."
                    )
                )
            }
            log.debug { "DCQL Fulfillment Succeeded (no credential_sets): All individual queries were satisfied." }
            return Result.success(Unit)
        }

        // Case 2: credential_sets are defined
        // We need to check each CredentialSetQuery.
        // All sets where 'required' is true (or omitted) MUST be satisfied.
        for (credentialSetQuery in dcqlQuery.credentialSets) {
            if (credentialSetQuery.required) { // 'required' defaults to true if omitted
                // This required set must have at least one of its 'options' satisfied.
                // An option is a list of CredentialQuery IDs.
                // That option is satisfied if ALL CredentialQuery IDs within it are present
                // in 'successfullyValidatedQueryIds'.
                val isThisRequiredSetSatisfied = credentialSetQuery.options.any { optionList ->
                    // Check if all query IDs in this specific optionList have been successfully validated
                    val optionSatisfied = optionList.all { queryIdInOption ->
                        successfullyValidatedQueryIds.contains(queryIdInOption)
                    }
                    if (optionSatisfied) {
                        log.trace { "Required CredentialSet option satisfied: $optionList" }
                    }
                    optionSatisfied
                }

                if (!isThisRequiredSetSatisfied) {
                    // A required set was not satisfied
                    log.warn {
                        "DCQL Fulfillment Failed: A required CredentialSet was not satisfied. " +
                                "Set options: ${credentialSetQuery.options}, " +
                                "Successfully validated query IDs: $successfullyValidatedQueryIds"
                    }
                    return Result.failure(
                        DcqlFulfillmentException(
                            "DCQL Fulfillment Failed: A required CredentialSet was not satisfied. " +
                                    "Set options: ${credentialSetQuery.options}, " +
                                    "Successfully validated query IDs: $successfullyValidatedQueryIds"
                        )
                    )
                }
            } else {
                // For optional sets (required = false), we don't need to fail if they are not met.
                // We just log whether any option was met for tracing.
                val isThisOptionalSetSatisfied = credentialSetQuery.options.any { optionList ->
                    optionList.all { queryIdInOption ->
                        successfullyValidatedQueryIds.contains(queryIdInOption)
                    }
                }
                log.trace { "Optional CredentialSet satisfaction status: $isThisOptionalSetSatisfied. Options: ${credentialSetQuery.options}" }
            }
        }

        // If we've gone through all credential sets and all *required* ones were satisfied,
        // then the overall DCQL query is considered fulfilled in terms of credential presence.
        //
        // Additional check: Are there any credentials in the original dcqlQuery.credentials list
        // that were NOT part of any credential_set and are thus implicitly required?
        // The OpenID4VP spec (Section 6.4.2 Selecting Credentials) implies that if credential_sets
        // is provided, it dictates the selection. If a CredentialQuery is listed in dcqlQuery.credentials
        // but NOT referenced in any option of any credential_set, its requirement status is ambiguous
        // by that section alone.
        // However, a common interpretation is that if credential_sets are used, they fully define
        // the combination logic. If a credential is required individually AND outside of sets,
        // it should probably be its own required set with one option.
        // For now, we assume that if credential_sets are present, they are the definitive source
        // for what combinations are required.

        log.debug { "DCQL Fulfillment Succeeded: All required credential_sets were satisfied." }
        return Result.success(Unit)
    }
}
