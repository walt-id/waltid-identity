package id.walt.authkit.permissions

data class PermissionedResource(val id: String) {
    internal val path = id.split(".")

    override fun toString(): String {
        return path.joinToString(".")
    }
}
