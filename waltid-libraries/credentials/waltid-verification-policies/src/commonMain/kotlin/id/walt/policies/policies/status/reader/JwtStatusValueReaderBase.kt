package id.walt.policies.policies.status.reader

import id.walt.policies.policies.status.content.ContentParser
import id.walt.policies.policies.status.model.StatusContent
import id.walt.policies.policies.status.reader.format.FormatMatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

abstract class JwtStatusValueReaderBase<T : StatusContent>(
    formatMatcher: FormatMatcher,
    private val parser: ContentParser<String, JsonObject>,
) : StatusValueReaderBase<T>(formatMatcher) {
    protected val logger = KotlinLogging.logger {}

    protected val jsonModule = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun read(response: String) = runCatching {
        val payload = parser.parse(response)
        logger.debug { "Payload: $payload" }
        val statusList = parseStatusList(payload)
        logger.debug { "EncodedList: ${statusList.list}" }
        statusList
    }

    protected abstract fun parseStatusList(payload: JsonObject): T
}
