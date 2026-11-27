package id.walt.wallet2.handlers

import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.credentials.signatures.sdjwt.locatedDisclosures
import id.walt.crypto2.keys.KeyUsage
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.DcqlMatchingContext
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.sdjwt.metadata.type.ResolvedTypeMetadata
import id.walt.wallet2.data.HolderKeyBindingException
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.resolveHolderKey
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Streams credentials from wallet credential stores, converts each to a
 * [RawDcqlCredential], then runs DCQL matching — mirrors the Enterprise
 * WalletPresentFunctionality.selectCredentialsForQuery exactly.
 *
 * When [eligibleCredentialIds] is non-null, only those stored credentials are
 * candidates. Digital Credentials API preview and submit use this so a
 * `multiple=false` query rematches the OS-selected credential instead of
 * collapsing to the first store match.
 */
internal suspend fun selectFromStores(
    wallet: Wallet,
    query: DcqlQuery,
    useWalletCredentialIds: Boolean = false,
    eligibleCredentialIds: Set<String>? = null,
    metadataHttpClient: HttpClient? = null,
): Map<String, List<DcqlMatcher.DcqlMatchResult>> = selectFromSnapshot(
    wallet = wallet,
    query = query,
    storedCredentials = wallet.streamAllCredentials().toList(),
    useWalletCredentialIds = useWalletCredentialIds,
    eligibleCredentialIds = eligibleCredentialIds,
    metadataHttpClient = metadataHttpClient,
).matches

/**
 * Selects credentials for automatic presentation after excluding stored mdocs that cannot
 * resolve their certified holder key. Eligibility is evaluated before a query that disallows
 * multiple credentials chooses its single result, so an unusable earlier match cannot hide a
 * later presentable credential.
 */
internal suspend fun selectPresentableFromStores(
    wallet: Wallet,
    query: DcqlQuery,
): Map<String, List<DcqlMatcher.DcqlMatchResult>> {
    val storedById = wallet.streamAllCredentials().toList().associateBy { it.id }
    val eligibility = selectHolderKeyEligibleFromSnapshot(
        wallet = wallet,
        query = query,
        storedById = storedById,
    )
    val availableQueryIds = eligibility.matched.filterValues { it.isNotEmpty() }.keys
    val requirements = query.requiredCredentialRequirements()
    if (!requirements.satisfiedBy(availableQueryIds)) {
        eligibility.bindingFailureForUnsatisfiedRequirements(
            requirements,
            availableQueryIds,
        )?.let { throw it }
    }
    return eligibility.matched
}

/**
 * Matches one immutable credential-store snapshot so eligibility checks and consent options are
 * derived from the same stored records instead of independently re-reading mutable stores.
 *
 * When [eligibleCredentialIds] is non-null, only those stored credentials are candidates so a
 * `multiple=false` query rematches the OS-selected credential instead of collapsing to the first
 * store match.
 */
internal suspend fun selectFromSnapshot(
    wallet: Wallet,
    query: DcqlQuery,
    storedCredentials: List<StoredCredential>,
    useWalletCredentialIds: Boolean,
    eligibleCredentialIds: Set<String>? = null,
    metadataHttpClient: HttpClient? = null,
): CredentialSelection {
    if (wallet.credentialStores.isEmpty()) {
        error("Wallet has no credential stores — use presentCredentialIsolated to present inline credentials")
    }

    val candidates = storedCredentials.filter { stored ->
        eligibleCredentialIds == null || stored.id in eligibleCredentialIds
    }
    val resolvedCandidates = candidates.mapIndexedNotNull { idx, stored ->
        log.trace { "  credential[$idx]: id=${stored.id}, format=${stored.credential.format}, issuer=${stored.credential.issuer}" }
        stored.toDcqlCandidate(
            id = if (useWalletCredentialIds) stored.id else idx.toString(),
            query = query,
            metadataHttpClient = metadataHttpClient,
        )
    }

    log.debug { "DCQL matching against ${candidates.size} stored credential(s), queries=${query.credentials.map { it.id }}" }
    val matched = DcqlMatcher.findMatches(query, resolvedCandidates.map { it.credential }, resolvedCandidates.matchingContext()).getOrThrow()
    log.trace { "DCQL match result: matchedQueryIds=${matched.keys}, matchCounts=${matched.mapValues { it.value.size }}" }
    return CredentialSelection(matched, resolvedCandidates.mapNotNull { candidate ->
        candidate.metadata?.snapshot()?.let { candidate.credential.id to it }
    }.toMap())
}

internal suspend fun selectHolderKeyEligibleFromSnapshot(
    wallet: Wallet,
    query: DcqlQuery,
    storedById: Map<String, StoredCredential>,
    allowedCredentialIds: Set<String>? = null,
): HolderKeyEligibleMatches {
    val originalQueries = query.credentials.associateBy { it.id }
    val candidateQuery = query.copy(
        credentials = query.credentials.map { it.copy(multiple = true) },
    )
    val structuralMatches = selectFromSnapshot(
        wallet = wallet,
        query = candidateQuery,
        storedCredentials = storedById.values.toList(),
        useWalletCredentialIds = true,
        eligibleCredentialIds = allowedCredentialIds,
    ).matches.mapValues { (queryId, results) ->
        val originalQuery = originalQueries.getValue(queryId)
        results.map { it.copy(originalQuery = originalQuery) }
    }
    val eligibility = wallet.holderKeyEligibleMatches(
        matched = structuralMatches,
        storedById = storedById,
        allowedCredentialIds = allowedCredentialIds,
    )
    return eligibility.copy(
        matched = eligibility.matched.mapValues { (queryId, results) ->
            if (originalQueries.getValue(queryId).multiple) results else results.take(1)
        },
    )
}

internal data class HolderKeyEligibleMatches(
    val matched: Map<String, List<DcqlMatcher.DcqlMatchResult>>,
    val bindingFailuresByQueryId: Map<String, List<HolderKeyBindingException>>,
) {
    fun bindingFailureForUnavailablePresentation(
        transactionDataCredentialIdGroups: List<List<String>>,
        requirements: List<PresentationCredentialRequirement>,
        availableCredentialQueryIds: Set<String>,
    ): HolderKeyBindingException? {
        val hasUnavailableTransactionData = transactionDataCredentialIdGroups.any { credentialIds ->
            credentialIds.none(availableCredentialQueryIds::contains)
        }
        return if (hasUnavailableTransactionData) {
            bindingFailureForUnavailableTransactionData(
                credentialIdGroups = transactionDataCredentialIdGroups,
                availableCredentialQueryIds = availableCredentialQueryIds,
            )
        } else {
            bindingFailureForUnsatisfiedRequirements(requirements, availableCredentialQueryIds)
        }
    }

    fun bindingFailureForUnsatisfiedRequirements(
        requirements: List<PresentationCredentialRequirement>,
        availableCredentialQueryIds: Set<String>,
    ): HolderKeyBindingException? = requirements.asSequence()
        .filterNot { listOf(it).satisfiedBy(availableCredentialQueryIds) }
        .flatMap { requirement ->
            requirement.options.asSequence().filter { option ->
                option.isNotEmpty() &&
                    option.any { it !in availableCredentialQueryIds } &&
                    option.all { queryId ->
                        queryId in availableCredentialQueryIds ||
                            bindingFailuresByQueryId[queryId].orEmpty().isNotEmpty()
                    }
            }
        }
        .flatMap { option ->
            option.asSequence().flatMap { bindingFailuresByQueryId[it].orEmpty().asSequence() }
        }
        .firstOrNull()

    fun bindingFailureForUnavailableTransactionData(
        credentialIdGroups: List<List<String>>,
        availableCredentialQueryIds: Set<String>,
    ): HolderKeyBindingException? = credentialIdGroups
        .firstOrNull { credentialIds -> credentialIds.none(availableCredentialQueryIds::contains) }
        ?.asSequence()
        ?.flatMap { bindingFailuresByQueryId[it].orEmpty().asSequence() }
        ?.firstOrNull()

    fun firstBindingFailure(): HolderKeyBindingException? =
        bindingFailuresByQueryId.values.asSequence().flatten().firstOrNull()
}

internal data class CredentialSelection(
    val matches: Map<String, List<DcqlMatcher.DcqlMatchResult>>,
    val typeMetadataSnapshots: Map<String, JsonObject>,
)

internal data class CredentialCandidate(val credential: RawDcqlCredential, val metadata: ResolvedTypeMetadata?)

internal fun List<CredentialCandidate>.matchingContext() = DcqlMatchingContext(
    associate { it.credential to it.metadata?.types.orEmpty() },
)

/** An unusable stored credential cannot fail matching for the other candidates. */
internal suspend fun StoredCredential.toDcqlCandidate(
    id: String, query: DcqlQuery? = null, metadataHttpClient: HttpClient? = null,
): CredentialCandidate? {
    val credential = this.credential
    val sdvc = credential as? SelectivelyDisclosableVerifiableCredential
    val disclosures = try {
        sdvc?.locatedDisclosures()
    } catch (e: IllegalArgumentException) {
        log.warn { "Skipping credential $id: its disclosures cannot be located in the issuer-signed payload (${e.message})" }
        return null
    }
    val resolved = try {
        resolveTypeMetadata(httpClient = metadataHttpClient, fetchIfMissing = query?.credentials?.any { item ->
            val meta = item.meta as? SdJwtVcMeta
            meta != null && credential.credentialData["vct"]?.jsonPrimitive?.content !in meta.vctValues
        } == true)
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: IllegalArgumentException) {
        log.warn { "Skipping credential $id: its type metadata is invalid" }
        return null
    }
    return CredentialCandidate(RawDcqlCredential(
        id = id,
        format = credential.format,
        data = credential.credentialData,
        originalCredential = credential,
        disclosures = disclosures?.map { DcqlDisclosure(it.name, it.value, it.location) }
    ), resolved)
}

private val log = KotlinLogging.logger {}

/**
 * Removes structurally matching mdocs whose certified DeviceKey cannot be resolved to one current
 * Crypto2 signing key while retaining per-query failures until DCQL requirements are evaluated.
 */
private suspend fun Wallet.holderKeyEligibleMatches(
    matched: Map<String, List<DcqlMatcher.DcqlMatchResult>>,
    storedById: Map<String, StoredCredential>,
    allowedCredentialIds: Set<String>? = null,
): HolderKeyEligibleMatches {
    val bindingFailuresByQueryId = mutableMapOf<String, MutableList<HolderKeyBindingException>>()
    val eligible = matched.mapValues { (queryId, results) ->
        results.filter { result ->
            val raw = result.credential as RawDcqlCredential
            val stored = storedById[raw.id]
                ?: error("Credential '${raw.id}' disappeared while checking holder-key eligibility")
            if (allowedCredentialIds != null && stored.id !in allowedCredentialIds) {
                false
            } else if (stored.credential !is MdocsCredential) {
                true
            } else {
                try {
                    resolveHolderKey(stored, setOf(KeyUsage.SIGN))
                    true
                } catch (failure: HolderKeyBindingException) {
                    bindingFailuresByQueryId.getOrPut(queryId, ::mutableListOf) += failure
                    false
                }
            }
        }
    }
    return HolderKeyEligibleMatches(eligible, bindingFailuresByQueryId)
}
