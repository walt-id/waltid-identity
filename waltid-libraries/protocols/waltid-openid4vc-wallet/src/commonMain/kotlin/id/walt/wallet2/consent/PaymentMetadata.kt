package id.walt.wallet2.consent

import id.waltid.openid4vci.wallet.metadata.LocalizedMetadata
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull

/** The wire alternatives are validated before any reference is retrieved. */
internal sealed interface PaymentMetadataSource {
    class Inline(val value: JsonElement) : PaymentMetadataSource
    class Reference(val uri: String, val integrity: String?) : PaymentMetadataSource
}

internal class PaymentMetadataSources(val claims: PaymentMetadataSource, val catalogue: PaymentMetadataSource)

internal fun paymentMetadataSources(metadata: JsonObject): PaymentMetadataSources {
    if ("extends" in metadata || "extends#integrity" in metadata) {
        consentFailure(PaymentConsentFailure.UNSUPPORTED_SCHEMA)
    }
    if (("schema" in metadata) == ("schema_uri" in metadata)) consentFailure(PaymentConsentFailure.INVALID_METADATA)
    if ("schema_uri" in metadata || metadata["schema"] != JsonPrimitive(TS12_PAYMENT_TYPE)) {
        consentFailure(PaymentConsentFailure.UNSUPPORTED_SCHEMA)
    }
    if ("schema_uri#integrity" in metadata) consentFailure(PaymentConsentFailure.INVALID_METADATA)
    return PaymentMetadataSources(source(metadata, "claims"), source(metadata, "ui_labels"))
}

private fun source(metadata: JsonObject, name: String): PaymentMetadataSource {
    val uriName = "${name}_uri"
    if ((name in metadata) == (uriName in metadata)) consentFailure(PaymentConsentFailure.INVALID_METADATA)
    return if (name in metadata) {
        if ("$uriName#integrity" in metadata) consentFailure(PaymentConsentFailure.INVALID_METADATA)
        PaymentMetadataSource.Inline(metadata.getValue(name))
    } else PaymentMetadataSource.Reference(
        metadata.string(uriName, PaymentConsentFailure.INVALID_METADATA),
        metadata.optionalString("$uriName#integrity"),
    )
}

/** Resolves a common preferred language range; regional tags need not be identical across labels. */
internal fun localizePaymentConsent(
    claimsDocument: JsonElement,
    catalogueDocument: JsonElement,
    values: Map<List<String>, String>,
    preferredLocales: List<String>,
): PaymentConsent {
    val claims = claimsDocument as? JsonArray ?: consentFailure(PaymentConsentFailure.INVALID_METADATA)
    val catalogue = catalogueDocument as? JsonObject ?: consentFailure(PaymentConsentFailure.INVALID_METADATA)
    if ("affirmative_action_label" !in catalogue) {
        consentFailure(PaymentConsentFailure.INVALID_METADATA)
    }
    val labels = catalogue.filterKeys { it in CATALOGUE_LIMITS }.mapValues { (name, entries) -> translations(entries, "lang") { entry ->
        val label = entry.string("value", PaymentConsentFailure.INVALID_METADATA)
        if (label.codePointLength() > CATALOGUE_LIMITS.getValue(name)) consentFailure(PaymentConsentFailure.INVALID_METADATA)
        label
    } }
    val fields = claims.map { raw ->
        val claim = raw as? JsonObject ?: consentFailure(PaymentConsentFailure.INVALID_METADATA)
        val path = (claim["path"] as? JsonArray)?.map { element ->
            val part = element as? JsonPrimitive ?: consentFailure(PaymentConsentFailure.INVALID_METADATA)
            if (!part.isString || part.content.isBlank()) consentFailure(PaymentConsentFailure.INVALID_METADATA)
            part.content
        }?.takeIf { it.isNotEmpty() } ?: consentFailure(PaymentConsentFailure.INVALID_METADATA)
        // TS-12 section 3.3.2 applies Claim Metadata to the transaction payload.
        // Resolve against that object; never guess another root from provider data.
        if ("mandatory" in claim) {
            val mandatory = (claim["mandatory"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
                ?: consentFailure(PaymentConsentFailure.INVALID_METADATA)
            if (mandatory && path !in values) consentFailure(PaymentConsentFailure.INVALID_METADATA)
        }
        val level = if ("visualisation" in claim) {
            val value = claim["visualisation"] as? JsonPrimitive ?: consentFailure(PaymentConsentFailure.INVALID_METADATA)
            if (value.isString) consentFailure(PaymentConsentFailure.INVALID_METADATA)
            value.intOrNull ?: consentFailure(PaymentConsentFailure.INVALID_METADATA)
        } else 3
        val placement = when (level) {
            1 -> PaymentFieldPlacement.PROMINENT
            2 -> PaymentFieldPlacement.MAIN
            3 -> PaymentFieldPlacement.DETAILS
            4 -> PaymentFieldPlacement.OMITTED
            else -> consentFailure(PaymentConsentFailure.INVALID_METADATA)
        }
        // SD-JWT VC draft 16 section 4.6.2 requires locale. Unrecognized metadata
        // properties are ignored per section 4.2, not used as aliases for required ones.
        val display = translations(claim["display"], "locale") { entry ->
            ClaimLabel(entry.string("label", PaymentConsentFailure.INVALID_METADATA), entry.optionalString("description"))
        }
        LocalizedClaim(path, placement, display)
    }
    if (fields.map { it.path }.distinct().size != fields.size) consentFailure(PaymentConsentFailure.INVALID_METADATA)
    val fieldsByPath = fields.associateBy { it.path }
    if (!fieldsByPath.keys.containsAll(values.keys)) consentFailure(PaymentConsentFailure.INVALID_METADATA)
    val presentFields = fields.filter { it.path in values }
    val languageSets = labels.values.map { it.keys } + presentFields.map { it.labels.keys }
    val candidates = LocalizedMetadata.normalizedPreferences(preferredLocales).flatMap(::paymentLanguageRanges).distinct()
    val locale = candidates.firstOrNull { candidate -> languageSets.all { it.matchingTag(candidate) != null } }
        ?: consentFailure(PaymentConsentFailure.MISSING_TRANSLATION)
    fun <T> Map<String, T>.localized(): T = getValue(requireNotNull(keys.matchingTag(locale)))
    return PaymentConsent(
        locale = locale,
        title = labels["transaction_title"]?.localized(),
        securityHint = labels["security_hint"]?.localized(),
        affirmativeAction = labels.getValue("affirmative_action_label").localized(),
        denialAction = labels["denial_action_label"]?.localized(),
        fields = presentFields.map { field ->
            val label = field.labels.localized()
            PaymentConsentField(field.path, label.label, label.description, values.getValue(field.path), field.placement)
        },
    )
}

/** RFC 4647 section 3.4 lookup ranges, with no unrelated-language default. */
private fun paymentLanguageRanges(preference: String): List<String> = buildList {
    var range = preference.lowercase()
    while (range.isNotEmpty()) {
        add(range)
        range = range.substringBeforeLast('-', "")
        if (range.substringAfterLast('-').length == 1) range = range.substringBeforeLast('-', "")
    }
}

// Prefer an exact tag; otherwise publisher order breaks ties between regional variants.
private fun Set<String>.matchingTag(range: String): String? =
    range.takeIf { it in this } ?: firstOrNull { it.startsWith("$range-") }

private class ClaimLabel(val label: String, val description: String?)
private class LocalizedClaim(val path: List<String>, val placement: PaymentFieldPlacement, val labels: Map<String, ClaimLabel>)

private fun <T> translations(raw: JsonElement?, languageKey: String, parse: (JsonObject) -> T): Map<String, T> {
    val entries = raw as? JsonArray ?: consentFailure(PaymentConsentFailure.INVALID_METADATA)
    if (entries.isEmpty()) consentFailure(PaymentConsentFailure.INVALID_METADATA)
    return buildMap {
        entries.forEach { rawEntry ->
            val entry = rawEntry as? JsonObject ?: consentFailure(PaymentConsentFailure.INVALID_METADATA)
            val tag = entry.string(languageKey, PaymentConsentFailure.INVALID_METADATA)
            if (LocalizedMetadata.normalizedPreferences(listOf(tag)).singleOrNull() != tag) consentFailure(PaymentConsentFailure.INVALID_METADATA)
            val locale = tag.lowercase()
            if (containsKey(locale)) consentFailure(PaymentConsentFailure.INVALID_METADATA)
            put(locale, parse(entry))
        }
    }
}

private fun JsonObject.optionalString(name: String): String? =
    if (name in this) string(name, PaymentConsentFailure.INVALID_METADATA) else null

private val CATALOGUE_LIMITS = mapOf(
    "affirmative_action_label" to 30,
    "denial_action_label" to 30,
    "transaction_title" to 50,
    "security_hint" to 250,
)
