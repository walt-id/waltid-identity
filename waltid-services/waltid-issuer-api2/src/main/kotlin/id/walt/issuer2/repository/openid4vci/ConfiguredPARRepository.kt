package id.walt.issuer2.repository.openid4vci

import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.commons.persistence.Persistence
import id.walt.openid4vci.repository.par.DefaultPARRecord
import id.walt.openid4vci.repository.par.DuplicatePARRecordException
import id.walt.openid4vci.repository.par.PARRecord
import id.walt.openid4vci.repository.par.PARRepository
import id.walt.openid4vci.requests.authorization.toDefaultAuthorizationRequest
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.serialization.json.*

class ConfiguredPARRepository(
    private val records: Persistence<String> = ConfiguredPersistence(
        "issuer2_pushed_authorization_requests", defaultExpiration = 90.seconds,
        encoding = { value: String -> value },
        decoding = { it },
    ),
) : PARRepository {
    override suspend fun save(record: PARRecord) {
        if (records.contains(record.requestId)) {
            throw DuplicatePARRecordException()
        }
        records.set(record.requestId, Json.encodeToString(record.toDefaultRecord()), ttlUntil(record.expiresAt))
    }

    override suspend fun consume(requestId: String, now: Instant): PARRecord? {
        val stored = records.getAndRemove(requestId) ?: return null
        val document = Json.parseToJsonElement(stored).jsonObject
        if ((document["authorizationRequest"] as? JsonObject)?.get("client") !is JsonObject) return null
        val record = Json.decodeFromJsonElement<DefaultPARRecord>(document)
        return record.takeIf { now < it.expiresAt }
    }
}

private fun PARRecord.toDefaultRecord() = DefaultPARRecord(
    requestId = requestId,
    authorizationRequest = authorizationRequest.toDefaultAuthorizationRequest(),
    createdAt = createdAt,
    expiresAt = expiresAt,
    clientMetadata = clientMetadata
)