package id.walt.oid4vc

object Values {
    const val version = "1.SNAPSHOT"
    val isSnapshot: Boolean
        get() = version.contains("SNAPSHOT")
}
