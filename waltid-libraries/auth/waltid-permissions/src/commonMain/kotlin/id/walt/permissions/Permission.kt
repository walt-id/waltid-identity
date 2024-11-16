package id.walt.permissions

import id.walt.permissions.utils.FlowUtils.splitIntoFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
data class Permission(val target: PermissionedResourceTarget, val action: String, val operation: PermissionOperation = PermissionOperation.ADD) {

    @Serializable
    data class MinimalPermission(val target: String, val action: String) {
        override fun toString() = "[$action on $target]"
    }

    override fun toString(): String {
        return "$target:${operation.symbol}$action"
    }

    companion object {
        fun parseFromPermissionString(string: String): Flow<Permission> {
            check(':' in string) { "No ':' in string: $string" }

            val (targetString, permissionStrings) = string.split(':').toMutableList()
            val target = PermissionedResourceTarget(targetString)

            // val permissions = permissionStrings.splitToSequence(",")
            val permissions = permissionStrings.splitIntoFlow(",")
                .map { permissionString ->
                    Permission(target, permissionString.drop(1), PermissionOperation.from(permissionString[0]))
                }

            return permissions
        }
    }
}
