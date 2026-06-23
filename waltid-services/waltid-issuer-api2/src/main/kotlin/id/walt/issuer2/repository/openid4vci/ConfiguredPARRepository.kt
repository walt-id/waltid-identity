package id.walt.issuer2.repository.openid4vci

import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.openid4vci.repository.par.DefaultPARRecord
import id.walt.openid4vci.repository.par.DuplicatePARRecordException
import id.walt.openid4vci.repository.par.PARRecord
import id.walt.openid4vci.repository.par.PARRepository
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ConfiguredPARRepository : PARRepository {
    private val records = ConfiguredPersistence(
        "issuer2_pushed_authorization_requests", defaultExpiration = 90.seconds,
        encoding = { Json.encodeToString(DefaultPARRecord.serializer(), it) },
        decoding = { Json.decodeFromString(DefaultPARRecord.serializer(), it) },
    )

    override suspend fun save(record: PARRecord) {
        if (records.contains(record.requestId)) {
            throw DuplicatePARRecordException()
        }
        records.set(record.requestId, record.toDefaultRecord(), ttlUntil(record.expiresAt))
    }

    override suspend fun consume(requestId: String, now: Instant): PARRecord? {
        val record = records.getAndRemove(requestId) ?: return null
        return record.takeIf { now < it.expiresAt }
    }
}

private fun PARRecord.toDefaultRecord() = DefaultPARRecord(
    requestId = requestId,
    requestParameters = requestParameters,
    createdAt = createdAt,
    expiresAt = expiresAt,
    clientMetadata = clientMetadata
)