package id.walt.issuer2.repository.openid4vci

import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import id.walt.openid4vci.repository.preauthorized.DefaultPreAuthorizedCodeRecord
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRecord
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRedemptionState
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
import id.walt.openid4vci.repository.preauthorized.isRedeemableAt
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class ConfiguredPreAuthorizedCodeRepository : PreAuthorizedCodeRepository {
    private val records = ConfiguredPersistence(
        "issuer2_pre_authorized_codes", defaultExpiration = 5.minutes,
        encoding = { Json.encodeToString(DefaultPreAuthorizedCodeRecord.serializer(), it) },
        decoding = { Json.decodeFromString(DefaultPreAuthorizedCodeRecord.serializer(), it) },
    )

    override suspend fun save(record: PreAuthorizedCodeRecord) {
        if (!records.compareAndSet(record.code, null, record.toDefaultRecord(), ttlUntil(record.expiresAt))) {
            throw DuplicateCodeException()
        }
    }

    override suspend fun get(code: String): PreAuthorizedCodeRecord? =
        records[code]?.takeIf { it.isRedeemableAt(Clock.System.now()) }

    override suspend fun compareAndSetRedemptionState(
        expected: PreAuthorizedCodeRecord,
        replacement: PreAuthorizedCodeRedemptionState,
    ): Boolean {
        val now = Clock.System.now()
        if (!expected.isRedeemableAt(now)) return false
        val current = expected.toDefaultRecord()
        return records.compareAndSet(
            current.code, current, current.copy(redemptionState = replacement), current.expiresAt - now,
        )
    }
}

private fun PreAuthorizedCodeRecord.toDefaultRecord() = DefaultPreAuthorizedCodeRecord(
    code = code,
    clientId = clientId,
    txCode = txCode,
    txCodeValue = txCodeValue,
    grantedScopes = grantedScopes,
    grantedAudience = grantedAudience,
    session = session,
    expiresAt = expiresAt,
    issuanceSessionId = issuanceSessionId,
    redemptionState = redemptionState,
)
