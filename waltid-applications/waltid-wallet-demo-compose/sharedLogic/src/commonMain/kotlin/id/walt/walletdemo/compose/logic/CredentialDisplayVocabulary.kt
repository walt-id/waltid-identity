package id.walt.walletdemo.compose.logic

internal enum class ClaimGroupKind(
    val title: String,
    val order: Int,
) {
    Personal(title = "Personal details", order = 0),
    Address(title = "Address", order = 1),
    Other(title = "Credential data", order = 2),
    Technical(title = "Credential metadata", order = 3),
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
    val displayOrder: Int,
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
    const val GenericVerifiableCredentialType = "VerifiableCredential"
    const val RequestedDisclosuresTitle = "Requested disclosures"

    private const val GivenNameClaim = "given_name"
    private const val FamilyNameClaim = "family_name"

    private val descriptorList = listOf(
        ClaimDescriptor("docType", label = "Doc type", displayOrder = 0),
        ClaimDescriptor(GivenNameClaim, aliases = setOf("Given name"), label = "Given name", group = ClaimGroupKind.Personal, roles = setOf(ClaimRole.GivenName), displayOrder = 10),
        ClaimDescriptor(FamilyNameClaim, aliases = setOf("Family name"), label = "Family name", group = ClaimGroupKind.Personal, roles = setOf(ClaimRole.FamilyName), displayOrder = 20),
        ClaimDescriptor("family_name_birth", label = "Family name at birth", group = ClaimGroupKind.Personal, displayOrder = 30),
        ClaimDescriptor("birth_date", aliases = setOf("Date of birth", "birthdate"), label = "Date of birth", group = ClaimGroupKind.Personal, displayOrder = 40),
        ClaimDescriptor("birth_place", aliases = setOf("Place of birth", "place_of_birth"), label = "Place of birth", group = ClaimGroupKind.Personal, displayOrder = 50),
        ClaimDescriptor("locality", group = ClaimGroupKind.Address, displayOrder = 51),
        ClaimDescriptor("country", group = ClaimGroupKind.Address, displayOrder = 52),
        ClaimDescriptor("region", group = ClaimGroupKind.Address, displayOrder = 53),
        ClaimDescriptor("nationality", label = "Nationality", group = ClaimGroupKind.Personal, displayOrder = 60),
        ClaimDescriptor("nationalities", label = "Nationalities", group = ClaimGroupKind.Personal, displayOrder = 61),
        ClaimDescriptor("portrait", aliases = setOf("Portrait"), label = "Portrait", group = ClaimGroupKind.Personal, roles = setOf(ClaimRole.Image), displayOrder = 70),
        ClaimDescriptor("photo", roles = setOf(ClaimRole.Image), displayOrder = 71),
        ClaimDescriptor("picture", roles = setOf(ClaimRole.Image), displayOrder = 72),
        ClaimDescriptor("image", roles = setOf(ClaimRole.Image), displayOrder = 73),
        ClaimDescriptor("logo", roles = setOf(ClaimRole.Image), displayOrder = 74),
        ClaimDescriptor("age", group = ClaimGroupKind.Personal, displayOrder = 80),
        ClaimDescriptor("age_over_18", label = "Age over 18", group = ClaimGroupKind.Personal, displayOrder = 81),
        ClaimDescriptor("resident_address", label = "Resident address", group = ClaimGroupKind.Address, displayOrder = 100),
        ClaimDescriptor("resident_country", label = "Resident country", group = ClaimGroupKind.Address, displayOrder = 101),
        ClaimDescriptor("resident_state", label = "Resident state", group = ClaimGroupKind.Address, displayOrder = 102),
        ClaimDescriptor("resident_city", label = "Resident city", group = ClaimGroupKind.Address, displayOrder = 103),
        ClaimDescriptor("resident_street", label = "Resident street", group = ClaimGroupKind.Address, displayOrder = 104),
        ClaimDescriptor("resident_house_number", label = "Resident house number", group = ClaimGroupKind.Address, displayOrder = 105),
        ClaimDescriptor("resident_postal_code", label = "Resident postal code", group = ClaimGroupKind.Address, displayOrder = 106),
        ClaimDescriptor("street_address", label = "Street address", group = ClaimGroupKind.Address, displayOrder = 107),
        ClaimDescriptor("postal_code", label = "Postal code", group = ClaimGroupKind.Address, displayOrder = 108),
        ClaimDescriptor("document_number", label = "Document number", displayOrder = 120),
        ClaimDescriptor("personal_administrative_number", label = "Personal administrative number", displayOrder = 121),
        ClaimDescriptor("issuing_authority", label = "Issuing authority", displayOrder = 122),
        ClaimDescriptor("issuing_country", label = "Issuing country", displayOrder = 123),
        ClaimDescriptor("attestation_legal_category", label = "Attestation legal category", displayOrder = 124),
        ClaimDescriptor("issuer", label = "Issuer", displayOrder = 130),
        ClaimDescriptor("vct", label = "Credential type", group = ClaimGroupKind.Technical, roles = setOf(ClaimRole.CredentialType), displayOrder = 200),
        ClaimDescriptor("iss", label = "Issuer", group = ClaimGroupKind.Technical, displayOrder = 201),
        ClaimDescriptor("sub", label = "Subject", group = ClaimGroupKind.Technical, displayOrder = 202),
        ClaimDescriptor("iat", label = "Issued at", group = ClaimGroupKind.Technical, roles = setOf(ClaimRole.Temporal), displayOrder = 203),
        ClaimDescriptor("issued_at", roles = setOf(ClaimRole.Temporal), displayOrder = 204),
        ClaimDescriptor("issuance_date", roles = setOf(ClaimRole.Temporal), displayOrder = 205),
        ClaimDescriptor("valid_from", aliases = setOf("Valid from"), roles = setOf(ClaimRole.Temporal), displayOrder = 206),
        ClaimDescriptor("nbf", label = "Valid from", group = ClaimGroupKind.Technical, roles = setOf(ClaimRole.Temporal), displayOrder = 207),
        ClaimDescriptor("not_before", roles = setOf(ClaimRole.Temporal), displayOrder = 208),
        ClaimDescriptor("exp", label = "Expires", group = ClaimGroupKind.Technical, roles = setOf(ClaimRole.Temporal, ClaimRole.ExpiryDate), displayOrder = 209),
        ClaimDescriptor("expires", aliases = setOf("Expires"), roles = setOf(ClaimRole.Temporal, ClaimRole.ExpiryDate), displayOrder = 210),
        ClaimDescriptor("expiry", aliases = setOf("Expiry"), roles = setOf(ClaimRole.Temporal, ClaimRole.ExpiryDate), displayOrder = 211),
        ClaimDescriptor("expiry_date", aliases = setOf("Expiry date"), roles = setOf(ClaimRole.Temporal, ClaimRole.ExpiryDate), displayOrder = 212),
        ClaimDescriptor("expiration", aliases = setOf("Expiration"), roles = setOf(ClaimRole.Temporal, ClaimRole.ExpiryDate), displayOrder = 213),
        ClaimDescriptor("expiration_date", aliases = setOf("Expiration date"), roles = setOf(ClaimRole.Temporal, ClaimRole.ExpiryDate), displayOrder = 214),
        ClaimDescriptor("valid_until", aliases = setOf("Valid until"), roles = setOf(ClaimRole.Temporal, ClaimRole.ExpiryDate), displayOrder = 215),
        ClaimDescriptor("valid_to", aliases = setOf("Valid to"), roles = setOf(ClaimRole.Temporal, ClaimRole.ExpiryDate), displayOrder = 216),
        ClaimDescriptor("status", label = "Credential status", group = ClaimGroupKind.Technical, displayOrder = 220),
        ClaimDescriptor("credential_status", aliases = setOf("credentialStatus"), label = "Credential status", displayOrder = 221),
        ClaimDescriptor("_sd", label = "Undisclosed claims", group = ClaimGroupKind.Technical, displayOrder = 230),
        ClaimDescriptor("_sd_alg", label = "Selective disclosure algorithm", group = ClaimGroupKind.Technical, displayOrder = 231),
        ClaimDescriptor("cnf", label = "Confirmation key", group = ClaimGroupKind.Technical, displayOrder = 240),
        ClaimDescriptor("credential_type", aliases = setOf("Credential type", "credentialType"), roles = setOf(ClaimRole.CredentialType), displayOrder = 250),
    )

    private val descriptors = descriptorList
        .flatMap { descriptor -> descriptor.recognizedNames.map { name -> NormalizedClaimKey.from(name) to descriptor } }
        .toMap()
    private val displayOrderByClaimName = descriptorList
        .flatMap { descriptor -> descriptor.recognizedNames.map { name -> NormalizedClaimKey.from(name) to descriptor.displayOrder } }
        .toMap()

    private val topLevelCredentialTypeClaimNames = setOf("type").map(NormalizedClaimKey::from).toSet()
    private val credentialSubjectContainerNames = setOf("credentialSubject", "credential_subject")
        .map(NormalizedClaimKey::from)
        .toSet()
    private val w3cMetadataClaimNames = setOf(
        "@context",
        "credentialSchema",
        "credential_status",
        "credentialStatus",
        "evidence",
        "expirationDate",
        "id",
        "issuanceDate",
        "issuer",
        "proof",
        "refreshService",
        "termsOfUse",
        "type",
        "validFrom",
        "validUntil",
    ).map(NormalizedClaimKey::from).toSet()
    private val technicalContainerNames = setOf(
        "@context",
        "credentialSchema",
        "credential_status",
        "credentialStatus",
        "evidence",
        "proof",
        "refreshService",
        "termsOfUse",
    ).map(NormalizedClaimKey::from).toSet()

    fun groupKind(path: ClaimPath): ClaimGroupKind {
        if (path.isW3cMetadataClaimPath()) {
            return ClaimGroupKind.Technical
        }

        descriptorFor(path.topLevel)?.group?.let { return it }

        if (path.isCredentialSubjectWrapped()) {
            return descriptorFor(path.leaf)?.group ?: ClaimGroupKind.Other
        }

        if (path.hasTechnicalContainer()) {
            return ClaimGroupKind.Technical
        }

        return ClaimGroupKind.Other
    }

    fun humanizedClaimLabel(key: String): String =
        descriptorFor(key)?.label ?: ClaimLabelFormatter.humanize(key)

    fun disclosureLabel(name: String?, path: String): String =
        name
            ?.takeIf { it.isNotBlank() }
            ?.let(::humanizedClaimLabel)
            ?: ClaimPath.semanticLeaf(path)?.let(::humanizedClaimLabel)
            ?: path

    fun roles(path: ClaimPath): Set<ClaimRole> {
        val leaf = path.leaf
        val roles = descriptorFor(leaf)?.roles.orEmpty().toMutableSet()
        if (path.isCredentialTypeClaimPath()) roles += ClaimRole.CredentialType
        if (pathHasRole(path, ClaimRole.Image)) roles += ClaimRole.Image
        return roles
    }

    fun compareClaimPaths(left: ClaimPath, right: ClaimPath): Int {
        val orderComparison = left.displayOrderSegments().compareLexicographically(right.displayOrderSegments())
        if (orderComparison != 0) return orderComparison

        val caseInsensitivePathComparison = left.itemPath.id.compareTo(right.itemPath.id, ignoreCase = true)
        if (caseInsensitivePathComparison != 0) return caseInsensitivePathComparison

        return left.itemPath.id.compareTo(right.itemPath.id)
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

    private fun ClaimPath.isCredentialTypeClaimPath(): Boolean {
        if (NormalizedClaimKey.from(leaf) !in topLevelCredentialTypeClaimNames) return false
        return isTopLevel || components == listOf("vc", leaf)
    }

    private fun ClaimPath.isCredentialSubjectWrapped(): Boolean {
        val topLevelKey = NormalizedClaimKey.from(topLevel)
        if (topLevelKey in credentialSubjectContainerNames) return true

        val secondLevel = components.getOrNull(1) ?: return false
        return topLevel == "vc" && NormalizedClaimKey.from(secondLevel) in credentialSubjectContainerNames
    }

    private fun ClaimPath.isW3cMetadataClaimPath(): Boolean {
        val topLevelKey = NormalizedClaimKey.from(topLevel)
        if (isTopLevel && topLevelKey in w3cMetadataClaimNames) return true

        val secondLevel = components.getOrNull(1) ?: return false
        return topLevel == "vc" && NormalizedClaimKey.from(secondLevel) in w3cMetadataClaimNames
    }

    private fun ClaimPath.hasTechnicalContainer(): Boolean {
        if (NormalizedClaimKey.from(topLevel) in technicalContainerNames) return true

        val secondLevel = components.getOrNull(1) ?: return false
        return topLevel == "vc" && NormalizedClaimKey.from(secondLevel) in technicalContainerNames
    }

    private fun descriptorFor(name: String): ClaimDescriptor? =
        descriptors[NormalizedClaimKey.from(name)]

    private fun displayOrderFor(name: String): Int? =
        displayOrderByClaimName[NormalizedClaimKey.from(name)]

    private fun ClaimPath.displayOrderSegments(): List<Int> =
        components.mapNotNull(::displayOrderFor).ifEmpty { listOf(unknownClaimDisplayOrder) }

    private fun List<Int>.compareLexicographically(other: List<Int>): Int {
        for (index in 0 until minOf(size, other.size)) {
            val comparison = this[index].compareTo(other[index])
            if (comparison != 0) return comparison
        }
        return size.compareTo(other.size)
    }

    private const val unknownClaimDisplayOrder = 10_000
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
