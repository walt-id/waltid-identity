package id.walt.walletdemo.compose.logic

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class WalletDemoMetadataDisplay(
    val name: String?,
    val logoUri: String?,
    val logoAltText: String?,
    val description: String? = null,
    val backgroundColor: String? = null,
    val backgroundImageUri: String? = null,
    val textColor: String? = null,
)

data class WalletDemoIssuerMetadata(
    val credentialIssuer: String,
    val display: WalletDemoMetadataDisplay?,
)

data class WalletDemoCredentialClaimMetadata(
    val path: List<JsonElement>,
    val mandatory: Boolean?,
    val displayName: String?,
) {
    constructor(path: Collection<String>, mandatory: Boolean?, displayName: String?) :
        this(path.map(::JsonPrimitive), mandatory, displayName)

    init {
        require(path.isValidClaimMetadataPath()) { "Claim metadata requires a non-empty path of strings, integers or null wildcards" }
    }
}

internal fun List<JsonElement>.isValidClaimMetadataPath(): Boolean = isNotEmpty() && all { segment ->
    segment == JsonNull || segment is JsonPrimitive && (segment.isString || metadataInteger.matches(segment.content))
}

private val metadataInteger = Regex("-?(0|[1-9][0-9]*)")

data class WalletDemoCredentialClaimDisplay(
    val label: String,
    val inclusion: String,
)

data class WalletDemoCredentialClaimDisplayGroup(
    val title: String,
    val claims: List<WalletDemoCredentialClaimDisplay>,
)

data class WalletDemoOfferedCredentialMetadata(
    val configurationId: String,
    val format: String,
    val vct: String?,
    val doctype: String?,
    val display: WalletDemoMetadataDisplay?,
    val claims: List<WalletDemoCredentialClaimMetadata>,
)

/** Friendly card title from issuer display or the shared credential-type mapping. */
fun WalletDemoOfferedCredentialMetadata.resolvedCardTitle(): String =
    resolveCardTitle(
        format = format,
        credentialDataJson = offeredTypePayloadJson(),
        displayName = display?.name,
        fallback = format,
    )

internal fun WalletDemoOfferedCredentialMetadata.offeredTypePayloadJson(): String? {
    val vct = vct?.trim()?.takeIf { it.isNotBlank() }
    val doctype = doctype?.trim()?.takeIf { it.isNotBlank() }
    if (vct == null && doctype == null) return null
    return buildJsonObject {
        vct?.let { put("vct", it) }
        doctype?.let { put("docType", it) }
    }.toString()
}

fun WalletDemoOfferedCredentialMetadata.claimDisplayGroups(): List<WalletDemoCredentialClaimDisplayGroup> =
    if (claims.isEmpty()) emptyList() else listOf(WalletDemoCredentialClaimDisplayGroup(
        title = "Credential claims",
        claims = claims.map { claim ->
            WalletDemoCredentialClaimDisplay(
                label = claim.displayLabel(format),
                inclusion = if (claim.mandatory == true) "Always included" else "May be included",
            )
        },
    ))

fun WalletDemoCredentialClaimMetadata.displayLabel(format: String): String {
    displayName?.takeIf { it.isNotBlank() }?.let { return it }
    val objectKeys = path.mapNotNull { (it as? JsonPrimitive)?.takeIf { key -> key.isString }?.content }
    if (objectKeys.size == path.size) {
        MdocClaimDisplaySemantics.describe(format = format, path = objectKeys)?.label?.let { return it }
    }
    return objectKeys.lastOrNull()?.takeIf { it.isNotBlank() }
        ?.let(CredentialDisplayVocabulary::humanizedClaimLabel) ?: "Claim"
}

enum class WalletDemoTransactionCodeInputMode {
    Numeric,
    Text,
}

data class WalletDemoTransactionCodeRequirement(
    val inputMode: WalletDemoTransactionCodeInputMode,
    val length: Int?,
    val description: String?,
) {
    fun normalizeInput(value: String): String {
        val normalized = when (inputMode) {
            WalletDemoTransactionCodeInputMode.Numeric -> value.filter { it in '0'..'9' }
            WalletDemoTransactionCodeInputMode.Text -> value
        }
        return length?.let(normalized::take) ?: normalized
    }

    fun accepts(value: String): Boolean {
        val normalized = value.trim()
        return normalized.isNotEmpty() &&
            (length == null || normalized.length == length) &&
            (inputMode != WalletDemoTransactionCodeInputMode.Numeric || normalized.all { it in '0'..'9' })
    }
}

data class WalletDemoVerifierMetadata(
    val display: WalletDemoMetadataDisplay?,
    val clientUri: String?,
    val policyUri: String?,
    val termsOfServiceUri: String?,
)
