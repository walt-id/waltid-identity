package id.walt.wallet2.handlers

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class PresentationCredentialRequirement(
    val options: List<List<String>>,
)

data class PresentationCredentialOption(
    val queryId: String,
    val credentialId: String,
    val multiple: Boolean,
    val format: String,
    val issuer: String?,
    val subject: String?,
    val label: String?,
    val credentialData: JsonObject,
    val disclosures: List<PresentationDisclosure>,
    val metadata: JsonObject? = null,
)

data class PresentationDisclosure(
    val path: String,
    val name: String?,
    val value: JsonElement,
    val selectivelyDisclosable: Boolean,
    /**
     * True when the value is presented whatever the user selects: the query requires it, or the
     * disclosures it needs are already presented for a required claim.
     */
    val required: Boolean,
    val selectable: Boolean,
    /** False for unrequested clear-text data that the credential must also transmit. */
    val requested: Boolean = true,
)

data class PresentationTransactionDataItem(
    val type: String,
    val credentialQueryIds: List<String>,
    val rawJson: JsonObject,
    val details: JsonObject,
)

@Serializable
data class PresentationCredentialSelection(
    val queryId: String,
    val credentialId: String,
)

@Serializable
data class PresentationDisclosureSelection(
    val queryId: String,
    val credentialId: String,
    val path: String,
)
