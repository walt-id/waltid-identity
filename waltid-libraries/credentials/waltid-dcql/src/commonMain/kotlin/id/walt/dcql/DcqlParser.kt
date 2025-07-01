package id.walt.dcql

import id.walt.dcql.models.DcqlQuery
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlin.Result

object DcqlParser {

    private val log = KotlinLogging.logger {}

    private val json = Json {
        ignoreUnknownKeys = true // Be lenient with extra fields
        isLenient = true         // Allow slightly malformed JSON if possible
        prettyPrint = true       // For easier debugging output
        coerceInputValues = true // Attempt to coerce types if possible
    }

    fun parse(jsonString: String): Result<DcqlQuery> {
        return try {
            val query = json.decodeFromString<DcqlQuery>(jsonString)
            Result.success(query)
        } catch (e: Exception) {
            log.error(e) { "Failed to parse DCQL JSON" }
            Result.failure(IllegalArgumentException("Error while parsing invalid DCQL query", e))
        }
    }

    fun toJson(query: DcqlQuery): String {
        return json.encodeToString(DcqlQuery.serializer(), query)
    }
}
