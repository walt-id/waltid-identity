package id.walt.walletdemo.compose.logic

internal object CredentialDisplayText {
    const val Unknown = "Unknown"
    const val ImageUnavailable = "Image unavailable or unsupported"

    fun expires(date: String): String = "Expires $date"
    fun added(date: String): String = "Added $date"
}
