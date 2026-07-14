package id.walt.walletdemo.compose.logic

fun CredentialDetails.toSystemInfoGroup(): ClaimGroup? {
    val items = listOfNotNull(
        summary.addedAt.toSystemInfoItem(path = "system.added", label = "Added"),
        summary.id.toSystemInfoItem(path = "system.id", label = "Credential ID"),
        summary.format.toSystemInfoItem(path = "system.format", label = "Format"),
        summary.issuer.toSystemInfoItem(path = "system.issuer", label = "Issuer"),
        summary.subject.toSystemInfoItem(path = "system.subject", label = "Subject"),
    )

    return items.takeIf { it.isNotEmpty() }?.let {
        ClaimGroup(title = "About this credential", items = it)
    }
}

private fun String?.toSystemInfoItem(path: String, label: String): ClaimItem? {
    val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return ClaimItem(
        path = ClaimItemPath.topLevel(path),
        label = label,
        value = DisplayValue.Text(value),
        rawValue = value,
    )
}
