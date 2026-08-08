package id.walt.walletdemo.compose.android

import id.walt.wallet2.mobile.MobileWalletDigitalCredentialPreview
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialOption
import id.walt.wallet2.mobile.MobileWalletTransactionDataItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Everything the user is consenting to for one Digital Credentials presentation.
 *
 * Kept out of the activity so it can be asserted on without a Credential Manager request: the
 * dialog's text is the entire consent record, and anything omitted from it is something the wallet
 * signs on the user's behalf without having shown it.
 */
internal fun digitalCredentialConsentMessage(
    preview: MobileWalletDigitalCredentialPreview,
    selectedOptions: List<MobileWalletPresentationCredentialOption>,
): String {
    val claimLines = selectedOptions.flatMap { option ->
        option.disclosures.filter { it.required || !it.selectable }.map { disclosure ->
            "${disclosure.name ?: disclosure.path}: ${disclosure.displayValue ?: disclosure.valueJson}"
        }
    }.distinct()
    val credentialLines = selectedOptions.map { option ->
        listOfNotNull(option.label, option.issuer, option.subject)
            .distinct()
            .joinToString(" · ")
            .ifEmpty { option.credentialId }
    }.distinct()

    return buildString {
        append("Requester: ${preview.request.verifierMetadata?.display?.name ?: preview.verifiedOrigin}\n")
        append("Protocol: ${preview.protocol}\n")
        append("\nCredential${if (credentialLines.size == 1) "" else "s"}:\n${credentialLines.joinToString("\n")}\n")
        if (claimLines.isNotEmpty()) append("\nData to share:\n${claimLines.joinToString("\n")}")
        // What the user authorizes beyond disclosure. The presentation signs over these items, so
        // omitting them would mean consenting to an unseen transaction.
        preview.request.transactionData.forEach { item ->
            append("\n\nAuthorizing ${item.displayName}:\n${item.transactionDataLines().joinToString("\n")}")
        }
    }
}

/**
 * The decoded transaction fields, falling back to the raw JSON rather than showing nothing: an item
 * the demo cannot decode is still something the user is about to authorize.
 */
private fun MobileWalletTransactionDataItem.transactionDataLines(): List<String> =
    runCatching {
        Json.parseToJsonElement(detailsJson).jsonObject.map { (field, value) ->
            "$field: ${(value as? JsonPrimitive)?.content ?: value}"
        }
    }.getOrElse { emptyList() }.ifEmpty { listOf(detailsJson) }
