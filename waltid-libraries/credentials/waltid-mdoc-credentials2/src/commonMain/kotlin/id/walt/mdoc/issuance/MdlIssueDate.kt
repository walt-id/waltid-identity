package id.walt.mdoc.issuance

import id.walt.mdoc.objects.elements.IssuerSignedItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.CborString
import kotlin.time.Instant

internal const val MDL_NAMESPACE = "org.iso.18013.5.1"

/**
 * ISO/IEC 18013-5:2021 §7.2.1: when namespace `org.iso.18013.5.1` is present, `issue_date` must be
 * at or before MSO `validFrom`. Full-date values compare as the start of that UTC day; tdate values
 * compare as instants. `expiry_date` is not compared to `validUntil` (§9.1.2.4 allows document
 * expiry after MSO expiry). Unrelated document types are skipped.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun requireMdlIssueDateNotAfterValidFrom(
    namespaceItems: Map<String, List<IssuerSignedItem>>,
    validFrom: Instant,
) {
    val items = namespaceItems[MDL_NAMESPACE] ?: return
    val issueDateItem = items.find { it.elementIdentifier == "issue_date" } ?: return
    val issueDate = issueDateItem.elementValue.toMdlIssueDateInstant()
        ?: throw IllegalArgumentException("org.iso.18013.5.1.issue_date must be a full-date or tdate")
    if (issueDate > validFrom) {
        throw IllegalArgumentException("org.iso.18013.5.1.issue_date cannot be after validFrom")
    }
}

@OptIn(ExperimentalSerializationApi::class)
private fun kotlinx.serialization.cbor.CborElement.toMdlIssueDateInstant(): Instant? {
    val text = (this as? CborString)?.value ?: return null
    val isFullDate = 1004uL in tags || (tags.isEmpty() && text.length == 10 && 'T' !in text)
    return if (isFullDate) {
        LocalDate.parse(text).atStartOfDayIn(TimeZone.UTC)
    } else {
        Instant.parse(text)
    }
}
