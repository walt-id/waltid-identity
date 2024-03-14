package id.walt.webwallet

object Values {
    const val version = "1.0.SNAPSHOT"
    val isSnapshot: Boolean
        get() = version.contains("SNAPSHOT")
    
    val versionNumber: Double
        get() = version.substring(0,3).toDouble()
}
