package id.walt.permissions

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@Serializable
/**
 * With evaluated lists, serializable
 * For external API use
 */
data class PermissionSet(
    val id: String,
    val grantPermissions: List<Permission>,
    val denyPermissions: List<Permission>,
) {

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        suspend fun fromPermissionStringsFlow(id: String, permissionStrings: Flow<String>): PermissionSet {
            val (grantPermissions, denyPermissions) = permissionStrings
                .flatMapMerge { Permission.parseFromPermissionString(it) }.toList()
                .partition { it.operation == PermissionOperation.ADD }
            return PermissionSet(id, grantPermissions, denyPermissions)
        }
    }

    override fun toString(): String = "[PermissionSet \"$id\": ${grantPermissions.joinToString()}]"
}

/**
 *
 */
@JsExport
@OptIn(ExperimentalJsExport::class)
data class FlowPermissionSet(
    val id: String,
    val permissions: Flow<Permission>,
//    val grantPermissions: Flow<Permission>,
//    val denyPermissions: Flow<Permission>,
) {
    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        fun fromPermissionStringsFlow(id: String, permissionStrings: Flow<String>): FlowPermissionSet {
            return FlowPermissionSet(
                id = id,
                permissions = permissionStrings.flatMapMerge { Permission.parseFromPermissionString(it) }
            )
        }

//      For Javascript
        fun fromPermissionStringFlow(id: String, permissionStrings: String): FlowPermissionSet {
            return FlowPermissionSet(
                id = id,
                permissions = Permission.parseFromPermissionString(permissionStrings)
            )
        }
    }
}

/*object PermissionSetUtils {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun splitPermissionStringFlow(permissionStrings: Flow<String>): Pair<Flow<Permission>, Flow<Permission>> {
        return permissionStrings
            .flatMapMerge { Permission.parseFromPermissionString(it) }
            .partition {
                println("Evaluating operation on $it")
                it.operation == id.walt.permissions.PermissionOperation.ADD
            }
    }
}*/
