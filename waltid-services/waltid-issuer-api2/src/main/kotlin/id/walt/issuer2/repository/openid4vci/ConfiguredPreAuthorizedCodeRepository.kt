package id.walt.issuer2.repository.openid4vci

import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import id.walt.openid4vci.repository.preauthorized.DefaultPreAuthorizedCodeRecord
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRecord
import id.walt.openid4vci.repository.preauthorized.PreAuthorizedCodeRepository
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
        if (records.contains(record.code)) {
            throw DuplicateCodeException()
        }
        records.set(record.code, record.toDefaultRecord(), ttlUntil(record.expiresAt))
    }

    override suspend fun get(code: String): PreAuthorizedCodeRecord? =
        records[code]?.takeIf { Clock.System.now() <= it.expiresAt }

    override suspend fun consume(code: String): PreAuthorizedCodeRecord? {
        // Follows issuer1's ConfiguredPersistence style. ConfiguredPersistence does not expose
        // atomic get-and-remove semantics yet, so stricter replay protection belongs in commons.
        val record = records[code] ?: return null
        records.remove(code)
        return record.takeIf { Clock.System.now() <= it.expiresAt }
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
    credentialNonce = credentialNonce,
    credentialNonceExpiresAt = credentialNonceExpiresAt,
    issuanceSessionId = issuanceSessionId,
)
