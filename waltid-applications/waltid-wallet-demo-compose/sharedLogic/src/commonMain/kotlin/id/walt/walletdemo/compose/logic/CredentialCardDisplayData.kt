package id.walt.walletdemo.compose.logic

data class CredentialCardDisplayData(
    val id: String,
    val title: String,
    val credentialType: String?,
    val format: String,
    val issuer: String?,
    val holderName: String?,
    val date: String?,
    val validity: String?,
    val portrait: DisplayValue.Image?,
)

fun CredentialDetails.toCardDisplayData(): CredentialCardDisplayData {
    val allItems = groups.flatMap { it.items }.flatMap { it.flatten() }
    val givenName = allItems.firstTextForRole(ClaimRole.GivenName)
    val familyName = allItems.firstTextForRole(ClaimRole.FamilyName)
    val holderName = listOfNotNull(givenName, familyName)
        .joinToString(" ")
        .ifBlank { summary.subject.orEmpty() }
        .ifBlank { null }
    val expiryDate = allItems.firstExpiryDateText()
    val fallbackAddedDate = summary.addedAt

    return CredentialCardDisplayData(
        id = summary.id,
        title = summary.label,
        credentialType = allItems.firstCredentialTypeText(),
        format = summary.format,
        issuer = summary.issuer,
        holderName = holderName,
        date = expiryDate ?: fallbackAddedDate,
        validity = expiryDate?.let { CredentialDisplayText.expires(it) }
            ?: fallbackAddedDate?.let { CredentialDisplayText.added(it) },
        portrait = allItems.firstImageForRole(ClaimRole.Image),
    )
}

private fun ClaimItem.flatten(): List<ClaimItem> =
    when (val displayValue = value) {
        is DisplayValue.ObjectValue -> listOf(this) + displayValue.entries.flatMap { it.flatten() }
        else -> listOf(this)
    }

private fun List<ClaimItem>.firstTextForRole(role: ClaimRole): String? =
    firstNotNullOfOrNull { item ->
        if (item.hasRole(role)) item.value.asPlainText() else null
    }

private fun List<ClaimItem>.firstExpiryDateText(): String? =
    firstNotNullOfOrNull { item ->
        if (item.hasRole(ClaimRole.ExpiryDate)) item.value.asPlainText() else null
    }

private fun List<ClaimItem>.firstCredentialTypeText(): String? =
    firstNotNullOfOrNull { item ->
        if (item.hasRole(ClaimRole.CredentialType)) {
            item.value.asCredentialTypeText()?.let(CredentialDisplayVocabulary::readableCredentialType)
        } else {
            null
        }
    }

private fun List<ClaimItem>.firstImageForRole(role: ClaimRole): DisplayValue.Image? =
    firstNotNullOfOrNull { item ->
        if (item.hasRole(role)) item.value as? DisplayValue.Image else null
    }

private fun ClaimItem.hasRole(role: ClaimRole): Boolean =
    role in roles

private fun DisplayValue.asCredentialTypeText(): String? =
    when (this) {
        is DisplayValue.ListValue -> values
            .mapNotNull { it.asPlainText() }
            .firstOrNull { !it.isGenericVcType() }
            ?: values.firstNotNullOfOrNull { it.asPlainText() }
        else -> asPlainText()
    }

private fun DisplayValue.asPlainText(): String? =
    when (this) {
        is DisplayValue.BooleanValue -> value.toString()
        is DisplayValue.DecodedText -> value
        is DisplayValue.NumberValue -> value
        is DisplayValue.Raw -> value
        is DisplayValue.Text -> value
        else -> null
    }

private fun String.isGenericVcType(): Boolean =
    CredentialDisplayVocabulary.isGenericCredentialType(this)
