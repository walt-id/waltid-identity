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

        fun disclosure(index: Int, rawPath: String, label: String): ClaimPath {
            val semanticLeaf = semanticLeaf(rawPath)
                ?: label.takeIf { it.isNotBlank() }
                ?: ClaimPathRoot.Disclosures.singularId
            return ClaimPath(
                itemPath = ClaimItemPath.topLevel(ClaimPathRoot.Disclosures.id)
                    .indexedChild(index)
                    .child(semanticLeaf),
                components = ClaimPathRoot.Disclosures.componentsWith(semanticLeaf),
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
