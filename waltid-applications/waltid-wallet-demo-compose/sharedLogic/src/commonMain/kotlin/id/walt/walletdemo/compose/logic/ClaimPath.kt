package id.walt.walletdemo.compose.logic

internal data class ClaimPath(
    val itemPath: ClaimItemPath,
    val components: List<String>,
) {
    val leaf: String = components.lastOrNull().orEmpty()
    val topLevel: String = components.firstOrNull().orEmpty()
    val isTopLevel: Boolean = components.size <= 1

    companion object {
        fun topLevel(name: String): ClaimPath =
            ClaimPath(itemPath = ClaimItemPath.topLevel(name), components = listOf(name))

        fun child(parent: ClaimPath, child: String): ClaimPath =
            ClaimPath(
                itemPath = parent.itemPath.child(child),
                components = parent.components + child,
            )

        fun indexed(parent: ClaimPath, index: Int): ClaimPath =
            ClaimPath(itemPath = parent.itemPath.indexedChild(index), components = parent.components)

        fun transactionData(index: Int, field: TransactionDataField): ClaimPath =
            ClaimPath(
                itemPath = ClaimItemPath.topLevel(ClaimPathRoot.TransactionData.id)
                    .indexedChild(index)
                    .child(field.id),
                components = ClaimPathRoot.TransactionData.componentsWith(field.id),
            )

        fun disclosure(index: Int, rawPath: String, format: String? = null): ClaimPath {
            val components = disclosurePathComponents(rawPath, format)
            val semanticLeaf = components.lastOrNull() ?: ClaimPathRoot.Disclosures.singularId
            return ClaimPath(
                itemPath = ClaimItemPath.topLevel(ClaimPathRoot.Disclosures.id)
                    .indexedChild(index)
                    .child(semanticLeaf),
                components = components,
            )
        }

        fun semanticLeaf(rawPath: String): String? =
            ClaimPathExpression.parse(rawPath).leafKey
    }
}

internal enum class ClaimPathRoot(val id: String) {
    Root("$"),
    Disclosures("disclosures"),
    TransactionData("transactionData"),
    ;

    val singularId: String
        get() = when (this) {
            Disclosures -> "disclosure"
            else -> id
        }

    fun componentsWith(child: String): List<String> =
        listOf(id, child)
            .filter { it.isNotBlank() }
            .distinct()
}

internal enum class TransactionDataField(val id: String) {
    Type("type"),
    CredentialQueryIds("credentialQueryIds"),
    Details("details"),
    Raw("raw"),
}

internal fun disclosurePathComponents(rawPath: String, format: String?): List<String> =
    disclosurePathExpression(rawPath, format).segments.mapNotNull { (it as? ClaimPathExpression.Segment.Key)?.value }

internal fun disclosurePathExpression(rawPath: String, format: String?): ClaimPathExpression =
    if (format == "mso_mdoc" && !rawPath.startsWith("[") && '/' in rawPath && "://" !in rawPath)
        ClaimPathExpression(rawPath.split('/', limit = 2).map(ClaimPathExpression.Segment::Key))
    else ClaimPathExpression.parse(rawPath)
