package id.walt.permissions

data class PermissionedResource(val id: String) {
    internal val path = id.split(".")

    override fun toString(): String {
        return path.joinToString(".")
    }
}
