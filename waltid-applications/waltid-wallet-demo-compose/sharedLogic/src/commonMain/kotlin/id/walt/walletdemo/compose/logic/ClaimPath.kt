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

        fun disclosure(index: Int, rawPath: String, label: String): ClaimPath {
            val sourcePath = ClaimSourcePath.parse(rawPath)
            val semanticLeaf = sourcePath?.semanticLeaf
                ?: label.takeIf { it.isNotBlank() }
                ?: ClaimPathRoot.Disclosures.singularId
            return ClaimPath(
                itemPath = ClaimItemPath.disclosure(index = index, semanticLeaf = semanticLeaf, sourcePath = sourcePath),
                components = ClaimPathRoot.Disclosures.componentsWith(semanticLeaf),
            )
        }

        fun semanticLeaf(rawPath: String): String? = semanticClaimName(rawPath)
    }
}

internal class ClaimSourcePath private constructor(
    val value: String,
    val semanticLeaf: String?,
) {
    companion object {
        fun parse(rawPath: String): ClaimSourcePath? {
            val value = rawPath.trim().takeIf { it.isNotBlank() } ?: return null
            return ClaimSourcePath(
                value = value,
                semanticLeaf = ClaimPathExpression.parse(value).leafKey,
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClaimSourcePath) return false
        return value == other.value && semanticLeaf == other.semanticLeaf
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + (semanticLeaf?.hashCode() ?: 0)
        return result
    }
}

private fun semanticClaimName(rawPath: String): String? =
    ClaimSourcePath.parse(rawPath)?.semanticLeaf

internal enum class ClaimPathRoot(val id: String) {
    Root("$"),
    Disclosures("disclosures"),
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
