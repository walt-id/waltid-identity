package id.walt.walletdemo.compose.logic

import kotlinx.serialization.json.*

/** A tree of effective data, never a reconstruction from all stored credential values. */
data class SharingClaimNode(
    val path: ClaimItemPath,
    val label: String,
    val kind: Kind,
    val value: DisplayValue?,
    val children: List<SharingClaimNode>,
    val reason: String?,
    val additional: Boolean,
    val technical: Boolean,
    val initiallyExpanded: Boolean,
    val choice: SharingClaimChoice? = null,
    val contextOnly: Boolean = false,
    val alwaysShared: Boolean = false,
    val arrayUsesCards: Boolean = children.any { it.kind != Kind.Scalar },
) {
    val sourceArrayIndex: Int?
        get() = (ClaimPathExpression.parse(path.id).segments.lastOrNull() as? ClaimPathExpression.Segment.Index)?.value

    enum class Kind { Scalar, Object, Array }
}
data class SharingClaimContribution(
    val id: String,
    val nodes: List<SharingClaimNode>,
    val wholeCredential: Boolean,
    val queries: Set<String> = setOf(id),
    val separatesAlwaysShared: Boolean = false,
)

/** Partition actual values, retaining every ancestor and its original typed identity in each section. */
fun List<SharingClaimNode>.sharingSection(alwaysShared: Boolean): List<SharingClaimNode> = mapNotNull { node ->
    // Offered values remain visibly Not shared under their one SDK control, including their typed children.
    if (!alwaysShared && node.contextOnly && node.choice != null) return@mapNotNull node
    val children = node.children.sharingSection(alwaysShared)
    val belongs = !node.contextOnly && node.alwaysShared == alwaysShared
    val choice = node.choice.takeUnless { alwaysShared || node.children.isEmpty() && node.alwaysShared && !node.contextOnly }
    if (node.children.isEmpty()) {
        if (belongs || choice != null) node.copy(reason = node.reason.takeUnless { alwaysShared }, choice = choice) else null
    } else if (children.isNotEmpty() || choice != null) {
        node.copy(children = children, reason = node.reason.takeUnless { alwaysShared || !belongs }, choice = choice,
            contextOnly = node.contextOnly || !belongs || children.size != node.children.size)
    } else null
}
data class SharingClaimChoice(
    val selection: WalletDemoPresentationDisclosureSelection,
    val item: ClaimItem,
    val selected: Boolean,
    val included: Boolean,
    val forcedReason: String?,
)
data class EffectiveSharingGroup(
    val credentialId: String,
    val summary: CredentialCardDisplayData,
    val contributions: List<SharingClaimContribution>,
    val choices: List<SharingClaimChoice>,
)

fun WalletDemoPreparedClaimReview.toEffectiveSharingInformation(
    options: List<WalletDemoPresentationCredentialOption>,
    selectedDisclosures: Set<WalletDemoPresentationDisclosureSelection>,
): List<EffectiveSharingGroup> {
    val optionById = options.associateBy { it.selection }
    val information = options.associate { it.selection to it.toSharingInformation() }
    return credentials.groupBy { it.credentialId }.map { (credentialId, contributions) ->
        val selectedOptions = contributions.map { contribution ->
            requireNotNull(optionById[WalletDemoPresentationCredentialSelection(contribution.queryId, credentialId)]) {
                "Claim review refers to an unavailable credential option"
            }
        }
        fun controls(contribution: WalletDemoCredentialClaims, location: WalletDemoClaimLocation) = location.sharedBy.map { path ->
            WalletDemoPresentationDisclosureSelection(contribution.queryId, credentialId, path)
        }
        fun label(selection: WalletDemoPresentationDisclosureSelection): String {
            val optionId = WalletDemoPresentationCredentialSelection(selection.queryId, selection.credentialId)
            val option = requireNotNull(optionById[optionId])
            return bestClaimMetadata(StoredCredentialMetadataParser.claims(option.metadataJson, format = option.format),
                reviewedDisclosurePathExpression(selection.path, option.format), option.format)?.value?.displayName
                ?: information[optionId]?.fields?.firstOrNull { selection in it.selections }?.item?.label ?: "selected information"
        }
        val choices = selectedOptions.flatMap { option ->
            information.getValue(option.selection).fields.filter { it.optionalSelections.isNotEmpty() }.map { field ->
                val selection = field.optionalSelections.single()
                val pattern = reviewedDisclosurePathExpression(selection.path, option.format)
                val matches = contributions.flatMap { contribution -> contribution.locations.filter { location ->
                    pattern.matches(concreteClaimPath(location.sourcePathJson))
                }.map { contribution to it } }
                // A wildcard is one SDK choice, possibly with only some values already carried.
                // Keep that choice available rather than invent per-element controls or assume all are forced.
                val forced = if (pattern.segments.any { it is ClaimPathExpression.Segment.Wildcard }) null else
                    matches.firstNotNullOfOrNull { (contribution, location) ->
                        when {
                            location.alwaysCarried -> "Always included by this credential"
                            else -> controls(contribution, location).firstOrNull { cause ->
                                cause != selection && (selection !in selectedDisclosures ||
                                    optionById[WalletDemoPresentationCredentialSelection(cause.queryId, cause.credentialId)]
                                        ?.disclosures?.any { it.path == cause.path && (it.required || !it.selectivelyDisclosable) } == true)
                            }?.let {
                                if (it.queryId != selection.queryId) "Included by another selected requirement"
                                else "Included with ${label(it)}"
                            }
                        }
                    }
                SharingClaimChoice(selection, field.item.copy(label = label(selection)), selection in selectedDisclosures, matches.isNotEmpty(), forced)
            }
        }
        val rendered = contributions.map { contribution ->
            val option = optionById.getValue(WalletDemoPresentationCredentialSelection(contribution.queryId, credentialId))
            val metadata = StoredCredentialMetadataParser.claims(option.metadataJson, format = option.format)
            val locations = contribution.locations.associateBy { concreteClaimPath(it.receivedPathJson) }
            require(locations.size == contribution.locations.size) { "Claim review contains duplicate received paths" }
            require(ClaimPathExpression(emptyList()) in locations) { "Claim review has no root identity" }
            val requested = option.disclosures.filter { it.requested }.map { reviewedDisclosurePathExpression(it.path, option.format) }
            fun node(value: JsonElement, received: List<JsonElement>, fallbackLabel: String): SharingClaimNode {
                val location = requireNotNull(locations[concreteClaimPath(JsonArray(received).toString())]) {
                    "A presented value has no original claim identity"
                }
                val source = concreteClaimPath(location.sourcePathJson)
                val path = source.claimPath()
                val match = bestClaimMetadata(metadata, source, option.format)
                val title = match?.value?.displayName ?: fallbackLabel
                val causes = controls(contribution, location)
                val exact = option.disclosures.filter { it.requested && reviewedDisclosurePathExpression(it.path, option.format).matches(source) }
                val own = exact.firstOrNull { WalletDemoPresentationDisclosureSelection(option.queryId, credentialId, it.path) in causes }
                val coveredByRequestedParent = causes.any { cause ->
                    val parent = reviewedDisclosurePathExpression(cause.path, option.format)
                    parent.segments.size < source.segments.size &&
                        parent.matches(ClaimPathExpression(source.segments.take(parent.segments.size)))
                }
                val additional = exact.isEmpty() && !coveredByRequestedParent
                val reason = when {
                    own?.required == true -> "Required"
                    coveredByRequestedParent -> null
                    location.alwaysCarried && exact.any { it.required } -> "Required"
                    location.alwaysCarried -> "Always included by this credential"
                    own != null && causes.all { it.path == own.path } -> "Included"
                    causes.isNotEmpty() -> "Shared with " + causes.map(::label).distinct().joinToString()
                    else -> "Also shared with selected information"
                }
                val decoded = value.reviewDisplayValue(path, option.format)
                val kind = value.reviewKind(decoded)
                val children = when (kind) {
                    SharingClaimNode.Kind.Object -> (value as JsonObject).entries.map { (key, child) ->
                        node(child, received + JsonPrimitive(key), CredentialDisplayVocabulary.humanizedClaimLabel(key))
                    }.sortedBy { child ->
                        bestClaimMetadata(metadata, ClaimPathExpression.parse(child.path.id), option.format)?.index ?: Int.MAX_VALUE
                    }
                    SharingClaimNode.Kind.Array -> (value as JsonArray).mapIndexed { index, child -> node(child, received + JsonPrimitive(index), "") }
                    SharingClaimNode.Kind.Scalar -> emptyList()
                }
                return SharingClaimNode(path.itemPath, title, kind, decoded, children,
                    reason = if (kind != SharingClaimNode.Kind.Scalar && exact.isEmpty()) null else reason,
                    additional = additional, technical = CredentialDisplayVocabulary.groupKind(path, option.format) == ClaimGroupKind.Technical,
                    initiallyExpanded = requested.any { source.isAncestorOf(it) } || causes.isNotEmpty(),
                    alwaysShared = location.alwaysCarried)
            }
            val root = Json.parseToJsonElement(contribution.claimsJson).jsonObject
            val nodes = root.entries.mapNotNull { (key, value) ->
                val location = requireNotNull(locations[concreteClaimPath(JsonArray(listOf(JsonPrimitive(key))).toString())])
                if (isCredentialSummaryClaim(concreteClaimPath(location.sourcePathJson).segments, option.format)) null
                else node(value, listOf(JsonPrimitive(key)), CredentialDisplayVocabulary.humanizedClaimLabel(key))
            }
            SharingClaimContribution(contribution.queryId, nodes, contribution.wholeCredential,
                separatesAlwaysShared = option.format in setOf("dc+sd-jwt", "vc+sd-jwt"))
        }
        // Equal contributions may share a view only when both values and original identities agree.
        // Different partial arrays remain separate; received index zero is never a deduplication key.
        val collidingChoices = choices.groupBy { choice ->
            val option = optionById.getValue(WalletDemoPresentationCredentialSelection(choice.selection.queryId, credentialId))
            reviewedDisclosurePathExpression(choice.selection.path, option.format)
        }.values.any { it.size > 1 }
        val merged = if (collidingChoices) rendered else rendered.zip(contributions).groupBy { (_, raw) ->
            Json.parseToJsonElement(raw.claimsJson) to raw.locations.associate {
                concreteClaimPath(it.receivedPathJson) to concreteClaimPath(it.sourcePathJson)
            }
        }.values.map { matching ->
            val views = matching.map { it.first }
            views.first().copy(nodes = mergeEqualClaimTrees(views.map { it.nodes }), queries = views.flatMap { it.queries }.toSet())
        }
        val decorated = merged.map { contribution ->
            val ownChoices = choices.filter { it.selection.queryId in contribution.queries }
            var nodes = contribution.nodes
            ownChoices.forEach { choice ->
                val option = optionById.getValue(WalletDemoPresentationCredentialSelection(choice.selection.queryId, credentialId))
                val expression = reviewedDisclosurePathExpression(choice.selection.path, option.format)
                if (expression.segments.isNotEmpty() && expression.segments.none { it is ClaimPathExpression.Segment.Wildcard }) {
                    // Keep an off choice in its original context without inventing a transmitted parent.
                    // A choice forced only by another query stays beside that query's contribution.
                    if (choice.forcedReason == null || nodes.hasClaimPath(expression.claimPath().itemPath)) {
                        nodes = nodes.withChoice(expression, choice,
                            StoredCredentialMetadataParser.claims(option.metadataJson, format = option.format), option.format)
                    }
                }
            }
            contribution.copy(nodes = nodes)
        }
        EffectiveSharingGroup(credentialId, information.getValue(selectedOptions.first().selection).summary, decorated, choices)
    }
}

private fun ClaimPathExpression.matches(actual: ClaimPathExpression): Boolean =
    segments.size == actual.segments.size && segments.indices.all { i ->
        segments[i] == actual.segments[i] || segments[i] is ClaimPathExpression.Segment.Wildcard && actual.segments[i] is ClaimPathExpression.Segment.Index
    }
private fun ClaimPathExpression.isAncestorOf(other: ClaimPathExpression): Boolean = segments.size <= other.segments.size &&
    segments.indices.all { i -> segments[i] == other.segments[i] || other.segments[i] is ClaimPathExpression.Segment.Wildcard && segments[i] is ClaimPathExpression.Segment.Index }
private fun ClaimPathExpression.claimPath(): ClaimPath {
    var item = ClaimItemPath.root()
    segments.forEachIndexed { index, segment -> item = when (segment) {
        is ClaimPathExpression.Segment.Key -> if (index == 0) ClaimItemPath.topLevel(segment.value) else item.child(segment.value)
        is ClaimPathExpression.Segment.Index -> item.indexedChild(segment.value)
        ClaimPathExpression.Segment.Wildcard -> error("Effective source paths must be concrete")
    } }
    return ClaimPath(item, segments.mapNotNull { (it as? ClaimPathExpression.Segment.Key)?.value })
}

private fun reviewedDisclosurePathExpression(path: String, format: String): ClaimPathExpression =
    if (format == "mso_mdoc") disclosurePathExpression(path, format) else ClaimPathExpression.parseSdkDisclosure(path)

private fun concreteClaimPath(raw: String): ClaimPathExpression {
    val array = Json.parseToJsonElement(raw) as? JsonArray ?: error("An effective claim path must be a JSON array")
    return ClaimPathExpression(array.map { segment ->
        val primitive = segment as? JsonPrimitive ?: error("An effective claim path must be concrete")
        if (primitive.isString) ClaimPathExpression.Segment.Key(primitive.content)
        else ClaimPathExpression.Segment.Index(requireNotNull(primitive.intOrNull?.takeIf { it >= 0 }) {
            "An effective array index must be a non-negative integer"
        })
    })
}

private fun List<SharingClaimNode>.hasClaimPath(path: ClaimItemPath): Boolean = any { it.path == path || it.children.hasClaimPath(path) }

private fun List<SharingClaimNode>.withChoice(
    expression: ClaimPathExpression,
    choice: SharingClaimChoice,
    metadata: List<WalletDemoCredentialClaimMetadata>,
    format: String,
    depth: Int = 0,
): List<SharingClaimNode> {
    val prefix = ClaimPathExpression(expression.segments.take(depth + 1))
    val path = prefix.claimPath()
    val existing = firstOrNull { it.path == path.itemPath }
    val leaf = depth == expression.segments.lastIndex
    val node = if (leaf) {
        existing?.copy(choice = choice) ?: run {
            val offered = choice.item.rawValue?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
            offered?.offeredClaimNode(prefix, choice.item.label, metadata, format)?.copy(choice = choice)
                ?: SharingClaimNode(path.itemPath, choice.item.label, SharingClaimNode.Kind.Scalar,
                    choice.item.value, emptyList(), null, false,
                    CredentialDisplayVocabulary.groupKind(path, format) == ClaimGroupKind.Technical, true, choice, contextOnly = true)
        }
    } else {
        val kind = if (expression.segments[depth + 1] is ClaimPathExpression.Segment.Index) SharingClaimNode.Kind.Array else SharingClaimNode.Kind.Object
        val label = bestClaimMetadata(metadata, prefix, format)?.value?.displayName
            ?: (expression.segments[depth] as? ClaimPathExpression.Segment.Key)?.value?.let(CredentialDisplayVocabulary::humanizedClaimLabel).orEmpty()
        val parent = if (existing?.contextOnly == true && existing.kind == SharingClaimNode.Kind.Scalar)
            existing.copy(kind = kind, value = null) else existing ?: SharingClaimNode(path.itemPath, label, kind, null, emptyList(), null, false,
                CredentialDisplayVocabulary.groupKind(path, format) == ClaimGroupKind.Technical, true, contextOnly = true)
        require(parent.kind == kind) { "Offered claim context conflicts with the effective claim tree" }
        val children = parent.children.withChoice(expression, choice, metadata, format, depth + 1)
        parent.copy(children = if (kind == SharingClaimNode.Kind.Array) children.sortedBy {
            (ClaimPathExpression.parse(it.path.id).segments.lastOrNull() as? ClaimPathExpression.Segment.Index)?.value ?: Int.MAX_VALUE
        } else children.sortedBy {
            bestClaimMetadata(metadata, ClaimPathExpression.parse(it.path.id), format)?.index ?: Int.MAX_VALUE
        })
    }
    return if (existing == null) this + node else map { if (it.path == existing.path) node else it }
}

/** Merge annotations only after the effective values and both typed path maps were proven equal. */
private fun mergeEqualClaimTrees(trees: List<List<SharingClaimNode>>): List<SharingClaimNode> = trees.first().map { node ->
    val same = trees.map { siblings -> requireNotNull(siblings.singleOrNull { it.path == node.path }) }
    val reasons = same.mapNotNull { it.reason }.distinct()
    node.copy(children = if (node.children.isEmpty()) emptyList() else mergeEqualClaimTrees(same.map { it.children }),
        reason = if ("Required" in reasons) "Required" else reasons.takeIf { it.isNotEmpty() }?.joinToString("; "),
        additional = same.all { it.additional }, initiallyExpanded = same.any { it.initiallyExpanded },
        alwaysShared = same.all { it.alwaysShared })
}

private fun JsonElement.reviewDisplayValue(path: ClaimPath, format: String): DisplayValue? =
    if (this !is JsonObject && (this !is JsonArray || CredentialDisplayVocabulary.hasRole(path, ClaimRole.Image)))
        CredentialDisplayNormalizer.toDisclosureValue(toString(), null, path, format) else null

private fun JsonElement.reviewKind(decoded: DisplayValue?): SharingClaimNode.Kind = when {
    this is JsonObject -> SharingClaimNode.Kind.Object
    this is JsonArray && (decoded == null || decoded is DisplayValue.ListValue) -> SharingClaimNode.Kind.Array
    else -> SharingClaimNode.Kind.Scalar
}

/** Holder-known candidate values: never mix these with the SDK's effective transmitted data. */
private fun JsonElement.offeredClaimNode(
    expression: ClaimPathExpression,
    fallbackLabel: String,
    metadata: List<WalletDemoCredentialClaimMetadata>,
    format: String,
): SharingClaimNode {
    val path = expression.claimPath()
    val decoded = reviewDisplayValue(path, format)
    val kind = reviewKind(decoded)
    val children = when (kind) {
        SharingClaimNode.Kind.Object -> (this as JsonObject).entries.map { (key, value) ->
            value.offeredClaimNode(ClaimPathExpression(expression.segments + ClaimPathExpression.Segment.Key(key)),
                CredentialDisplayVocabulary.humanizedClaimLabel(key), metadata, format)
        }.sortedBy { bestClaimMetadata(metadata, ClaimPathExpression.parse(it.path.id), format)?.index ?: Int.MAX_VALUE }
        SharingClaimNode.Kind.Array -> (this as JsonArray).mapIndexed { index, value ->
            value.offeredClaimNode(ClaimPathExpression(expression.segments + ClaimPathExpression.Segment.Index(index)), "", metadata, format)
        }
        SharingClaimNode.Kind.Scalar -> emptyList()
    }
    return SharingClaimNode(path.itemPath, bestClaimMetadata(metadata, expression, format)?.value?.displayName ?: fallbackLabel,
        kind, decoded, children, null, false,
        CredentialDisplayVocabulary.groupKind(path, format) == ClaimGroupKind.Technical, true, contextOnly = true)
}
