package id.walt.dcql

import id.walt.dcql.models.*
import id.walt.dcql.models.meta.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*

object DcqlMatcher {

    private val log = KotlinLogging.logger {}

    data class DcqlMatchResult(
        val credential: DcqlCredential,
        /**
         * Map of selected claims.
         * Key: Stringified path from ClaimsQuery.
         * Value: SdJwtSelectiveDisclosure if it was an SD claim, or JsonElement for a directly resolved claim.
         *
         * - null: If the credential format doesn't support selective disclosure in a way relevant to the query.
         * - emptyMap(): If the format supports SD, but no queried claims were selected.
         * - non-emptyMap(): If claims were selected.
         */
        val selectedDisclosures: Map<String, Any>?, // JsonElement or DcqlDisclosure
        val originalQuery: CredentialQuery
    )

    /**
     * Matches available credentials against a DCQL query.
     *
     * @param query The parsed DCQL query.
     * @param availableCredentials The list of credentials held by the wallet.
     * @return A Result containing a map where keys are CredentialQuery IDs and
     *         values are lists of matching Credentials, or a failure with an exception.
     */
    fun match(
        // Or not suspend if availableCredentials is a List
        query: DcqlQuery,
        availableCredentials: List<DcqlCredential>,
    ): Result<Map<String, List<DcqlMatchResult>>> {
        log.debug { "Starting DCQL match. Query: $query, Available Credentials Count: ${availableCredentials.size}" }

        val individualMatches = mutableMapOf<String, MutableList<DcqlMatchResult>>()

        // Find matches for each individual CredentialQuery
        for (credentialQuery in query.credentials) {
            log.trace { "Processing CredentialQuery: ${credentialQuery.id} (format: ${credentialQuery.format})" }
            val potentialMatchesByFormat = availableCredentials.filter { it.format in credentialQuery.format.id }
            log.trace { "Potential matches for ${credentialQuery.id} based on format: ${potentialMatchesByFormat.map { it.id + "(${it.format})" }}" }

            val successfullyMatchedCredentialsForThisQuery = mutableListOf<DcqlMatchResult>()

            for (credential in potentialMatchesByFormat) {
                val metaCheck = matchesMeta(credential, credentialQuery.meta ?: NoMeta, credentialQuery.format)
                if (!metaCheck) {
                    log.trace { "Credential ${credential.id} failed meta check for query ${credentialQuery.id}" }
                    continue
                }

                val authoritiesCheck = matchesTrustedAuthorities(credential, credentialQuery.trustedAuthorities)
                if (!authoritiesCheck) {
                    log.trace { "Credential ${credential.id} failed trusted authorities check for query ${credentialQuery.id}" }
                    continue
                }

                val claimsMatchResult = matchesClaimsAndGetSelected(credential, credentialQuery.claims, credentialQuery.claimSets)

                if (claimsMatchResult.isSuccess) {
                    // claimsMatchResult.getOrThrow() is Map<String, Any>?
                    // This map itself can be null if the credential is not selectively disclosable
                    // or empty if no claims were selected from a disclosable one.
                    successfullyMatchedCredentialsForThisQuery.add(
                        DcqlMatchResult(credential, claimsMatchResult.getOrThrow(), originalQuery = credentialQuery)
                    )
                    log.trace { "Credential ${credential.id} processed for claims for query ${credentialQuery.id}. Selected: ${claimsMatchResult.getOrThrow()}" }
                } else {
                    log.trace { "Credential ${credential.id} failed claims processing for query ${credentialQuery.id}: ${claimsMatchResult.exceptionOrNull()?.message}" }
                }
            }

            if (successfullyMatchedCredentialsForThisQuery.isNotEmpty()) {
                if (!credentialQuery.multiple && successfullyMatchedCredentialsForThisQuery.size > 1) {
                    log.warn { "Multiple credentials matched query '${credentialQuery.id}' but 'multiple' is false. Selecting the first: ${successfullyMatchedCredentialsForThisQuery.first().credential.id}" }
                    individualMatches.getOrPut(credentialQuery.id) { mutableListOf() }
                        .add(successfullyMatchedCredentialsForThisQuery.first())
                } else {
                    individualMatches.getOrPut(credentialQuery.id) { mutableListOf() }
                        .addAll(successfullyMatchedCredentialsForThisQuery)
                }
            } else {
                log.debug { "No credentials found matching query: ${credentialQuery.id}" }
            }
        }
        val finalIndividualMatches: Map<String, List<DcqlMatchResult>> =
            individualMatches.mapValues { it.value.toList() }

        log.debug { "Individual matches found: ${finalIndividualMatches.mapValues { entry -> entry.value.map { it.credential.id } }}" }

        query.credentialSets?.let { sets ->
            val satisfied = checkCredentialSets(sets, finalIndividualMatches.keys)
            if (!satisfied) {
                val errorMsg = "Required credential set constraints not met."
                log.warn { errorMsg }
                return Result.failure(DcqlMatchException(errorMsg))
            }
        }

        log.info { "DCQL Match successful. Result: ${finalIndividualMatches.mapValues { entry -> entry.value.map { it.credential.id } }}" }
        return Result.success(finalIndividualMatches)
    }

    /**
     * Matches available credentials against a DCQL query - will not handle SD claims.
     *
     * @param query The parsed DCQL query.
     * @param availableCredentials The list of credentials held by the wallet.
     * @return A Result containing a map where keys are CredentialQuery IDs and
     *         values are lists of matching Credentials, or a failure with an exception.
     */
    fun matchWithoutClaims(
        query: DcqlQuery,
        availableCredentials: List<DcqlCredential>, // TODO: Have this be a flow
    ): Result<Map<String, List<DcqlCredential>>> { // TODO: Have the result be a flow
        log.debug { "Starting DCQL match. Query: $query, Available Credentials: ${availableCredentials.map { it.id }}" }
        val individualMatches = mutableMapOf<String, List<DcqlCredential>>()

        // 1. Find matches for each individual CredentialQuery
        for (credQuery in query.credentials) {
            log.trace { "Processing CredentialQuery: ${credQuery.id} (format: ${credQuery.format})" }
            val potentialMatches = availableCredentials.filter { it.format in credQuery.format.id }
            log.trace { "Potential matches for ${credQuery.id} based on format: ${potentialMatches.map { it.id }}" }

            val finalMatchesForQuery = potentialMatches.filter { credential ->
                // Apply further filtering based on query constraints
                matchesMeta(credential, credQuery.meta, credQuery.format) &&
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
        val requiredIndividualQueryIds =
            query.credentials.map { it.id } // Assuming all individual queries are implicitly required unless part of an optional set
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


        log.debug { "DCQL Match successful. Result: ${individualMatches.mapValues { it.value.map { c -> c.id } }}" }
        // Return success even if some optional queries weren't matched
        return Result.success(individualMatches)
    }

    // --- Helper Functions ---

    /**
     * Checks if a credential satisfies the claims constraints and returns the selected claims.
     * Returns a Result: Success with Map<String (path), Any (JsonElement or SdJwtSelectiveDisclosure)>?
     * The map is null if not selectively disclosable, empty if no claims selected from SD cred.
     */
    private fun matchesClaimsAndGetSelected(
        credential: DcqlCredential,
        claimsQueries: List<ClaimsQuery>?,
        claimSets: List<List<String>>?,
    ): Result<Map<String, Any>?> { // Map can be null now

        // Determine if this credential format inherently supports SD based on our model
        // A credential uses SD if it has 'disclosables' or 'disclosures'
        val isPotentiallySelectivelyDisclosable = credential.disclosures?.isNotEmpty() == true

        if (claimsQueries.isNullOrEmpty()) {
            log.trace { "No specific claims requested for credential ${credential.id}" }
            // If no claims are queried, return emptyMap if it's an SD cred, null otherwise
            return Result.success(if (isPotentiallySelectivelyDisclosable) emptyMap() else null)
        }

        val claimsQueriesMapById = claimsQueries.associateBy { it.id }
        val collectedSelectedClaims = mutableMapOf<String, Any>()

        val overallMatchLogic = { claimsToEvaluate: List<ClaimsQuery> ->
            var allCurrentSetMatch = true
            val currentSetSelectedClaims = mutableMapOf<String, Any>()
            for (cq in claimsToEvaluate) {
                val matchResult = claimExistsAndMatchesValue(credential, cq, isPotentiallySelectivelyDisclosable)
                if (matchResult.isSuccess) {
                    matchResult.getOrNull()?.let { // getOrNull because success can be with null value (existence check)
                        currentSetSelectedClaims[cq.path.joinToString(".")] = it
                    }
                } else {
                    allCurrentSetMatch = false
                    break // Stop checking this set/list of claims
                }
            }
            if (allCurrentSetMatch) {
                collectedSelectedClaims.putAll(currentSetSelectedClaims)
                true
            } else {
                false
            }
        }

        val finalMatchSuccessful = when {
            claimSets.isNullOrEmpty() -> { // Case 1: All listed claims are required
                log.trace { "Checking all listed claims for credential ${credential.id}" }
                overallMatchLogic(claimsQueries)
            }
            else -> { // Case 2: At least one claim_set must be satisfied
                log.trace { "Checking claim sets for credential ${credential.id}" }
                claimSets.any { setOptionIds -> // Try to satisfy one option
                    log.trace { "Checking claim set option: $setOptionIds" }
                    val claimsForThisSet = setOptionIds.mapNotNull { claimsQueriesMapById[it] }
                    if (claimsForThisSet.size != setOptionIds.size) { // A claimId in set was not in main claims list
                        log.warn{"Not all claim IDs in claim_set $setOptionIds found in main claims list for ${credential.id}"}
                        false
                    } else {
                        overallMatchLogic(claimsForThisSet) // This will populate collectedSelectedClaims if true
                    }
                }
            }
        }

        return if (finalMatchSuccessful) {
            // If it's potentially SD, return the collectedSelectedClaims (could be empty)
            // If not SD, but claims were matched (e.g. from JWT body), return those.
            // If not SD and no claimsQueries, it would have returned null earlier.
            // If not SD and claimsQueries were present, collectedSelectedClaims would contain JsonElements.
            if (isPotentiallySelectivelyDisclosable) {
                Result.success(collectedSelectedClaims)
            } else {
                // If not SD, but we matched claims, return them. If no claims were queried,
                // it would have returned success(null) earlier.
                // If claims were queried but none matched, finalMatchSuccessful would be false.
                Result.success(collectedSelectedClaims.ifEmpty { null })
            }
        } else {
            Result.failure(DcqlMatchException("Claims requirements not met for credential ${credential.id}"))
        }
    }


    /**
     * Checks if a single claim exists, matches optional values, and returns the matched value/disclosure.
     * Returns Result.success(matchedValueOrDisclosure: Any?) or Result.failure.
     * matchedValueOrDisclosure can be SdJwtSelectiveDisclosure or JsonElement.
     * It's null if only existence was checked (claimQuery.values is null/empty) and value was found.
     */
    private fun claimExistsAndMatchesValue(
        credential: DcqlCredential,
        claimQuery: ClaimsQuery,
        isCredentialPotentiallySD: Boolean // Pass this info
    ): Result<Any?> {

        // If the credential has disclosures and is JWT-based (where SD mechanism applies)
        if (isCredentialPotentiallySD && credential.disclosures != null) {
            // More robust path matching for SD-JWTs is needed here.
            // This simplistic approach assumes path.last() is the claim name.
            val targetClaimName = claimQuery.path.lastOrNull()
            val matchingDisclosure = credential.disclosures?.find { it.name == targetClaimName /* && it.location matches claimQuery.path more precisely */ }

            if (matchingDisclosure != null) {
                if (!claimQuery.values.isNullOrEmpty()) {
                    val disclosureValueJson = matchingDisclosure.value
                    val matchesValue = claimQuery.values.any { queryValue -> queryValue == disclosureValueJson }
                    if (!matchesValue) {
                        log.trace { "SD Disclosure '${targetClaimName}' value '${disclosureValueJson}' does not match required values ${claimQuery.values} in ${credential.id}" }
                        return Result.failure(DcqlMatchException("SD Disclosure value mismatch for $targetClaimName"))
                    }
                }
                log.trace { "SD Disclosure '${targetClaimName}' found and matches criteria in ${credential.id}" }
                return Result.success(matchingDisclosure) // Return the disclosure object
            } else {
                // If path not found as a disclosure, it might be an always-visible claim in the SD-JWT core.
                // Fall through to generic path resolution for such cases.
                log.trace { "Claim path ${claimQuery.path} not found among SD disclosures for ${credential.id}. Checking core JWT."}
            }
        }

        // Generic path resolution for non-SD claims or core claims of an SD-JWT
        val claimJsonElement = resolveClaimPath(credential.data, claimQuery.path)
            ?: return Result.failure(DcqlMatchException("Claim path ${claimQuery.path} not found in ${credential.id}"))

        if (!claimQuery.values.isNullOrEmpty()) {
            val matchesValue = claimQuery.values.any { queryValue -> queryValue == claimJsonElement }
            if (!matchesValue) {
                log.trace { "claimExistsAndMatchesValue: Claim path ${claimQuery.path} value '$claimJsonElement' (${if (claimJsonElement is JsonPrimitive && claimJsonElement.isString) "string" else "non-string"}) does not match required values ${claimQuery.values} in ${credential.id}" }
                return Result.failure(DcqlMatchException("Claim value mismatch for ${claimQuery.path}"))
            }
        }
        log.trace { "Claim path ${claimQuery.path} exists and matches criteria in ${credential.id}" }
        // If values were specified and matched, or if no values were specified (existence check), return the element.
        return Result.success(claimJsonElement)
    }

    private fun matchesMeta(
        credential: DcqlCredential,
        metaQuery: CredentialQueryMeta?,
        expectedFormat: CredentialFormat
    ): Boolean {
        // If metaQuery is NoMeta, it means no specific constraints from the query side.
        if (metaQuery == null || metaQuery is NoMeta) {
            log.trace { "No specific meta query constraints (NoMeta) for credential ${credential.id}." }
            return true
        }

        // Ensure the metaQuery type aligns with the credential's actual format
        // This check is important if the metaQuery was constructed independently.
        val formatMatchesQueryType = when (expectedFormat) {
            CredentialFormat.JWT_VC_JSON, CredentialFormat.LDP_VC -> metaQuery is JwtVcJsonMeta
            CredentialFormat.DC_SD_JWT -> metaQuery is SdJwtVcMeta
            CredentialFormat.MSO_MDOC -> metaQuery is MsoMdocMeta
            CredentialFormat.AC_VP -> true // Assuming no specific meta for AC_VP yet or handled by GenericMeta
            // Add other format checks if new CredentialQueryMeta types are added
        }
        if (!formatMatchesQueryType && metaQuery !is GenericMeta) { // Allow GenericMeta to try and match any format
            log.warn {
                "Meta query type ${metaQuery::class.simpleName} does not align with expected credential format $expectedFormat for credential ${credential.id}. " +
                        "This indicates a mismatch in query construction or credential filtering."
            }
            return false
        }


        log.trace { "Checking typed metadata for credential ${credential.id} (format: ${credential.format}) against query: $metaQuery" }

        fun isNotAllowedMetaFormat(vararg allowedMetaForm: CredentialFormat): Boolean =
            credential.format !in allowedMetaForm.flatMap { it.id.toList() }

        return when (metaQuery) {
            is JwtVcJsonMeta -> {
                if (isNotAllowedMetaFormat(CredentialFormat.JWT_VC_JSON, CredentialFormat.LDP_VC)) {
                    log.warn { "W3cCredentialMeta applied to non-W3C format ${credential.format} for ${credential.id}" }
                    return false
                }
                val credTypesElement = credential.data["type"] ?: credential.data["vc"]?.jsonObject["type"]
                if (credTypesElement !is JsonArray) {
                    log.warn { "W3C credential ${credential.id} 'type' field is missing or not an array." }
                    return false
                }
                val credTypes = credTypesElement.mapNotNull { it.jsonPrimitive.contentOrNull }

                // Spec B.1.1: "Each of the top-level arrays specifies one alternative to match..."
                // "...Each inner array specifies a set of fully expanded types that MUST be present..."
                // Spec B.1.1: "type_values: REQUIRED. A non-empty array of string arrays..."
                // The init block in W3cCredentialMeta now enforces non-empty.
                metaQuery.typeValues.any { requiredTypeSet ->
                    requiredTypeSet.all { requiredType -> credTypes.contains(requiredType) }
                }
            }

            is SdJwtVcMeta -> {
                if (isNotAllowedMetaFormat(CredentialFormat.DC_SD_JWT)) {
                    log.warn { "SdJwtVcMeta applied to non-SD-JWT format ${credential.format} for ${credential.id}" }
                    return false
                }
                val vctClaim = credential.data["vct"]?.jsonPrimitive?.contentOrNull
                if (vctClaim == null) {
                    log.warn { "SD-JWT VC ${credential.id} 'vct' claim is missing." }
                    return false
                }
                // Spec B.3.5: "vct_values: REQUIRED. A non-empty array of strings..."
                // The init block in SdJwtVcMeta now enforces non-empty.
                metaQuery.vctValues.contains(vctClaim)
            }

            is MsoMdocMeta -> {
                if (isNotAllowedMetaFormat(CredentialFormat.MSO_MDOC)) {
                    log.warn { "MsoMdocMeta applied to non-mdoc format ${credential.format} for ${credential.id}" }
                    return false
                }
                val docTypeClaim = credential.data["docType"]?.jsonPrimitive?.contentOrNull // Or "doctype"
                if (docTypeClaim == null) {
                    log.warn { "Mdoc credential ${credential.id} 'docType' (or 'doctype') claim is missing." }
                    return false
                }
                // doctypeValue is already non-nullable in MsoMdocMeta
                metaQuery.doctypeValue == docTypeClaim
            }

            is GenericMeta -> {
                log.trace { "GenericMeta check for ${credential.id}: ${metaQuery.properties}. (Simplified: returning true)" }
                // Implement matching logic for generic properties (if needed?)
                // e.g.: metaQuery.properties.all { (key, queryValue) -> credential.data[key] == queryValue }
                true
            }

            is NoMeta -> true // This case is now handled at the beginning of the function.
        }
    }

    /** Placeholder: Check issuer constraints. */
    private fun matchesTrustedAuthorities(
        credential: DcqlCredential,
        authoritiesQuery: List<TrustedAuthoritiesQuery>?,
    ): Boolean {
        if (authoritiesQuery.isNullOrEmpty()) return true
        log.trace { "Checking trusted authorities for credential ${credential.id} (simplified: returning true)" }
        // Actual implementation requires checking credential.issuer against
        // the types and values in authoritiesQuery. May involve trust list lookups.
        return true // TODO
    }

    /** Check if a credential satisfies the claims constraints. */
    private fun matchesClaims(
        credential: DcqlCredential,
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
    private fun claimExistsAndMatches(credential: DcqlCredential, claimQuery: ClaimsQuery): Boolean {
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
                log.trace { "claimExistsAndMatches: Claim path ${claimQuery.path} value '$claimValue' does not match required values ${claimQuery.values} in credential ${credential.id}" }
                return false
            }
        }
        log.trace { "Claim path ${claimQuery.path} exists and matches criteria in credential ${credential.id}" }
        return true // Claim exists and matches value constraints (if any)
    }

    /** Basic JSON path resolver. Needs enhancement for arrays, different formats. */
    fun resolveClaimPath(data: JsonObject, path: List<String>): JsonElement? {
        var currentElement: JsonElement? = data["vc"] as? JsonObject ?: data
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

