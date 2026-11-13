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
    val candidates = metadataPaths(expression, format)
    val match = metadata.withIndex().firstOrNull { entry ->
        candidates.any { candidate -> entry.value.path.matches(candidate) }
    }
    return copy(
        label = match?.value?.displayName ?: label,
        labelSource = if (match?.value?.displayName != null) ClaimLabelSource.IssuerMetadata else labelSource,
        displayOrder = match?.index,
        value = value.withClaimMetadata(metadata, format),
    )
}

private fun DisplayValue.withClaimMetadata(metadata: List<WalletDemoCredentialClaimMetadata>, format: String): DisplayValue = when (this) {
    is DisplayValue.ObjectValue -> copy(entries = entries.map { it.withClaimMetadata(metadata, format) }.sortedBy { it.displayOrder ?: Int.MAX_VALUE })
    is DisplayValue.ListValue -> copy(values = values.map { it.withClaimMetadata(metadata, format) })
    else -> this // In particular, don't resolve deferred images during metadata normalization.
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
    val mapped = groups.map { it.copy(items = it.items.map { item -> item.withClaimMetadata(metadata, format) }) }
    if (mapped.none { group -> group.items.any { it.displayOrder != null } }) return mapped
    val readable = mapped.flatMap { group -> group.items.filter { group.id != "technical" || it.displayOrder != null } }
        .sortedBy { it.displayOrder ?: Int.MAX_VALUE }
    val technical = mapped.filter { it.id == "technical" }.map { it.copy(items = it.items.filter { item -> item.displayOrder == null }) }
        .filter { it.items.isNotEmpty() }
    return listOfNotNull(readable.takeIf { it.isNotEmpty() }?.let {
        ClaimGroup(id = "data", title = ClaimGroupKind.Other.title, items = it)
    }) + technical
}
