package id.walt.issuer2.repository.openid4vci

import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRecord
import id.walt.openid4vci.repository.authorization.AuthorizationCodeRepository
import id.walt.openid4vci.repository.authorization.DefaultAuthorizationCodeRecord
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class ConfiguredAuthorizationCodeRepository : AuthorizationCodeRepository {
    private val records = ConfiguredPersistence(
        "issuer2_authorization_codes", defaultExpiration = 5.minutes,
        encoding = { Json.encodeToString(DefaultAuthorizationCodeRecord.serializer(), it) },
        decoding = { Json.decodeFromString(DefaultAuthorizationCodeRecord.serializer(), it) },
    )

    override suspend fun save(record: AuthorizationCodeRecord) {
        if (records.contains(record.code)) {
            throw DuplicateCodeException()
        }
        records.set(record.code, record.toDefaultRecord(), ttlUntil(record.expiresAt))
    }

    override suspend fun consume(code: String): AuthorizationCodeRecord? {
        val record = records.getAndRemove(code) ?: return null
        return record.takeIf { Clock.System.now() <= it.expiresAt }
    }
}

private fun AuthorizationCodeRecord.toDefaultRecord() = DefaultAuthorizationCodeRecord(
    code = code,
    clientId = clientId,
    redirectUri = redirectUri,
    grantedScopes = grantedScopes,
    grantedAudience = grantedAudience,
    session = session,
    expiresAt = expiresAt,
)
