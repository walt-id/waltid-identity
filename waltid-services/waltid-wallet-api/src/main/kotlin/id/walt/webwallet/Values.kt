package id.walt.webwallet

object Values {
    const val version = "1.SNAPSHOT"
    val isSnapshot: Boolean
        get() = version.contains("SNAPSHOT")
}
