package id.walt.permissions

import id.walt.permissions.Permission.MinimalPermission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.serialization.Serializable
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

infix fun String.permissions(permissions: List<String>) =
    permissions(permissions.asFlow())

infix fun String.permissions(permissions: Flow<String>) =
    FlowPermissionSet.fromPermissionStringsFlow(this, permissions)


@JsExport
@OptIn(ExperimentalJsExport::class)
class PermissionChecker {

    val allowTrie = PermissionTrie()
    val denyTrie = PermissionTrie()

    @JsExport.Ignore
    fun applyPermissions(set: PermissionSet) {
        set.grantPermissions.forEach {
            allowTrie.storePermission(it.target, it.action)
        }
        set.denyPermissions.forEach {
            denyTrie.storePermission(it.target, it.action)
        }
    }

    @JvmAsync()
    @JvmBlocking()
    @JsPromise()
    @JsExport.Ignore
    suspend fun applyPermissions(set: FlowPermissionSet) {
        set.permissions.collect {
            when (it.operation) {
                PermissionOperation.ADD -> {
                    allowTrie.storePermission(it.target, it.action)
                }

                PermissionOperation.REMOVE -> {
                    denyTrie.storePermission(it.target, it.action)
                }
            }
        }
    }

    fun checkPermission(target: String, operation: String) = checkPermission(PermissionedResourceTarget(target), operation)

    /**
     * Check if operation is allowed on target, and permission is not denied on target
     * @param target Target to check for operation
     * @param operation Operation to check for target
     */
    @JsExport.Ignore
    fun checkPermission(target: PermissionedResourceTarget, operation: String): Boolean {
        val isAllowed = allowTrie.hasAnyMatching(target, operation)

        if (!isAllowed) {
            return false
        }

        val isDenied = denyTrie.hasAnyMatching(target, operation)

        return !isDenied
    }

    @Serializable
    @JsExport.Ignore
    data class PermissionInsights(
        val target: PermissionedResourceTarget,
        val operation: String,
        val allowedBy: List<MinimalPermission>,
        val deniedBy: List<MinimalPermission>,
        val result: Boolean,
    ) {
        fun print() {
            println("$operation on $target: ${if (result) "✅" else "❌"} | allows: ${allowedBy} / denies: ${deniedBy}")
        }
    }

    @JsExport.Ignore
    fun checkPermissionInsights(target: String, operation: String) = checkPermissionInsights(PermissionedResourceTarget(target), operation)
    fun checkPermissionInsights(target: PermissionedResourceTarget, operation: String): PermissionInsights {
        val allowedBy = allowTrie.findAllMatching(target, operation)
        val deniedBy = denyTrie.findAllMatching(target, operation)

        val result = allowedBy.isNotEmpty() && deniedBy.isEmpty()

        return PermissionInsights(target, operation, allowedBy, deniedBy, result)
    }
}

@Deprecated("")
fun canAccessResourceInsights(
    roles: List<PermissionSet>,
    operation: String,
    resource: PermissionedResource,
): Map<PermissionSet, List<Permission>> {
    val allowCause = roles.associateWith { role ->
        role.grantPermissions.filter { p ->
            if (p.target.targets(resource)
            //.also { println("Does ${p.target} target ${resource.id}? $it") }
            ) {
                p.action == operation || p.action == "all"
            } else false
        }
    }.filterValues { it.isNotEmpty() }

    return allowCause
}

@Deprecated("")
fun canAccessResource(roles: List<PermissionSet>, operation: String, resource: PermissionedResource): Boolean {
    return roles.any {
        it.grantPermissions.any {
            if (it.target.targets(resource)) {
                it.action == operation || it.action == "all"
            } else false
        }
    }
}


