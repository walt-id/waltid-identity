package id.walt.dcql

import id.walt.dcql.jsonld.JsonLdContextDocumentSource
import id.walt.dcql.jsonld.W3cTypeExpander
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
        val originalQuery: CredentialQuery,
        /**
         * Disclosures to present per claims path: the disclosures of the selected claims and of every
         * selectively disclosable claim containing them (RFC 9901 §4.2.6), never their descendants.
         * Empty for a path whose selected claims are all visible without disclosures.
         */
        val disclosuresByPath: Map<String, List<DcqlDisclosure>> = emptyMap(),
    )

    /**
     * Discovers credentials that match individual DCQL queries without requiring the full request
     * to be satisfiable. Missing queries and unsatisfied credential sets are omitted from the
     * result instead of failing the match.
     *
     * W3C types are expanded with the bundled context documents. Pass [contextDocuments] on the
     * overload that takes a [JsonLdContextDocumentSource] to add more.
     */
    fun findMatches(
        query: DcqlQuery,
        availableCredentials: List<DcqlCredential>,
        trustedAuthoritiesChecker: ((DcqlCredential, List<TrustedAuthoritiesQuery>) -> Boolean)? = null,
    ): Result<Map<String, List<DcqlMatchResult>>> =
        findMatches(query, availableCredentials, JsonLdContextDocumentSource.bundled, trustedAuthoritiesChecker)

    fun findMatches(
        query: DcqlQuery,
        availableCredentials: List<DcqlCredential>,
        contextDocuments: JsonLdContextDocumentSource,
        trustedAuthoritiesChecker: ((DcqlCredential, List<TrustedAuthoritiesQuery>) -> Boolean)? = null,
    ): Result<Map<String, List<DcqlMatchResult>>> {
        log.debug { "Starting DCQL discovery. Query: $query, Available Credentials Count: ${availableCredentials.size}" }

        val individualMatches = mutableMapOf<String, MutableList<DcqlMatchResult>>()

        for (credentialQuery in query.credentials) {
            log.trace { "Processing CredentialQuery: ${credentialQuery.id} (format: ${credentialQuery.format})" }
            if (!credentialQuery.trustedAuthorities.isNullOrEmpty() && trustedAuthoritiesChecker == null) {
                return Result.failure(
                    UnsupportedDcqlConstraintException(
                        "trusted_authorities matching requires a trusted-authorities checker",
                    )
                )
            }
            val potentialMatchesByFormat = availableCredentials.filter { it.format in credentialQuery.format.id }
            log.trace { "Potential matches for ${credentialQuery.id} based on format: ${potentialMatchesByFormat.map { it.id + "(${it.format})" }}" }

            val successfullyMatchedCredentialsForThisQuery = mutableListOf<DcqlMatchResult>()

            for (credential in potentialMatchesByFormat) {
                val metaCheck = matchesMeta(credential, credentialQuery.meta, credentialQuery.format, contextDocuments)
                if (!metaCheck) {
                    log.trace { "Credential ${credential.id} failed meta check for query ${credentialQuery.id}" }
                    continue
                }

                val authoritiesCheck = matchesTrustedAuthorities(credential, credentialQuery.trustedAuthorities, trustedAuthoritiesChecker)
                if (!authoritiesCheck) {
                    log.trace { "Credential ${credential.id} failed trusted authorities check for query ${credentialQuery.id}" }
                    continue
                }

                val claimsMatchResult = matchesClaimsAndGetSelected(credential, credentialQuery.claims, credentialQuery.claimSets)

                if (claimsMatchResult.isSuccess) {
                    val claimsSelection = claimsMatchResult.getOrThrow()
                    successfullyMatchedCredentialsForThisQuery.add(
                        DcqlMatchResult(
                            credential = credential,
                            selectedDisclosures = claimsSelection.selectedDisclosures,
                            originalQuery = credentialQuery,
                            disclosuresByPath = claimsSelection.disclosuresByPath,
                        )
                    )
                    log.trace { "Credential ${credential.id} processed for claims for query ${credentialQuery.id}. Selected: ${claimsSelection.selectedDisclosures}" }
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
        return Result.success(finalIndividualMatches)
    }

    /**
     * Matches available credentials against a DCQL query.
     *
     * @param query The parsed DCQL query.
     * @param availableCredentials The list of credentials held by the wallet.
     * @param trustedAuthoritiesChecker Optional callback invoked when a credential query has
     *   `trusted_authorities` constraints. Receives the credential and the list of authority
     *   queries; should return `true` if the credential satisfies at least one authority.
     *   When null (default), credentials cannot match a trusted_authorities constraint.
     * @return A Result containing a map where keys are CredentialQuery IDs and
     *         values are lists of matching Credentials, or a failure with an exception.
     */
    fun match(
        query: DcqlQuery,
        availableCredentials: List<DcqlCredential>,
        trustedAuthoritiesChecker: ((DcqlCredential, List<TrustedAuthoritiesQuery>) -> Boolean)? = null,
    ): Result<Map<String, List<DcqlMatchResult>>> =
        match(query, availableCredentials, JsonLdContextDocumentSource.bundled, trustedAuthoritiesChecker)

    fun match(
        query: DcqlQuery,
        availableCredentials: List<DcqlCredential>,
        contextDocuments: JsonLdContextDocumentSource,
        trustedAuthoritiesChecker: ((DcqlCredential, List<TrustedAuthoritiesQuery>) -> Boolean)? = null,
    ): Result<Map<String, List<DcqlMatchResult>>> {
        val discovered = findMatches(query, availableCredentials, contextDocuments, trustedAuthoritiesChecker)
        if (discovered.isFailure) return discovered
        val finalIndividualMatches = discovered.getOrThrow()
        enforceSatisfaction(query, finalIndividualMatches.keys)?.let { return Result.failure(it) }

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
        availableCredentials: List<DcqlCredential>,
        trustedAuthoritiesChecker: ((DcqlCredential, List<TrustedAuthoritiesQuery>) -> Boolean)? = null,
    ): Result<Map<String, List<DcqlCredential>>> =
        matchWithoutClaims(query, availableCredentials, JsonLdContextDocumentSource.bundled, trustedAuthoritiesChecker)

    fun matchWithoutClaims(
        query: DcqlQuery,
        availableCredentials: List<DcqlCredential>,
        contextDocuments: JsonLdContextDocumentSource,
        trustedAuthoritiesChecker: ((DcqlCredential, List<TrustedAuthoritiesQuery>) -> Boolean)? = null,
    ): Result<Map<String, List<DcqlCredential>>> {
        val discovered = findMatches(query, availableCredentials, contextDocuments, trustedAuthoritiesChecker)
        val individualMatches = discovered.getOrElse { return Result.failure(it) }
            .mapValues { (_, results) -> results.map { it.credential } }
        enforceSatisfaction(query, individualMatches.keys)?.let { return Result.failure(it) }
        return Result.success(individualMatches)
    }

    private fun enforceSatisfaction(
        query: DcqlQuery,
        matchedQueryIds: Set<String>,
    ): RequiredCredentialUnavailableException? {
        query.credentialSets?.let { sets ->
            if (!checkCredentialSets(sets, matchedQueryIds)) {
                val errorMsg = "Required credential set constraints not met."
                log.warn { errorMsg }
                return RequiredCredentialUnavailableException(emptyList(), errorMsg)
            }
            return null
        }
        val missingRequired = query.credentials.map { it.id }
            .filterNot(matchedQueryIds::contains)
        if (missingRequired.isNotEmpty()) {
            val errorMsg = "No matches found for required credential queries: $missingRequired"
            log.warn { errorMsg }
            return RequiredCredentialUnavailableException(missingRequired, errorMsg)
        }
        return null
    }

    // --- Helper Functions ---

    private class ClaimsSelection(
        val selectedDisclosures: Map<String, Any>?,
        val disclosuresByPath: Map<String, List<DcqlDisclosure>>,
    )

    /**
     * One claims query matched against a credential.
     *
     * @property value the value [DcqlMatchResult.selectedDisclosures] holds for the claim
     * @property disclosures the disclosures to present for it, as in [DcqlMatchResult.disclosuresByPath]
     */
    data class ClaimMatch(
        val value: Any?,
        val disclosures: List<DcqlDisclosure>,
    )

    /** Matches a single claims query, e.g. an optional claim outside the claim set [match] chose. */
    fun matchClaim(credential: DcqlCredential, claimQuery: ClaimsQuery): Result<ClaimMatch> =
        claimExistsAndMatchesValue(credential, claimQuery, credential.disclosures?.isNotEmpty() == true)

    /**
     * Checks if a credential satisfies the claims constraints and returns the selected claims.
     * Returns a Result: Success with Map<String (path), Any (JsonElement or SdJwtSelectiveDisclosure)>?
     * The map is null if not selectively disclosable, empty if no claims selected from SD cred.
     */
    private fun matchesClaimsAndGetSelected(
        credential: DcqlCredential,
        claimsQueries: List<ClaimsQuery>?,
        claimSets: List<List<String>>?,
    ): Result<ClaimsSelection> {

        // Determine if this credential format inherently supports SD based on our model
        // A credential uses SD if it has 'disclosables' or 'disclosures'
        val isPotentiallySelectivelyDisclosable = credential.disclosures?.isNotEmpty() == true

        if (claimsQueries.isNullOrEmpty()) {
            log.trace { "No specific claims requested for credential ${credential.id}" }
            // If no claims are queried, return emptyMap if it's an SD cred, null otherwise
            return Result.success(ClaimsSelection(if (isPotentiallySelectivelyDisclosable) emptyMap() else null, emptyMap()))
        }

        val claimsQueriesMapById = claimsQueries.associateBy { it.id }
        val collectedSelectedClaims = mutableMapOf<String, Any>()
        val collectedDisclosures = mutableMapOf<String, List<DcqlDisclosure>>()

        val overallMatchLogic = { claimsToEvaluate: List<ClaimsQuery> ->
            var allCurrentSetMatch = true
            val currentSetSelectedClaims = mutableMapOf<String, Any>()
            val currentSetDisclosures = mutableMapOf<String, List<DcqlDisclosure>>()
            for (cq in claimsToEvaluate) {
                val matchResult = claimExistsAndMatchesValue(credential, cq, isPotentiallySelectivelyDisclosable)
                if (matchResult.isSuccess) {
                    val claimMatch = matchResult.getOrThrow()
                    val pathKey = cq.path.joinToString(".")
                    claimMatch.value?.let { currentSetSelectedClaims[pathKey] = it }
                    if (isPotentiallySelectivelyDisclosable) currentSetDisclosures[pathKey] = claimMatch.disclosures
                } else {
                    allCurrentSetMatch = false
                    break // Stop checking this set/list of claims
                }
            }
            if (allCurrentSetMatch) {
                collectedSelectedClaims.putAll(currentSetSelectedClaims)
                collectedDisclosures.putAll(currentSetDisclosures)
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
                        log.warn { "Not all claim IDs in claim_set $setOptionIds found in main claims list for ${credential.id}" }
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
                Result.success(ClaimsSelection(collectedSelectedClaims, collectedDisclosures))
            } else {
                // If not SD, but we matched claims, return them. If no claims were queried,
                // it would have returned success(null) earlier.
                // If claims were queried but none matched, finalMatchSuccessful would be false.
                Result.success(ClaimsSelection(collectedSelectedClaims.ifEmpty { null }, emptyMap()))
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
    ): Result<ClaimMatch> {

        // Selectively disclosable credential: select by claims path (OpenID4VP §7.1.1) and release the
        // disclosures of the selected claims plus those containing them (RFC 9901 §4.2.6).
        if (isCredentialPotentiallySD && credential.disclosures != null) {
            return matchSelectivelyDisclosableClaim(credential, claimQuery)
        }

        // Generic path resolution for credentials without selective disclosure
        val claimJsonElement = resolveClaimPath(credential.data, claimQuery.path)
            ?: return Result.failure(DcqlMatchException("Claim path ${claimQuery.path.joinToString(".")} not found in ${credential.id}"))

        if (!claimQuery.values.isNullOrEmpty()) {
            val matchesValue = claimQuery.values.any { queryValue -> queryValue == claimJsonElement }
            if (!matchesValue) {
                log.trace { "claimExistsAndMatchesValue: Claim path ${claimQuery.path.joinToString(".")} value '$claimJsonElement' does not match required values ${claimQuery.values} in ${credential.id} " }
                return Result.failure(DcqlMatchException("Claim value mismatch for ${claimQuery.path.joinToString(".")}"))
            }
        }
        log.trace { "Claim path ${claimQuery.path.joinToString(".")} exists and matches criteria in ${credential.id}" }
        // If values were specified and matched, or if no values were specified (existence check), return the element.
        return Result.success(ClaimMatch(claimJsonElement, emptyList()))
    }

    private fun matchSelectivelyDisclosableClaim(credential: DcqlCredential, claimQuery: ClaimsQuery): Result<ClaimMatch> {
        val vcObject = credential.data["vc"] as? JsonObject
        val root = vcObject ?: credential.data
        val selectedClaims = processClaimsPath(root, claimQuery.path).getOrElse { error ->
            // Callers may pass disclosures without the claims they reveal in data; select by disclosure location then
            if (error !is ClaimNotFoundException) return Result.failure(error)
            credential.disclosures.orEmpty().mapNotNull { disclosure ->
                disclosure.location?.takeIf { it.matchesClaimsPath(claimQuery.path) }?.let { it to disclosure.value }
            }.ifEmpty { return Result.failure(error) }
        }.filter { (_, value) -> claimQuery.values.isNullOrEmpty() || claimQuery.values.any { it == value } }
        if (selectedClaims.isEmpty()) {
            return Result.failure(DcqlMatchException("Claim value mismatch for ${claimQuery.path.joinToString(".")}"))
        }

        // Disclosure locations are relative to the issuer-signed payload, which may wrap the claims in "vc".
        val selectedLocations = selectedClaims.flatMap { (location, _) -> listOfNotNull(location, vcObject?.let { listOf(JsonPrimitive("vc")) + location }) }
        val disclosures = credential.disclosures.orEmpty().filter { disclosure ->
            val location = disclosure.location ?: disclosure.name?.let { listOf(JsonPrimitive(it)) } ?: return@filter false
            selectedLocations.any { selected -> location.isSameOrAncestorOf(selected) }
        }
        log.trace { "Claim path ${claimQuery.path.joinToString(".")} selects ${selectedClaims.size} claim(s) and ${disclosures.size} disclosure(s) in ${credential.id}" }

        // selectedDisclosures keeps its shape: the disclosure for a single disclosed claim, otherwise the selected value(s)
        val (_, value) = selectedClaims.singleOrNull() ?: return Result.success(ClaimMatch(JsonArray(selectedClaims.map { it.second }), disclosures))
        val exactDisclosure = disclosures.singleOrNull { disclosure ->
            val location = disclosure.location ?: disclosure.name?.let { listOf(JsonPrimitive(it)) }
            location != null && selectedLocations.any { it.size == location.size && location.isSameOrAncestorOf(it) }
        }
        return Result.success(ClaimMatch(exactDisclosure ?: value, disclosures))
    }

    /**
     * Processes a claims path pointer against [root] (OpenID4VP §7.1.1) and returns each selected claim
     * with its concrete path (array indexes resolved). Fails when a component does not apply to a
     * selected element or the selection becomes empty.
     */
    fun processClaimsPath(
        root: JsonObject,
        path: List<JsonElement>,
    ): Result<List<Pair<List<JsonElement>, JsonElement>>> {
        var selected: List<Pair<List<JsonElement>, JsonElement>> = listOf(emptyList<JsonElement>() to root)
        for (component in path) {
            val next = mutableListOf<Pair<List<JsonElement>, JsonElement>>()
            for ((location, element) in selected) {
                when {
                    component is JsonPrimitive && component.isString -> {
                        val jsonObject = element as? JsonObject
                            ?: return Result.failure(DcqlMatchException("Claims path component \"${component.content}\" applied to a non-object element"))
                        jsonObject[component.content]?.let { next += (location + component) to it }
                    }
                    component is JsonNull -> {
                        val array = element as? JsonArray
                            ?: return Result.failure(DcqlMatchException("Claims path component null applied to a non-array element"))
                        array.forEachIndexed { index, item -> next += (location + JsonPrimitive(index)) to item }
                    }
                    component is JsonPrimitive && component.intOrNull?.let { it >= 0 } == true -> {
                        val array = element as? JsonArray
                            ?: return Result.failure(DcqlMatchException("Claims path index ${component.content} applied to a non-array element"))
                        array.getOrNull(component.int)?.let { next += (location + JsonPrimitive(component.int)) to it }
                    }
                    else -> return Result.failure(DcqlMatchException("Invalid claims path component: $component"))
                }
            }
            if (next.isEmpty()) {
                return Result.failure(ClaimNotFoundException("Claims path ${path.joinToString(".")} selects no claim"))
            }
            selected = next
        }
        return Result.success(selected)
    }

    private class ClaimNotFoundException(message: String) : DcqlMatchException(message)

    /** True if this concrete claims path is matched by the claims path pointer [pointer] (null matches any index). */
    private fun List<JsonElement>.matchesClaimsPath(pointer: List<JsonElement>): Boolean =
        size == pointer.size && indices.all { i ->
            val segment = this[i]
            if (pointer[i] is JsonNull) segment is JsonPrimitive && !segment.isString && segment.intOrNull?.let { it >= 0 } == true
            else isSameSegment(pointer[i], segment)
        }

    /** True if this claims path equals [other] or is one of its ancestors; indexes compare by number. */
    private fun List<JsonElement>.isSameOrAncestorOf(other: List<JsonElement>): Boolean =
        size <= other.size && indices.all { i -> isSameSegment(this[i], other[i]) }

    private fun isSameSegment(a: JsonElement, b: JsonElement): Boolean {
        if (a !is JsonPrimitive || b !is JsonPrimitive) return a == b
        if (a.isString != b.isString) return false
        return if (a.isString) a.content == b.content else a.intOrNull != null && a.intOrNull == b.intOrNull
    }

    private fun matchesMeta(
        credential: DcqlCredential,
        metaQuery: CredentialQueryMeta?,
        expectedFormat: CredentialFormat,
        contextDocuments: JsonLdContextDocumentSource,
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
                val credentialNode = W3cTypeExpander.w3cCredentialNode(credential.data)
                val credTypesElement = credentialNode["type"]
                if (credTypesElement !is JsonArray) {
                    log.warn { "W3C credential ${credential.id} 'type' field is missing or not an array." }
                    return false
                }
                val credTypes = credTypesElement.mapNotNull { it.jsonPrimitive.contentOrNull }
                // OpenID4VP 1.0 Appendix B.1.1 compares expanded IRIs only. Compact strings are also
                // accepted so existing walt.id queries that send the compact name keep matching.
                // That is a deliberate superset of the spec. Expansion runs only once a required
                // value is missing from the compact type array.
                var cachedExpandedTypes: Set<String>? = null
                fun expandedTypes(): Set<String> = cachedExpandedTypes
                    ?: W3cTypeExpander.expandedTypes(credential.data, contextDocuments).also { cachedExpandedTypes = it }

                metaQuery.typeValues.any { requiredTypeSet ->
                    requiredTypeSet.all { requiredType ->
                        requiredType in credTypes || requiredType in expandedTypes()
                    }
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

    /** Check issuer trust constraints. Delegates to [trustedAuthoritiesChecker] when provided. */
    private fun matchesTrustedAuthorities(
        credential: DcqlCredential,
        authoritiesQuery: List<TrustedAuthoritiesQuery>?,
        trustedAuthoritiesChecker: ((DcqlCredential, List<TrustedAuthoritiesQuery>) -> Boolean)?,
    ): Boolean {
        if (authoritiesQuery.isNullOrEmpty()) return true
        if (trustedAuthoritiesChecker == null) {
            log.warn { "trusted_authorities query present for credential ${credential.id} but no checker was provided; failing closed" }
            return false
        }
        return trustedAuthoritiesChecker(credential, authoritiesQuery)
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
    fun resolveClaimPath(data: JsonObject, path: List<JsonElement>): JsonElement? {
        var currentElements: List<JsonElement> = listOf(data["vc"] as? JsonObject ?: data)

        for (segment in path) {
            val nextElements = mutableListOf<JsonElement>()

            for (element in currentElements) {
                when (element) {
                    is JsonObject -> {
                        if (segment is JsonPrimitive && segment.isString) {
                            element[segment.content]?.let { nextElements.add(it) }
                        }
                    }

                    is JsonArray -> {
                        if (segment is JsonNull) {
                            // null means select all elements in the array
                            nextElements.addAll(element)
                        } else if (segment is JsonPrimitive && segment.intOrNull != null) {
                            // integer means select specific index
                            val index = segment.int
                            if (index in 0 until element.size) {
                                nextElements.add(element[index])
                            }
                        }
                    }

                    else -> throw NotImplementedError()
                }
            }

            if (nextElements.isEmpty()) return null
            currentElements = nextElements
        }

        // If the final result is a single element, return it.
        // If it resulted from a wildcard (null), return it as a JsonArray.
        return if (currentElements.size == 1 && path.lastOrNull() !is JsonNull) {
            currentElements.first()
        } else {
            JsonArray(currentElements)
        }
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
