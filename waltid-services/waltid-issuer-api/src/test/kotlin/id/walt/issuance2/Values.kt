package id.walt.issuance2

object Values {
    const val VERSION = "1.SNAPSHOT"
    val isSnapshot: Boolean
        get() = VERSION.contains("SNAPSHOT")
}
