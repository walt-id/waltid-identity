package id.walt.verifier2.verification

import id.walt.dcql.models.DcqlQuery
import id.walt.verifier2.data.DcqlFulfillmentFailure
import id.walt.verifier2.data.UnsatisfiedSet
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable

object DcqlFulfillmentChecker { // Encapsulating in an object

    private val log = KotlinLogging.logger("DcqlFulfillmentChecker")

    @Serializable
    class DcqlFulfillmentException(override val message: String) : IllegalArgumentException(message)

    sealed class DcqlFulfillmentCheckResult {
        data object Success : DcqlFulfillmentCheckResult()

        data class Failure(
            val reason: String,
            val details: DcqlFulfillmentFailure,
        ) : DcqlFulfillmentCheckResult()
    }

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
        return when (val detailed = checkOverallDcqlFulfillmentDetailed(dcqlQuery, successfullyValidatedQueryIds)) {
            is DcqlFulfillmentCheckResult.Success -> Result.success(Unit)
            is DcqlFulfillmentCheckResult.Failure -> Result.failure(DcqlFulfillmentException(detailed.reason))
        }
    }

    /**
     * Detailed variant of [checkOverallDcqlFulfillment] (WAL-977). On failure, returns structured
     * details that the verifier attaches to the session so webhook / /info consumers can see which
     * required queries or credential sets were missed.
     */
    fun checkOverallDcqlFulfillmentDetailed(
        dcqlQuery: DcqlQuery,
        successfullyValidatedQueryIds: Set<String>,
    ): DcqlFulfillmentCheckResult {
        log.debug {
            "Checking overall DCQL fulfillment. Required query IDs from DCQL: ${dcqlQuery.credentials.map { it.id }}, " +
                    "Successfully validated query IDs: $successfullyValidatedQueryIds, " +
                    "Credential Sets: ${dcqlQuery.credentialSets?.size ?: 0}"
        }

        val validatedList = successfullyValidatedQueryIds.toList()

        // Case 1: No credential_sets are defined in the DCQL query
        if (dcqlQuery.credentialSets.isNullOrEmpty()) {
            val allOriginalQueryIds = dcqlQuery.credentials.map { it.id }.toSet()
            val missingRequiredQueries = (allOriginalQueryIds - successfullyValidatedQueryIds).toList()

            if (missingRequiredQueries.isNotEmpty()) {
                val message = "DCQL Fulfillment Failed (no credential_sets): Missing presentations for required query IDs: $missingRequiredQueries. " +
                        "All individual queries are considered required when no credential_sets are defined."
                log.warn { message }
                return DcqlFulfillmentCheckResult.Failure(
                    reason = message,
                    details = DcqlFulfillmentFailure(
                        missingQueryIds = missingRequiredQueries,
                        unsatisfiedSets = emptyList(),
                        successfullyValidatedQueryIds = validatedList,
                    ),
                )
            }
            log.debug { "DCQL Fulfillment Succeeded (no credential_sets): All individual queries were satisfied." }
            return DcqlFulfillmentCheckResult.Success
        }

        // Case 2: credential_sets are defined
        val unsatisfiedRequired = ArrayList<UnsatisfiedSet>()
        for (credentialSetQuery in dcqlQuery.credentialSets) {
            if (credentialSetQuery.required) { // 'required' defaults to true if omitted
                val isThisRequiredSetSatisfied = credentialSetQuery.options.any { optionList ->
                    optionList.all { queryIdInOption -> successfullyValidatedQueryIds.contains(queryIdInOption) }
                }

                if (!isThisRequiredSetSatisfied) {
                    unsatisfiedRequired += UnsatisfiedSet(
                        options = credentialSetQuery.options,
                        required = true,
                    )
                }
            } else {
                val isThisOptionalSetSatisfied = credentialSetQuery.options.any { optionList ->
                    optionList.all { queryIdInOption -> successfullyValidatedQueryIds.contains(queryIdInOption) }
                }
                log.trace { "Optional CredentialSet satisfaction status: $isThisOptionalSetSatisfied. Options: ${credentialSetQuery.options}" }
            }
        }

        if (unsatisfiedRequired.isNotEmpty()) {
            val message = "DCQL Fulfillment Failed: ${unsatisfiedRequired.size} required CredentialSet(s) not satisfied. " +
                    "Unsatisfied sets: ${unsatisfiedRequired.map { it.options }}, " +
                    "Successfully validated query IDs: $successfullyValidatedQueryIds"
            log.warn { message }
            return DcqlFulfillmentCheckResult.Failure(
                reason = message,
                details = DcqlFulfillmentFailure(
                    missingQueryIds = emptyList(),
                    unsatisfiedSets = unsatisfiedRequired,
                    successfullyValidatedQueryIds = validatedList,
                ),
            )
        }

        log.debug { "DCQL Fulfillment Succeeded: All required credential_sets were satisfied." }
        return DcqlFulfillmentCheckResult.Success
    }
}
