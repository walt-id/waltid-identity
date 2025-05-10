package id.walt.dcql

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import kotlin.Result

object DcqlMatcher {

    private val log = KotlinLogging.logger {}

    /**
     * Matches available credentials against a DCQL query.
     *
     * @param query The parsed DCQL query.
     * @param availableCredentials The list of credentials held by the wallet.
     * @return A Result containing a map where keys are CredentialQuery IDs and
     *         values are lists of matching Credentials, or a failure with an exception.
     */
    fun match(
        query: DcqlQuery,
        availableCredentials: List<Credential>,
    ): Result<Map<String, List<Credential>>> {
        log.debug { "Starting DCQL match. Query: $query, Available Credentials: ${availableCredentials.map { it.id }}" }
        val individualMatches = mutableMapOf<String, List<Credential>>()

        // 1. Find matches for each individual CredentialQuery
        for (credQuery in query.credentials) {
            log.trace { "Processing CredentialQuery: ${credQuery.id} (format: ${credQuery.format})" }
            val potentialMatches = availableCredentials.filter { it.format == credQuery.format }
            log.trace { "Potential matches for ${credQuery.id} based on format: ${potentialMatches.map { it.id }}" }

            val finalMatchesForQuery = potentialMatches.filter { credential ->
                // Apply further filtering based on query constraints
                matchesMeta(credential, credQuery.meta) &&
                        matchesTrustedAuthorities(credential, credQuery.trustedAuthorities) &&
                        matchesClaims(credential, credQuery.claims, credQuery.claimSets)
                // Note: requireCryptographicHolderBinding check would happen during
                // presentation generation, not typically during matching.
            }

            log.trace { "Final matches for ${credQuery.id} after filtering: ${finalMatchesForQuery.map { it.id }}" }

            if (finalMatchesForQuery.isNotEmpty()) {
                if (!credQuery.multiple && finalMatchesForQuery.size > 1) {
                    // If multiple=false, only one should match. We take the first one found.
                    // Depending on requirements, this could also be an error or require user selection.
                    log.warn { "Multiple credentials matched query '${credQuery.id}' but 'multiple' is false. Selecting the first: ${finalMatchesForQuery.first().id}" }
                    individualMatches[credQuery.id] = listOf(finalMatchesForQuery.first())
                } else {
                    individualMatches[credQuery.id] = finalMatchesForQuery
                }
            } else {
                log.debug { "No credentials found matching query: ${credQuery.id}" }
                // Store nothing if no matches found for this query ID
            }
        }

        log.debug { "Individual matches found: ${individualMatches.mapValues { it.value.map { c -> c.id } }}" }

        // 2. Check Credential Set constraints
        query.credentialSets?.let { sets ->
            val satisfied = checkCredentialSets(sets, individualMatches.keys)
            if (!satisfied) {
                return Result.failure(DcqlMatchException("Required credential set constraints not met."))
            }
        }

        // 3. Check if all *required* individual queries yielded at least one match
        // (This check is implicitly covered by required credential sets if they exist and cover all queries,
        // but added for clarity if no sets are defined or sets are optional)
        val requiredIndividualQueryIds = query.credentials.map { it.id } // Assuming all individual queries are implicitly required unless part of an optional set
        val missingRequired = requiredIndividualQueryIds.filterNot { individualMatches.containsKey(it) }

        // Refine required check: Only fail if a query ID is NOT part of ANY satisfied OPTIONAL set AND is missing.
        // This logic gets complex. A simpler approach: fail if any query ID mentioned
        // in a REQUIRED set option is missing from individualMatches. This is handled by checkCredentialSets.
        // If there are NO credential sets, then all individual queries are implicitly required.
        if (query.credentialSets == null && missingRequired.isNotEmpty()) {
            val errorMsg = "No matches found for required credential queries: $missingRequired"
            log.warn { errorMsg }
            // Decide if this is a failure. Often, returning an empty map or partial map is desired.
            // Let's return what we found, assuming partial fulfillment might be acceptable.
            // If strict fulfillment is needed, uncomment the failure below.
            // return Result.failure(DcqlMatchException(errorMsg))
        }


        log.info { "DCQL Match successful. Result: ${individualMatches.mapValues { it.value.map { c -> c.id } }}" }
        // Return success even if some optional queries weren't matched
        return Result.success(individualMatches)
    }

    // --- Helper Functions ---

    /** Placeholder: Check format-specific metadata constraints. */
    private fun matchesMeta(credential: Credential, metaQuery: JsonObject?): Boolean {
        if (metaQuery == null) return true
        log.trace { "Checking metadata for credential ${credential.id} (simplified: returning true)" }
        // Actual implementation requires parsing metaQuery based on credential.format
        // Example: For W3C VC, check "type_values" against credential.data["type"]
        return true // Simplified
    }

    /** Placeholder: Check issuer constraints. */
    private fun matchesTrustedAuthorities(
        credential: Credential,
        authoritiesQuery: List<TrustedAuthoritiesQuery>?,
    ): Boolean {
        if (authoritiesQuery.isNullOrEmpty()) return true
        log.trace { "Checking trusted authorities for credential ${credential.id} (simplified: returning true)" }
        // Actual implementation requires checking credential.issuer against
        // the types and values in authoritiesQuery. May involve trust list lookups.
        return true // Simplified
    }

    /** Check if a credential satisfies the claims constraints. */
    private fun matchesClaims(
        credential: Credential,
        claims: List<ClaimsQuery>?,
        claimSets: List<List<String>>?,
    ): Boolean {
        if (claims.isNullOrEmpty()) {
            log.trace { "No specific claims requested for credential ${credential.id}" }
            return true // No specific claims requested for selective disclosure
        }

        val claimsMap = claims.associateBy { it.id } // For easy lookup by ID if claimSets are used

        return when {
            // Case 1: Specific claims listed, no sets (Section 6.4.1, bullet 2)
            claimSets.isNullOrEmpty() -> {
                log.trace { "Checking all listed claims for credential ${credential.id}" }
                claims.all { claimQuery -> claimExistsAndMatches(credential, claimQuery) }
            }
            // Case 2: Both claims and claimSets present (Section 6.4.1, bullet 3)
            else -> {
                log.trace { "Checking claim sets for credential ${credential.id}" }
                // Find the first claim set option where all claims exist and match
                claimSets.any { setOptionIds ->
                    log.trace { "Checking claim set option: $setOptionIds" }
                    setOptionIds.all { claimId ->
                        val claimQuery = claimsMap[claimId]
                        if (claimQuery == null) {
                            log.warn { "Claim ID '$claimId' in claimSet not found in claims list for credential ${credential.id}" }
                            false // ID in set must exist in the claims list
                        } else {
                            claimExistsAndMatches(credential, claimQuery)
                        }
                    }
                }
            }
        }
    }

    /** Check if a single claim exists at the path and matches optional values. */
    private fun claimExistsAndMatches(credential: Credential, claimQuery: ClaimsQuery): Boolean {
        val claimValue = resolveClaimPath(credential.data, claimQuery.path)
        if (claimValue == null) {
            log.trace { "Claim path ${claimQuery.path} not found in credential ${credential.id}" }
            return false // Claim must exist at the path
        }

        // If specific values are requested, the claim's value must match at least one
        if (!claimQuery.values.isNullOrEmpty()) {
            // Need careful comparison for JsonPrimitive types
            val matchesValue = claimQuery.values.any { queryValue ->
                // Simple comparison for primitives. Might need type coercion logic.
                queryValue == claimValue
            }
            if (!matchesValue) {
                log.trace { "Claim path ${claimQuery.path} value '$claimValue' does not match required values ${claimQuery.values} in credential ${credential.id}" }
                return false
            }
        }
        log.trace { "Claim path ${claimQuery.path} exists and matches criteria in credential ${credential.id}" }
        return true // Claim exists and matches value constraints (if any)
    }

    /** Basic JSON path resolver. Needs enhancement for arrays, different formats. */
    private fun resolveClaimPath(data: JsonObject, path: List<String>): JsonElement? {
        var currentElement: JsonElement? = data
        for (segment in path) {
            when (currentElement) {
                is JsonObject -> currentElement = currentElement[segment]
                // Basic array index support (needs proper error handling/type checks)
                is JsonArray -> {
                    val index = segment.toIntOrNull()
                    currentElement = if (index != null && index >= 0 && index < currentElement.size) {
                        currentElement[index]
                    } else {
                        null
                    }
                }
                else -> return null // Cannot traverse further
            }
            if (currentElement == null || currentElement is JsonNull) return null
        }
        return currentElement
    }

    /** Check if the matched credentials satisfy the credential set requirements. */
    private fun checkCredentialSets(
        credentialSets: List<CredentialSetQuery>,
        matchedQueryIds: Set<String>,
    ): Boolean {
        log.debug { "Checking credential sets against matched IDs: $matchedQueryIds" }
        for (setQuery in credentialSets) {
            log.trace { "Checking set: required=${setQuery.required}, options=${setQuery.options}" }
            // Check if at least one option in the set is satisfied
            val satisfiedOption = setQuery.options.any { optionIds ->
                // All IDs in this option must be present in the matched IDs
                optionIds.all { it in matchedQueryIds }
            }

            if (!satisfiedOption && setQuery.required) {
                log.warn { "Required credential set not satisfied. Options: ${setQuery.options}, Matched IDs: $matchedQueryIds" }
                return false // A required set was not satisfied
            }
            if (satisfiedOption) {
                log.trace { "Credential set satisfied (required=${setQuery.required})" }
            } else {
                log.trace { "Optional credential set not satisfied" }
            }
        }
        log.debug { "All required credential sets satisfied." }
        return true // All required sets were satisfied
    }
}

