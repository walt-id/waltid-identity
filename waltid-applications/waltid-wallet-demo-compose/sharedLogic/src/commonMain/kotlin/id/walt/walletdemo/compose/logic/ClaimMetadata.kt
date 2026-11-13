package id.walt.walletdemo.compose.logic

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** Metadata changes labels/order only. Values, required/selectable policy and consent stay SDK-owned. */
internal fun ClaimItem.withClaimMetadata(
    metadata: List<WalletDemoCredentialClaimMetadata>,
    format: String,
    expression: ClaimPathExpression = ClaimPathExpression.parse(path.id),
): ClaimItem {
    val match = bestClaimMetadata(metadata, expression, format)
    return copy(
        label = match?.value?.displayName ?: label,
        labelSource = if (match?.value?.displayName != null) ClaimLabelSource.IssuerMetadata else labelSource,
        displayOrder = match?.index,
        value = value.withClaimMetadata(metadata, format, expression),
    )
}

internal fun bestClaimMetadata(
    metadata: List<WalletDemoCredentialClaimMetadata>, expression: ClaimPathExpression, format: String,
): IndexedValue<WalletDemoCredentialClaimMetadata>? {
    if (expression.segments.isEmpty()) return null
    val candidates = metadataPaths(expression, format)
    val match = metadata.withIndex().firstOrNull { entry ->
        candidates.any { candidate -> entry.value.path.matches(candidate) }
    }
    return match
}

private fun DisplayValue.withClaimMetadata(
    metadata: List<WalletDemoCredentialClaimMetadata>, format: String, expression: ClaimPathExpression,
): DisplayValue {
    if (expression.segments.isEmpty()) return this
    return when (this) {
        is DisplayValue.ObjectValue -> copy(entries = entries.map { it.withClaimMetadata(metadata, format) }.sortedBy { it.displayOrder ?: Int.MAX_VALUE })
        is DisplayValue.ListValue -> {
            val entryPaths = values.indices.map { index -> ClaimPathExpression(expression.segments + ClaimPathExpression.Segment.Index(index)) }
            copy(
                values = values.mapIndexed { index, value -> value.withClaimMetadata(metadata, format, entryPaths[index]) },
                labels = entryPaths.mapIndexedNotNull { index, path ->
                    bestClaimMetadata(metadata, path, format)?.value?.displayName?.let { index to it }
                }.toMap(),
            )
        }
        else -> this // Deferred images stay unresolved.
    }
}

private fun metadataPaths(expression: ClaimPathExpression, format: String): List<List<ClaimPathExpression.Segment>> {
    val path = expression.segments
    return buildList {
        add(path)
        if (format == "mso_mdoc" && path.lastOrNull() == ClaimPathExpression.Segment.Key("elementValue")) add(path.dropLast(1))
        if (format == "jwt_vc_json" && path.firstOrNull() == ClaimPathExpression.Segment.Key("vc")) add(path.drop(1))
    }
}

/** Match complete paths; a wildcard selects array elements, never an object key or its parent. */
private fun List<JsonElement>.matches(actual: List<ClaimPathExpression.Segment>): Boolean =
    size == actual.size && indices.all { index ->
        val segment = actual[index]
        when (val expected = this[index]) {
            JsonNull -> segment == ClaimPathExpression.Segment.Wildcard ||
                segment is ClaimPathExpression.Segment.Index && segment.value >= 0
            is JsonPrimitive -> if (expected.isString) {
                segment is ClaimPathExpression.Segment.Key && segment.value == expected.content
            } else {
                segment is ClaimPathExpression.Segment.Index && expected.content.toIntOrNull() == segment.value
            }
            else -> false
        }
    }

internal fun applyClaimMetadata(groups: List<ClaimGroup>, metadata: List<WalletDemoCredentialClaimMetadata>, format: String): List<ClaimGroup> {
    if (metadata.isEmpty()) return groups
    // Ordering applies only to siblings. Metadata must never move a nested leaf
    // out of its parent, merge semantic sections or expose technical fields.
    return groups.map { group ->
        group.copy(items = group.items.map { it.withClaimMetadata(metadata, format) }
            .sortedBy { it.displayOrder ?: Int.MAX_VALUE })
    }
}
