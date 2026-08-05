package id.walt.cli.util

import id.walt.credentials.trustedauthorities.DcqlTrustedAuthoritiesChecker
import id.walt.dcql.DcqlCredential
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.TrustedAuthoritiesQuery
import id.walt.dcql.models.TrustedAuthorityType

object CliDcql {
    private val trustedAuthoritiesChecker: (DcqlCredential, List<TrustedAuthoritiesQuery>) -> Boolean =
        DcqlTrustedAuthoritiesChecker.checker

    fun match(
        query: DcqlQuery,
        credentials: List<DcqlCredential>,
    ): Map<String, List<DcqlMatcher.DcqlMatchResult>> {
        validate(query)
        val matches = query.credentials.mapNotNull { credentialQuery ->
            val queryMatches = credentials.flatMap { credential ->
                DcqlMatcher.match(
                    query = DcqlQuery(credentials = listOf(credentialQuery)),
                    availableCredentials = listOf(credential),
                    trustedAuthoritiesChecker = trustedAuthoritiesChecker,
                ).getOrThrow()[credentialQuery.id].orEmpty()
            }
            require(credentialQuery.multiple || queryMatches.size <= 1) {
                "DCQL query ${credentialQuery.id} has ${queryMatches.size} matches but multiple=false"
            }
            queryMatches.takeIf(List<DcqlMatcher.DcqlMatchResult>::isNotEmpty)
                ?.let { credentialQuery.id to it }
        }.toMap()
        enforceCredentialSets(query, matches.keys)
        return matches
    }

    fun requireExactResponseCardinality(query: CredentialQuery, presentations: Int, credentials: Int) {
        require(query.multiple || presentations == 1) {
            "DCQL query ${query.id} requires exactly one presentation when multiple=false"
        }
        require(query.multiple || credentials == 1) {
            "DCQL query ${query.id} requires exactly one credential when multiple=false"
        }
        require(presentations > 0 && credentials > 0) { "DCQL query ${query.id} has an empty response" }
    }

    fun enforceCredentialSets(query: DcqlQuery, matchedQueryIds: Set<String>) {
        if (query.credentialSets == null) {
            val missing = query.credentials.map { it.id }.filterNot(matchedQueryIds::contains)
            require(missing.isEmpty()) { "No credential matched required DCQL query IDs: $missing" }
            return
        }
        query.credentialSets.orEmpty().forEach { set ->
            if (set.required) {
                require(set.options.any { option -> option.all(matchedQueryIds::contains) }) {
                    "Required DCQL credential set is not satisfied: ${set.options}"
                }
            }
        }
    }

    fun validate(query: DcqlQuery) {
        query.precheck()
        val queryIds = query.credentials.map(CredentialQuery::id)
        require(queryIds.distinct().size == queryIds.size) { "DCQL credential query IDs must be unique" }
        query.credentials.forEach { credentialQuery ->
            credentialQuery.trustedAuthorities.orEmpty().forEach { authority ->
                require(authority.values.isNotEmpty() && authority.values.none(String::isBlank)) {
                    "trusted_authorities values must be non-empty"
                }
                require(authority.type == TrustedAuthorityType.AKI) {
                    "Unsupported trusted_authorities type: ${authority.type}; CLI supports aki only"
                }
            }
        }
        query.credentialSets.orEmpty().forEach { set ->
            require(set.options.isNotEmpty() && set.options.all(List<String>::isNotEmpty)) {
                "DCQL credential set options must be non-empty"
            }
            require(set.options.flatten().all(queryIds::contains)) {
                "DCQL credential set references an unknown query ID"
            }
        }
    }
}
