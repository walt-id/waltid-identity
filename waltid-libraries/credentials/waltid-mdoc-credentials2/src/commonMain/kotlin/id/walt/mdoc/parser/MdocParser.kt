package id.walt.mdoc.parser

import id.walt.cose.coseCompliantCbor
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.matchesBase64Url
import id.walt.crypto.utils.HexUtils.matchesHex
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray

object MdocParser {

    private val log = KotlinLogging.logger { }

    @OptIn(ExperimentalSerializationApi::class)
    fun parseToDocument(signed: String): Document {
        val isHex = signed.matchesHex()
        val isBase64 = signed.matchesBase64Url()

        require(isHex || isBase64) { "Signed is neither hex nor base64" }
        if (isHex && isBase64) {
            log.trace { "Mdocs Document \"$signed\" is both hex & base64!" }
        }

        val signedBytes = if (isHex) signed.hexToByteArray() else signed.decodeFromBase64Url()

        val document = runCatching {
            val deviceResponse = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(signedBytes)
            log.trace { "Parsed mdoc as DeviceResponse" }
            deviceResponse.documents?.firstOrNull() ?: throw IllegalArgumentException("Mdoc document not found in DeviceResponse")
        }.recoverCatching {
            log.trace { "Parsing mdoc as Document (not DeviceResponse)" }
            coseCompliantCbor.decodeFromByteArray<Document>(signedBytes)
        }.recoverCatching {
            // OID4VCI §mso_mdoc: the credential response contains a bare IssuerSigned structure.
            // Reconstruct the Document using the docType from the MSO in the issuerAuth COSE structure.
            log.trace { "Parsing mdoc as IssuerSigned (OID4VCI credential response format)" }
            val issuerSigned = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(signedBytes)
            val docType = issuerSigned.decodeMobileSecurityObject().docType
            log.trace { "Parsed mdoc as IssuerSigned, docType=$docType" }
            Document(docType = docType, issuerSigned = issuerSigned)
        }.getOrElse { ex ->
            throw IllegalArgumentException("Was unable to parse mdoc document: ${ex.message}", ex)
        }

        return document
    }

    /**
     * Parses a stored credential for the edition-2 protocol without changing the released model API.
     * Reads the original credential so unknown fields and exact IssuerSignedItemBytes survive.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun parseToEdition2Document(signed: String): id.walt.mdoc.objects.edition2.document.Document {
        val bytes = when {
            signed.matchesHex() -> signed.hexToByteArray()
            signed.matchesBase64Url() -> signed.decodeFromBase64Url()
            else -> throw IllegalArgumentException("Signed is neither hex nor base64")
        }
        return runCatching {
            coseCompliantCbor.decodeFromByteArray<id.walt.mdoc.objects.edition2.deviceretrieval.DeviceResponse>(bytes)
                .documents?.single() ?: throw IllegalArgumentException("Expected one stored mdoc document")
        }.recoverCatching {
            coseCompliantCbor.decodeFromByteArray<id.walt.mdoc.objects.edition2.document.Document>(bytes)
        }.recoverCatching {
            val issuerSigned = coseCompliantCbor
                .decodeFromByteArray<id.walt.mdoc.objects.edition2.document.IssuerSigned>(bytes)
            id.walt.mdoc.objects.edition2.document.Document(issuerSigned.decodeMobileSecurityObject().docType, issuerSigned)
        }.getOrElse { throw IllegalArgumentException("Unable to parse edition-2 mdoc credential", it) }
    }

}
