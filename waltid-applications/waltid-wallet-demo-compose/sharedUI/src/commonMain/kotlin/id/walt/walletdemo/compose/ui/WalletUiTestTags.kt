package id.walt.walletdemo.compose.ui

internal object WalletUiTestTags {
    val Status = tag("status")
    val CredentialDetailsScreen = tag("credentialDetailsScreen")
    val DetailsBack = tag("detailsBack")

    fun claim(path: String): String = dynamicTag("claim", path)
    fun claimImage(path: String): String = dynamicTag("claimImage", path)
    fun claimGroup(title: String): String = dynamicTag("claimGroup", title)
    fun credentialCard(id: String): String = tag("credentialCard", id)
    fun credentialDetails(id: String): String = tag("credentialDetails", id)
    fun credentialOverview(id: String): String = tag("credentialOverview", id)

    private fun tag(vararg segments: String): String =
        (listOf(Namespace) + segments).joinToString(separator = ".")

    private fun dynamicTag(kind: String, rawValue: String): String =
        tag(kind, rawValue.toTestTagSegment())

    private const val Namespace = "wallet"
}

private fun String.toTestTagSegment(): String =
    map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
