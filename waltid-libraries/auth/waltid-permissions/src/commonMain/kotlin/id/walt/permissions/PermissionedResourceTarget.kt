package id.walt.permissions

import kotlinx.serialization.Serializable

/*
 handle .* and stuff
 */
@Serializable
data class PermissionedResourceTarget(val id: String) {
    val path = id.split(".")

    override fun toString(): String {
        return path.joinToString(".")
    }

    fun targets(resource: PermissionedResource): Boolean {
        if (id == resource.id) return true

        if (resource.path.size >= path.size) {
            path.forEachIndexed { i, s ->
                if (resource.path[i] != s && s != "*") return false
            }

            return true
        } else return false
    }
}
