package id.walt.walletdemo.compose.logic

internal enum class ClaimGroupKind(
    val title: String,
    val order: Int,
) {
    Personal(title = "Personal details", order = 0),
    Address(title = "Address", order = 1),
    Other(title = "Credential data", order = 2),
    Technical(title = "Technical claims", order = 3),
}

enum class ClaimRole {
    GivenName,
    FamilyName,
    Temporal,
    ExpiryDate,
    Image,
    CredentialType,
}

private data class ClaimDescriptor(
    val name: String,
    val aliases: Set<String> = emptySet(),
    val label: String? = null,
    val group: ClaimGroupKind = ClaimGroupKind.Other,
    val roles: Set<ClaimRole> = emptySet(),
) {
    val recognizedNames: Set<String> = aliases + name
}

private data class NormalizedClaimKey(val value: String) {
    companion object {
        fun from(name: String): NormalizedClaimKey =
            NormalizedClaimKey(
                buildString {
                    for (char in name) {
                        if (char.isLetterOrDigit()) append(char.lowercaseChar())
                    }
                }
            )
    }
}

internal object CredentialDisplayVocabulary {
    const val RawCredentialDataTitle = "Raw credential data"
    const val RawCredentialDataLabel = "Raw credential data"
    const val CredentialDataJsonLabel = "Credential data JSON"
    const val GenericVerifiableCredentialType = "VerifiableCredential"

    private const val GivenNameClaim = "given_name"
    private const val FamilyNameClaim = "family_name"

    private val descriptors = listOf(
        ClaimDescriptor(GivenNameClaim, aliases = setOf("Given name"), label = "Given name", group = ClaimGroupKind.Personal, roles = setOf(ClaimRole.GivenName)),
        ClaimDescriptor(FamilyNameClaim, aliases = setOf("Family name"), label = "Family name", group = ClaimGroupKind.Personal, roles = setOf(ClaimRole.FamilyName)),
        ClaimDescriptor("birth_date", aliases = setOf("Date of birth"), label = "Date of birth", group = ClaimGroupKind.Personal),
        ClaimDescriptor("birth_place", aliases = setOf("Place of birth"), label = "Place of birth", group = ClaimGroupKind.Personal),
        ClaimDescriptor("age", group = ClaimGroupKind.Personal),
        ClaimDescriptor("age_over_18", label = "Age over 18", group = ClaimGroupKind.Personal),
        ClaimDescriptor("portrait", aliases = setOf("Portrait"), label = "Portrait", group = ClaimGroupKind.Personal, roles = setOf(ClaimRole.Image)),
        ClaimDescriptor("photo", roles = setOf(ClaimRole.Image)),
        ClaimDescriptor("picture", roles = setOf(ClaimRole.Image)),
        ClaimDescriptor("image", roles = setOf(ClaimRole.Image)),
        ClaimDescriptor("logo", roles = setOf(ClaimRole.Image)),
        ClaimDescriptor("nationalities", label = "Nationalities", group = ClaimGroupKind.Personal),
        ClaimDescriptor("resident_address", label = "Resident address", group = ClaimGroupKind.Address),
        ClaimDescriptor("street_address", label = "Street address", group = ClaimGroupKind.Address),
        ClaimDescriptor("locality", group = ClaimGroupKind.Address),
        ClaimDescriptor("postal_code", label = "Postal code", group = ClaimGroupKind.Address),
        ClaimDescriptor("country", group = ClaimGroupKind.Address),
        ClaimDescriptor("iss", label = "Issuer", group = ClaimGroupKind.Technical),
        ClaimDescriptor("vct", label = "Credential type", group = ClaimGroupKind.Technical, roles = setOf(ClaimRole.CredentialType)),
        ClaimDescriptor("credential_type", aliases = setOf("Credential type", "credentialType"), roles = setOf(ClaimRole.CredentialType)),
        ClaimDescriptor("exp", label = "Expires", group = ClaimGroupKind.Technical, roles = setOf(ClaimRole.Temporal, ClaimRole.ExpiryDate)),
        ClaimDescriptor("expires", aliases = setOf("Expires"), roles = setOf(ClaimRole.Temporal, ClaimRole.ExpiryDate)),
        ClaimDescriptor("expiry", aliases = setOf("Expiry"), roles = setOf(ClaimRole.Temporal, ClaimRole.ExpiryDate)),
        ClaimDescriptor("expiry_date", aliases = setOf("Expiry date"), roles = setOf(ClaimRole.Temporal, ClaimRole.ExpiryDate)),
        ClaimDescriptor("expiration", aliases = setOf("Expiration"), roles = setOf(ClaimRole.Temporal, ClaimRole.ExpiryDate)),
        ClaimDescriptor("expiration_date", aliases = setOf("Expiration date"), roles = setOf(ClaimRole.Temporal, ClaimRole.ExpiryDate)),
        ClaimDescriptor("valid_until", aliases = setOf("Valid until"), roles = setOf(ClaimRole.Temporal, ClaimRole.ExpiryDate)),
        ClaimDescriptor("valid_to", aliases = setOf("Valid to"), roles = setOf(ClaimRole.Temporal, ClaimRole.ExpiryDate)),
        ClaimDescriptor("valid_from", aliases = setOf("Valid from"), roles = setOf(ClaimRole.Temporal)),
        ClaimDescriptor("nbf", label = "Valid from", group = ClaimGroupKind.Technical, roles = setOf(ClaimRole.Temporal)),
        ClaimDescriptor("not_before", roles = setOf(ClaimRole.Temporal)),
        ClaimDescriptor("iat", label = "Issued at", group = ClaimGroupKind.Technical, roles = setOf(ClaimRole.Temporal)),
        ClaimDescriptor("issued_at", roles = setOf(ClaimRole.Temporal)),
        ClaimDescriptor("issuance_date", roles = setOf(ClaimRole.Temporal)),
        ClaimDescriptor("cnf", label = "Confirmation key", group = ClaimGroupKind.Technical),
    )
        .flatMap { descriptor -> descriptor.recognizedNames.map { name -> NormalizedClaimKey.from(name) to descriptor } }
        .toMap()

    private val topLevelCredentialTypeClaimNames = setOf("type").map(NormalizedClaimKey::from).toSet()

    fun groupKind(path: ClaimPath): ClaimGroupKind =
        descriptorFor(path.topLevel)?.group ?: ClaimGroupKind.Other

    fun humanizedClaimLabel(key: String): String =
        descriptorFor(key)?.label ?: ClaimLabelFormatter.humanize(key)

    fun roles(path: ClaimPath): Set<ClaimRole> {
        val leaf = path.leaf
        val roles = descriptorFor(leaf)?.roles.orEmpty().toMutableSet()
        if (path.isTopLevel && NormalizedClaimKey.from(leaf) in topLevelCredentialTypeClaimNames) roles += ClaimRole.CredentialType
        if (pathHasRole(path, ClaimRole.Image)) roles += ClaimRole.Image
        return roles
    }

    fun hasRole(path: ClaimPath, role: ClaimRole): Boolean =
        role in roles(path = path)

    fun isGenericCredentialType(value: String): Boolean =
        CredentialTypeIdentifier.token(value)?.equals(GenericVerifiableCredentialType, ignoreCase = true) == true

    fun readableCredentialType(value: String): String? {
        val token = CredentialTypeIdentifier.token(value)
            ?: return null

        if (isGenericCredentialType(token)) return null

        return ClaimLabelFormatter.humanize(token).takeIf { it.isNotBlank() }
    }

    private fun pathHasRole(path: ClaimPath, role: ClaimRole): Boolean =
        path.components.any { pathComponent ->
            role in descriptorFor(pathComponent)?.roles.orEmpty()
        }

    private fun descriptorFor(name: String): ClaimDescriptor? =
        descriptors[NormalizedClaimKey.from(name)]
}

private object ClaimLabelFormatter {
    private val wordSeparators = Regex("[_\\-. ]+")
    private val camelCaseBoundary = Regex("([a-z])([A-Z])")

    fun humanize(key: String): String =
        key.replace(camelCaseBoundary, "$1 $2")
            .split(wordSeparators)
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.lowercase() }
            .replaceFirstChar { it.titlecase() }
}
