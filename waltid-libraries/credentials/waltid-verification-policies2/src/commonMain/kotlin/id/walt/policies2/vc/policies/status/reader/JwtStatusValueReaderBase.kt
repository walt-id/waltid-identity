package id.walt.policies2.vc.policies.status.reader

import id.walt.policies2.vc.policies.status.StatusListContent
import id.walt.policies2.vc.policies.status.content.ContentParser
import id.walt.policies2.vc.policies.status.model.StatusContent
import id.walt.policies2.vc.policies.status.reader.format.FormatMatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

abstract class JwtStatusValueReaderBase<T : StatusContent>(
    formatMatcher: FormatMatcher,
    private val parser: ContentParser<String, JsonObject>,
) : StatusValueReaderBase<T>(formatMatcher) {

    companion object {

        protected val logger = KotlinLogging.logger {}

        protected val jsonModule = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    override fun read(content: StatusListContent) = runCatching {
        val textContent = when (content) {
            is StatusListContent.Text -> content.content
            is StatusListContent.Binary -> throw IllegalArgumentException("JWT reader requires text content")
        }
        val payload = parser.parse(textContent)
        logger.debug { "Payload: $payload" }
        val statusList = parseStatusList(payload)
        logger.debug { "EncodedList: ${statusList.list}" }
        statusList
    }

    protected abstract fun parseStatusList(payload: JsonObject): T
}
