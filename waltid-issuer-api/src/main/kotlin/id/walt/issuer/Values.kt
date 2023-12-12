package id.walt.issuer

object Values {
    const val VERSION = "1.SNAPSHOT"
    val isSnapshot: Boolean
        get() = VERSION.contains("SNAPSHOT")
}
