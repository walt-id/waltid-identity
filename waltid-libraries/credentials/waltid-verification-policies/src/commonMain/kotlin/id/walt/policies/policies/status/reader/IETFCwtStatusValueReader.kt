package id.walt.policies.policies.status.reader

import id.walt.cose.coseCompliantCbor
import id.walt.policies.policies.status.content.ContentParser
import id.walt.policies.policies.status.model.IETFStatusContent
import id.walt.policies.policies.status.reader.format.FormatMatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.decodeFromByteArray

@OptIn(ExperimentalSerializationApi::class)
class IETFCwtStatusValueReader(
    formatMatcher: FormatMatcher,
    private val parser: ContentParser<String, ByteArray>,
) : StatusValueReaderBase<IETFStatusContent>(formatMatcher) {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun read(response: String): Result<IETFStatusContent> = runCatching {
        val payload = parser.parse(response)
        logger.debug { "Payload: $payload" }
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