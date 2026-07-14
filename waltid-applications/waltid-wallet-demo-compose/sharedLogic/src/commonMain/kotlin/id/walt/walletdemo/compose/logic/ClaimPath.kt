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
    }
}

internal enum class ClaimPathRoot(val id: String) {
    Root("$"),
}
