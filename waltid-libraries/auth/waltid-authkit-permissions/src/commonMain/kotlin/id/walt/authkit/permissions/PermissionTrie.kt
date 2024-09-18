package id.walt.authkit.permissions

import id.walt.authkit.permissions.Permission.MinimalPermission
import id.walt.authkit.permissions.utils.times
import kotlinx.serialization.Serializable

/**
 * A search tree data structure in the form of a compacted Trie (also called Patricia-Trie) for
 * efficient permission lookup.
 * Compacting the permissions is reached by having the full target be the search path, and use
 *  separate tree for the operations.
 */
@Serializable
class PermissionTrie(
    val root: HashMap<String, PermissionTreeNode> = HashMap<String, PermissionTreeNode>(),
) {

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("[PermissionTrie]")
        root.forEach { (_, node) ->
            sb.append("\n$node")
        }
        return sb.toString()
    }

    @Serializable
    data class PermissionTreeNode(
        val thisName: String,

        val permissionMethods: ArrayList<String>,
        val children: HashMap<String, PermissionTreeNode>,
    ) {
        constructor(id: String) : this(id, ArrayList(), HashMap())

        /**
         * Check if the permissions of this PermissionTreeNode contain action (on the target (= path where the PermissionTreeNode is))
         */
        fun hasActionPermission(action: String): Boolean {
            return action in permissionMethods || "all" in permissionMethods
        }

        fun getAllMatchingActionPermission(action: String): List<String> {
            return permissionMethods.filter { it == action || it == "all" }
        }

        fun toString(index: Int): String {
            val sb = StringBuilder()
            sb.append("  " * index + "- $thisName: $permissionMethods")
            if (children.isNotEmpty()) {
                children.forEach {
                    sb.append("\n" + "  " * index + it.value.toString(index + 2))
                }
            }
            return sb.toString()
        }

        override fun toString(): String {
            return toString(0)
        }
    }

    fun getOrMakeRoot(id: String): PermissionTreeNode = root.getOrPut(id) { PermissionTreeNode(id) }

    fun storePermission(target: PermissionedResourceTarget, permission: String) {
        val targetPath = target.path
        val root = getOrMakeRoot(targetPath[0])
        val remainingTraversals = targetPath.drop(1)

        var currentNode = root
        remainingTraversals.forEach {
            currentNode = currentNode.children.getOrPut(it) { PermissionTreeNode(it) }
        }
        currentNode.permissionMethods += permission
    }

    inline fun traverseMatching(
        target: PermissionedResourceTarget,
        permission: String,
        nextTraversal: (String) -> Unit = {},
        onMatch: () -> Unit,
    ): Unit? {
        val targetPath = target.path
        val root = root[targetPath[0]] ?: return null
        val remainingTraversals = targetPath.drop(1)

        var currentNode = root
        remainingTraversals.forEach {
            if (currentNode.hasActionPermission(permission)) {
                onMatch.invoke()
            }
            currentNode = currentNode.children[it] ?: currentNode.children["*"] ?: return null
            nextTraversal.invoke(it)
        }
        if (currentNode.hasActionPermission(permission)) {
            onMatch.invoke()
        }
        return Unit
    }

    fun hasAny2(target: PermissionedResourceTarget, permission: String): Boolean {
        traverseMatching(target, permission) {
            return true
        }
        return false
    }

    fun findAll2(target: PermissionedResourceTarget, permission: String): List<String> {
        val matching = ArrayList<String>()

        var currentPath = target.path[0]
        traverseMatching(target, permission, nextTraversal = { currentPath += ".$it" }) {
            matching.add(currentPath)
        }
        return matching
    }

    /**
     * @param target example: organization1.tenant1.resource1
     * @param permission example: use
     */
    fun hasAnyMatching(target: PermissionedResourceTarget, permission: String): Boolean {
        val targetPath = target.path
        val root = root[targetPath[0]] ?: return false
        val remainingTraversals = targetPath.drop(1)

        var currentNode = root
        remainingTraversals.forEach {
            if (currentNode.hasActionPermission(permission)) return true
            currentNode = currentNode.children[it] ?: currentNode.children["*"] ?: return false
        }
        return currentNode.hasActionPermission(permission)
    }

    fun findAllMatching(target: PermissionedResourceTarget, permission: String): List<MinimalPermission> {
        val targetPath = target.path
        val root = root[targetPath[0]] ?: return emptyList()
        val remainingTraversals = targetPath.drop(1)

        val matching = ArrayList<MinimalPermission>()

        var currentPath = targetPath[0]
        var currentNode = root
        remainingTraversals.forEach {
            currentNode.getAllMatchingActionPermission(permission).forEach {
                matching.add(MinimalPermission(currentPath, it))
            }

            currentNode = currentNode.children[it] ?: currentNode.children["*"] ?: return matching
            currentPath += ".$it"
        }
        currentNode.getAllMatchingActionPermission(permission).forEach {
            matching.add(MinimalPermission(currentPath, it))
        }
        return matching
    }
}
