@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.digitalcredentials

import kotlinx.serialization.ExperimentalSerializationApi
import id.walt.credentials.formats.MdocsCredential
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.mobile.credentials.WalletCredential
import id.walt.wallet2.mobile.presentation.DisclosureDisposition
import id.walt.wallet2.mobile.presentation.PresentationCredentialOption
import id.walt.wallet2.mobile.presentation.PresentationDisclosure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun DeviceRequest.toParsedRequest(): AnnexCParsedRequest =
    AnnexCParsedRequest(
        documents = docRequests.map { docRequest ->
            val items = docRequest.itemsRequest.value
            AnnexCDocumentRequest(
                docType = items.docType,
                namespaces = items.namespaces.entries
                    .sortedBy { it.key }
                    .associate { (namespace, requests) ->
                        namespace to requests.entries.map { it.key }.distinct().sorted()
                    },
            )
        },
    ).normalized()

internal fun AnnexCParsedRequest.normalized(): AnnexCParsedRequest =
    copy(
        documents = documents.map { document ->
            document.copy(
                namespaces = document.namespaces.entries
                    .sortedBy { it.key }
                    .associate { (namespace, elements) ->
                        namespace to elements.distinct().sorted()
                    },
            )
        },
    )

internal fun StoredCredential.toAnnexCOption(
    index: Int,
    request: AnnexCDocumentRequest,
    selectedCredentialIds: Set<String>,
): PresentationCredentialOption? {
    val mdoc = credential as? MdocsCredential ?: return null
    if (mdoc.docType != request.docType) return null
    if (selectedCredentialIds.isNotEmpty() && id !in selectedCredentialIds) return null
    val disclosures = request.namespaces.flatMap { (namespace, elementNames) ->
        val namespaceData = mdoc.credentialData[namespace] as? JsonObject ?: return null
        elementNames.map { elementName ->
            val value = namespaceData[elementName] ?: return null
            PresentationDisclosure(
                path = "$namespace.$elementName",
                name = elementName,
                valueJson = Json.encodeToString(JsonElement.serializer(), value),
                displayValue = value.displayValue(),
                disposition = DisclosureDisposition.RequiredSelective,
            )
        }
    }
    val metadata = toMetadata()
    return PresentationCredentialOption(
        queryId = annexCQueryId(index, request.docType),
        credential = WalletCredential(
            id = id,
            format = metadata.format,
            credentialDataJson = Json.encodeToString(JsonObject.serializer(), mdoc.credentialData),
            issuer = metadata.issuer,
            subject = metadata.subject,
            label = metadata.label,
            metadataJson = this.metadata?.let { Json.encodeToString(JsonObject.serializer(), it) },
        ),
        multiple = false,
        disclosures = disclosures,
    )
}

private fun JsonElement.displayValue(): String? =
    (this as? JsonPrimitive)?.contentOrNull ?: toString()

internal fun annexCQueryId(index: Int, docType: String): String = "$index:$docType"
