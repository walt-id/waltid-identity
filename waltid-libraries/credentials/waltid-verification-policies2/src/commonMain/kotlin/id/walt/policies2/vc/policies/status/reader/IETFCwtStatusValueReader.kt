package id.walt.policies2.vc.policies.status.reader

import id.walt.cose.coseCompliantCbor
import id.walt.policies2.vc.policies.status.StatusListContent
import id.walt.policies2.vc.policies.status.content.ContentParser
import id.walt.policies2.vc.policies.status.model.IETFStatusContent
import id.walt.policies2.vc.policies.status.reader.format.FormatMatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.decodeFromByteArray

/**
 * Reader for IETF Token Status List in CWT format.
 * Parses binary CBOR content to extract the status list.
 */
@OptIn(ExperimentalSerializationApi::class)
class IETFCwtStatusValueReader(
    formatMatcher: FormatMatcher,
    private val parser: ContentParser<ByteArray, ByteArray>,
) : StatusValueReaderBase<IETFStatusContent>(formatMatcher) {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun read(content: StatusListContent): Result<IETFStatusContent> = runCatching {
        val binaryContent = when (content) {
            is StatusListContent.Binary -> content.content
            is StatusListContent.Text -> throw IllegalArgumentException("CWT reader requires binary content")
        }
        val payload = parser.parse(binaryContent)
        logger.debug { "Payload bytes: ${payload.size}" }
        val statusList = coseCompliantCbor.decodeFromByteArray<StatusList>(payload).statusList
        logger.debug { "EncodedList: ${statusList.list}" }
        statusList
    }

    @Serializable
    private data class StatusList(
        @SerialName("status_list")
        @CborLabel(65533)
        @ByteString
        val statusList: IETFStatusContent
    )
}