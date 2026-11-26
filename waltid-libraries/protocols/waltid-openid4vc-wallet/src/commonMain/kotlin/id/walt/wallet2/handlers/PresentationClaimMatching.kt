@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.handlers

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.dcql.DcqlDisclosure
import id.walt.dcql.DcqlMatcher
import id.walt.dcql.RawDcqlCredential
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialQuery
import id.walt.wallet2.consent.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

internal fun DcqlMatcher.DcqlMatchResult.selectDisclosures(
    selectedPaths: Set<String>,
): DcqlMatcher.DcqlMatchResult {
    val plan = originalQuery.claimSelectionPlan()
    val claims = availablePresentationClaims(plan) ?: run {
        require(selectedPaths.isEmpty()) {
            "Selected disclosure option does not match the presentation preview"
        }
        return this
    }
    val selectivelyDisclosablePaths = claims
        .filterValues { claim -> claim.selectivelyDisclosable }
        .keys
    val unknownPaths = selectedPaths - selectivelyDisclosablePaths
    require(unknownPaths.isEmpty()) {
        "Selected disclosure option does not match a selectively disclosable presentation claim"
    }
    val retainedPaths = plan.requiredPaths + selectedPaths
    val retainedClaimPaths = retainedPaths + claims
        .filterValues { claim -> !claim.selectivelyDisclosable }
        .keys
    require(plan.satisfiedBy(retainedClaimPaths)) {
        "Selected disclosure option(s) do not satisfy required presentation claim constraints"
    }

    val retainedClaims = claims.filter { (path, claim) -> !claim.selectivelyDisclosable || path in retainedPaths }
    return copy(
        selectedDisclosures = retainedClaims.mapValues { (_, claim) -> claim.value },
        disclosuresByPath = retainedClaims.mapValues { (_, claim) -> claim.disclosures },
    )
}

internal fun DcqlMatcher.DcqlMatchResult.toPresentationDisclosures(): List<PresentationDisclosure> {
    val plan = originalQuery.claimSelectionPlan()
    val claims = availablePresentationClaims(plan).orEmpty()
    // Disclosures presented whatever the user selects; an optional claim needing only these cannot be withheld
    val requiredDisclosures = claims.filterKeys(plan::isRequired).values.flatMapTo(mutableSetOf()) { it.disclosures }
    val requested = claims.map { (path, claim) ->
        val required = plan.isRequired(path)
        val value = claim.value
        when {
            claim.selectivelyDisclosable -> {
                val presentedAnyway = required || claim.disclosures.all { it in requiredDisclosures }
                PresentationDisclosure(
                    path = path,
                    name = (value as? DcqlDisclosure)?.name ?: claimName(path) ?: path.substringAfterLast('.', path),
                    value = disclosedValue(path, claim),
                    selectivelyDisclosable = true,
                    required = presentedAnyway,
                    selectable = plan.isSelectable(path, claim) && !presentedAnyway,
                )
            }
            // Clear text, but its own children may be disclosures that are not presented
            value is JsonElement -> PresentationDisclosure(
                path = path,
                name = claimName(path) ?: path.substringAfterLast('.', path),
                value = disclosedValue(path, claim),
                selectivelyDisclosable = false,
                required = required,
                selectable = false,
            )
            else -> PresentationDisclosure(
                path = path,
                name = path.substringAfterLast('.', path),
                value = (value as? DcqlDisclosure)?.value ?: JsonPrimitive(value.toString()),
                selectivelyDisclosable = false,
                required = required,
                selectable = false,
            )
        }
    }
    // Consent must include clear-text claims carried by the signed credential even when the
    // verifier did not ask for them. This is preview-only: selection and payload generation
    // remain owned by the matcher/presenter. An mdoc sends selected elements, not its full data.
    val included = alwaysIncludedPresentationClaims()
    return requested + included.filterNot { claim ->
        requested.any { it.path == claim.path || claim.path.startsWith("${it.path}.") }
    }
}

private fun DcqlMatcher.DcqlMatchResult.alwaysIncludedPresentationClaims(): List<PresentationDisclosure> {
    if (credential.format == "mso_mdoc") return emptyList()
    val original = (credential as? RawDcqlCredential)?.originalCredential as? DigitalCredential
    val selective = original as? SelectivelyDisclosableVerifiableCredential
    // Only the original signed payload proves which SD-JWT values are unconditionally visible.
    val payload = if (!credential.disclosures.isNullOrEmpty()) selective?.originalCredentialData
        ?: return emptyList() else selective?.originalCredentialData ?: credential.data
    val root = if (originalQuery.claims.orEmpty().any { it.path.firstOrNull() == JsonPrimitive("$") }) listOf("$") else emptyList()
    val claims = mutableListOf<PresentationDisclosure>()
    fun visible(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.filterKeys { it !in setOf("_sd", "_sd_alg", "...") }.mapValues { visible(it.value) })
        is JsonArray -> JsonArray(value.filterNot { it is JsonObject && "..." in it }.map(::visible))
        else -> value
    }
    fun collect(value: JsonElement, path: List<String>, name: String) {
        when (value) {
            is JsonObject -> value.forEach { (key, child) ->
                if (key !in setOf("_sd", "_sd_alg", "...")) collect(child, path + key, key)
            }
            else -> claims += PresentationDisclosure((root + path).joinToString(".") { JsonPrimitive(it).toString() }, name, visible(value),
                selectivelyDisclosable = false, required = false, selectable = false, requested = false)
        }
    }
    collect(payload, emptyList(), "")
    return claims
}

/**
 * The last claim name in the requested path, e.g. `nationalities` for `["nationalities", 1]`.
 * Array elements have no name of their own, so rows for them are labelled after their array.
 */
private fun DcqlMatcher.DcqlMatchResult.claimName(path: String): String? =
    originalQuery.claims.orEmpty().firstOrNull { it.pathKey() == path }?.path
        ?.lastOrNull { segment -> segment is JsonPrimitive && segment.isString }
        ?.jsonPrimitive?.content

/** A requested claim with the disclosures presenting it; none when it is visible without disclosures. */
private class PresentationClaim(val value: Any, val disclosures: List<DcqlDisclosure>) {
    val selectivelyDisclosable: Boolean get() = disclosures.isNotEmpty()
}

private fun DcqlMatcher.DcqlMatchResult.availablePresentationClaims(
    plan: ClaimSelectionPlan,
): Map<String, PresentationClaim>? {
    val selected = selectedDisclosures ?: return null
    fun claim(path: String, value: Any) = PresentationClaim(value, disclosuresByPath[path] ?: listOfNotNull(value as? DcqlDisclosure))
    if (plan.optionalPaths.isEmpty()) return selected.mapValues { (path, value) -> claim(path, value) }

    val expanded = linkedMapOf<String, PresentationClaim>()
    val selectedByPath = selected.toMutableMap()
    originalQuery.claims.orEmpty().forEach { claim ->
        val path = claim.pathKey()
        val selectedValue = selectedByPath.remove(path)
        when {
            selectedValue != null -> expanded[path] = claim(path, selectedValue)
            path in plan.optionalPaths -> matchOptionalClaim(claim)?.let { expanded[path] = it }
        }
    }
    selectedByPath.forEach { (path, value) -> expanded[path] = claim(path, value) }
    return expanded
}

/** An optional claim outside the claim set the matcher chose, offered only if it can be withheld. */
private fun DcqlMatcher.DcqlMatchResult.matchOptionalClaim(claim: ClaimsQuery): PresentationClaim? {
    DcqlMatcher.matchClaim(credential, claim).getOrNull()
        ?.takeIf { match -> match.disclosures.isNotEmpty() }
        ?.let { match -> return PresentationClaim(match.value ?: JsonArray(emptyList()), match.disclosures) }

    // Disclosures without a location can only be found by claim name
    val claimName = claim.path
        .lastOrNull { pathPart -> pathPart is JsonPrimitive && pathPart.isString }
        ?.jsonPrimitive?.content
        ?: return null
    val allowedValues = claim.values.orEmpty()
    return credential.disclosures?.firstOrNull { disclosure ->
        disclosure.location == null && disclosure.name == claimName &&
            (allowedValues.isEmpty() || disclosure.value in allowedValues)
    }?.let { PresentationClaim(it, listOf(it)) }
}

/**
 * What the verifier sees at [path] once [claim]'s disclosures are presented: claims and array elements
 * with a disclosure of their own that is not presented are left out, so consent matches the presentation.
 */
private fun DcqlMatcher.DcqlMatchResult.disclosedValue(path: String, claim: PresentationClaim): JsonElement {
    val fallback = (claim.value as? DcqlDisclosure)?.value ?: claim.value as? JsonElement ?: JsonPrimitive(claim.value.toString())
    val claimPath = originalQuery.claims.orEmpty().firstOrNull { it.pathKey() == path }?.path ?: return fallback
    val presentedLocations = claim.disclosures.map { it.location ?: return fallback }.toSet()
    val withheldLocations = credential.disclosures.orEmpty().mapNotNull { it.location }.filterNot { it in presentedLocations }.toSet()

    fun isWithheld(location: List<JsonElement>) =
        withheldLocations.any { it.size <= location.size && it == location.subList(0, it.size) }

    // Only the selected claim's own subtree is walked; a leaf is returned as is
    fun withoutWithheld(element: JsonElement, location: List<JsonElement>): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapNotNull { (key, value) ->
            val child = location + JsonPrimitive(key)
            if (child in withheldLocations) null else key to withoutWithheld(value, child)
        }.toMap())
        is JsonArray -> JsonArray(element.mapIndexedNotNull { index, value ->
            val child = location + JsonPrimitive(index)
            if (child in withheldLocations) null else withoutWithheld(value, child)
        })
        else -> element
    }

    val root = (credential.data["vc"] as? JsonObject) ?: credential.data
    val selectedValues = DcqlMatcher.processClaimsPath(root, claimPath).getOrElse { return fallback }
        .mapNotNull { (location, value) ->
            when {
                withheldLocations.isEmpty() -> value
                isWithheld(location) -> null
                else -> withoutWithheld(value, location)
            }
        }
    return selectedValues.singleOrNull()?.takeIf { claimPath.lastOrNull() !is JsonNull } ?: JsonArray(selectedValues)
}

private data class ClaimSelectionPlan(
    val requiredPaths: Set<String>,
    val optionalPaths: Set<String>,
    private val allClaimPaths: Set<String>,
    private val claimSetOptions: List<Set<String>>?,
) {
    fun isRequired(path: String): Boolean = path in requiredPaths

    fun isSelectable(path: String, claim: PresentationClaim): Boolean = path in optionalPaths && claim.selectivelyDisclosable

    fun satisfiedBy(selectedPathKeys: Set<String>): Boolean =
        claimSetOptions
            ?.any { option -> option.isNotEmpty() && option.all { path -> path in selectedPathKeys } }
            ?: allClaimPaths.all { path -> path in selectedPathKeys }
}

private fun CredentialQuery.claimSelectionPlan(): ClaimSelectionPlan {
    val claims = claims.orEmpty()
    val allClaimPaths = claims.mapTo(linkedSetOf()) { it.pathKey() }
    val claimSets = claimSets
    if (claimSets.isNullOrEmpty()) {
        return ClaimSelectionPlan(
            requiredPaths = allClaimPaths,
            optionalPaths = emptySet(),
            allClaimPaths = allClaimPaths,
            claimSetOptions = null,
        )
    }

    val pathByClaimId = claims
        .mapNotNull { claim -> claim.id?.let { id -> id to claim.pathKey() } }
        .toMap()
    val requiredClaimIds = claimSets
        .map { ids -> ids.toSet() }
        .reduceOrNull { required, option -> required intersect option }
        .orEmpty()
    val claimIdsInAnySet = claimSets.flatten().toSet()

    return ClaimSelectionPlan(
        requiredPaths = requiredClaimIds.mapNotNullTo(linkedSetOf()) { id -> pathByClaimId[id] },
        optionalPaths = (claimIdsInAnySet - requiredClaimIds).mapNotNullTo(linkedSetOf()) { id -> pathByClaimId[id] },
        allClaimPaths = allClaimPaths,
        claimSetOptions = claimSets.mapNotNull { optionIds ->
            optionIds
                .mapNotNullTo(linkedSetOf()) { id -> pathByClaimId[id] }
                .takeIf { paths -> paths.size == optionIds.size }
        },
    )
}

private fun ClaimsQuery.pathKey(): String = path.joinToString(".")
