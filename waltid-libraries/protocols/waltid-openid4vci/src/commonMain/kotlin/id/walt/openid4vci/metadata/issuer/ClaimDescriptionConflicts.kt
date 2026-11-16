package id.walt.openid4vci.metadata.issuer

import id.walt.openid4vci.CredentialFormat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Claims description objects that cannot be processed together (OpenID4VCI 1.0 Appendix B.3), or a path that the
 * credential format does not allow (Appendix C). [paths] holds the paths involved, in metadata order.
 */
data class ClaimDescriptionConflict(val paths: List<List<JsonElement>>, val reason: String) {
    val message: String get() = "$reason: ${paths.joinToString(", ") { JsonArray(it).toString() }}"
}

/**
 * The conflicts among this configuration's claim descriptions; empty when they can be processed.
 *
 * Not checked on construction: metadata from other issuers must still parse, so each consumer decides what aborting
 * means for it.
 */
fun CredentialConfiguration.claimDescriptionConflicts(): List<ClaimDescriptionConflict> =
    credentialMetadata?.claims.orEmpty().conflicts(mdoc = format == CredentialFormat.MSO_MDOC)

/**
 * The claim descriptions a wallet may process: all of them, or none when any conflict, since Appendix B.3 says
 * processing must then be aborted. [onConflicts] receives the conflicts, for example to log them.
 */
fun CredentialConfiguration.claimDescriptionsToProcess(
    onConflicts: (List<ClaimDescriptionConflict>) -> Unit = {},
): List<ClaimDescription> {
    val conflicts = claimDescriptionConflicts()
    if (conflicts.isEmpty()) return credentialMetadata?.claims.orEmpty()
    onConflicts(conflicts)
    return emptyList()
}

/**
 * Paths are merged into a tree, so two paths share nodes up to their first differing segment and part there into
 * sibling children. Siblings must describe one kind of value: an object (string keys) or an array (indexes or the
 * wildcard, but not both). An mdoc map may also take integer keys, so only the wildcard excludes everything else.
 */
private fun List<ClaimDescription>.conflicts(mdoc: Boolean): List<ClaimDescriptionConflict> {
    val conflicts = mutableListOf<ClaimDescriptionConflict>()
    val root = ClaimPathNode()
    for (description in this) {
        val path = description.path
        val invalid = invalidPathReason(path, mdoc)
        if (invalid != null) {
            conflicts += ClaimDescriptionConflict(listOf(path), invalid)
            continue
        }
        val node = path.fold(root) { node, segment -> node.child(segment, path) }
        val described = node.describedBy
        if (described == null) node.describedBy = path
        else conflicts += ClaimDescriptionConflict(listOf(described, path), "the same claim is described more than once")
    }
    root.collectSiblingConflicts(mdoc, conflicts)
    return conflicts
}

private class ClaimPathNode {
    val children = LinkedHashMap<JsonElement, ClaimPathNode>()

    /** The first path through each child, to name the claims in a conflict. */
    val firstPathThrough = LinkedHashMap<JsonElement, List<JsonElement>>()
    var describedBy: List<JsonElement>? = null

    fun child(segment: JsonElement, path: List<JsonElement>): ClaimPathNode {
        firstPathThrough.getOrPut(segment) { path }
        return children.getOrPut(segment) { ClaimPathNode() }
    }

    fun collectSiblingConflicts(mdoc: Boolean, into: MutableList<ClaimDescriptionConflict>) {
        val byKind = children.keys.groupBy { it.segmentKind() }
        val consistent = byKind.size <= 1 || (mdoc && SegmentKind.WILDCARD !in byKind)
        if (!consistent) {
            val reason = if (byKind.keys == setOf(SegmentKind.WILDCARD, SegmentKind.INDEX)) {
                "a null wildcard and an index address the same array"
            } else {
                "the same claim is addressed both as an array and as ${if (mdoc) "a map" else "an object"}"
            }
            into += ClaimDescriptionConflict(byKind.values.map { firstPathThrough.getValue(it.first()) }, reason)
        }
        children.values.forEach { it.collectSiblingConflicts(mdoc, into) }
    }
}

private enum class SegmentKind { KEY, INDEX, WILDCARD }

/** Negative integers only occur as mdoc map keys, so they count as keys. */
private fun JsonElement.segmentKind(): SegmentKind = when {
    this == JsonNull -> SegmentKind.WILDCARD
    (this as JsonPrimitive).isString || content.startsWith("-") -> SegmentKind.KEY
    else -> SegmentKind.INDEX
}

private fun invalidPathReason(path: List<JsonElement>, mdoc: Boolean): String? = when {
    mdoc && (path.size < 2 || path.take(2).any { it !is JsonPrimitive || !it.isString }) ->
        "mdoc claim paths start with a namespace and a data element identifier"
    !mdoc && path.any { it is JsonPrimitive && !it.isString && it.content.startsWith("-") } ->
        "negative integers are not used in paths of JSON-based credentials"
    else -> null
}
