package id.walt.issuer2.repository.openid4vci

import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.openid4vci.Client
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.DefaultSession
import id.walt.openid4vci.Session
import id.walt.openid4vci.TokenType
import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import id.walt.openid4vci.repository.refresh.DefaultRefreshTokenRecord
import id.walt.openid4vci.repository.refresh.RefreshTokenRecord
import id.walt.openid4vci.repository.refresh.RefreshTokenRepository
import id.walt.openid4vci.requests.token.AccessTokenRequest
import id.walt.openid4vci.requests.token.DefaultAccessTokenRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ConfiguredRefreshTokenRepository : RefreshTokenRepository {
    private val records = ConfiguredPersistence(
        "issuer2_refresh_tokens", defaultExpiration = 30.days,
        encoding = { Json.encodeToString(StoredRefreshTokenRecord.serializer(), it) },
        decoding = { Json.decodeFromString(StoredRefreshTokenRecord.serializer(), it) },
    )
    private val rotationLock = Any()

    override suspend fun save(record: RefreshTokenRecord) {
        synchronized(rotationLock) {
            if (records.contains(record.tokenSignature)) {
                throw DuplicateCodeException()
            }
            records.set(record.tokenSignature, record.toStoredRecord(), refreshTokenTtlUntil(record.expiresAt))
        }
    }

    override suspend fun get(tokenSignature: String): RefreshTokenRecord? =
        records[tokenSignature]?.toLibraryRecord()

    override suspend fun rotate(
        tokenSignature: String,
        replacement: RefreshTokenRecord,
    ): RefreshTokenRecord? = synchronized(rotationLock) {
        val current = records[tokenSignature] ?: return@synchronized null
        val currentExpiresAt = Instant.fromEpochMilliseconds(current.expiresAt)
        if (!current.active || Clock.System.now() >= currentExpiresAt) {
            records.set(tokenSignature, current.copy(active = false), refreshTokenTtlUntil(currentExpiresAt))
            return@synchronized null
        }
        if (records.contains(replacement.tokenSignature)) {
            throw DuplicateCodeException()
        }

        records.set(tokenSignature, current.copy(active = false), refreshTokenTtlUntil(currentExpiresAt))
        records.set(replacement.tokenSignature, replacement.toStoredRecord(), refreshTokenTtlUntil(replacement.expiresAt))

        current.toLibraryRecord()
    }
}

@Serializable
private data class StoredRefreshTokenRecord(
    val tokenSignature: String,
    val active: Boolean,
    val accessTokenRequest: StoredAccessTokenRequest,
    val accessTokenSignature: String,
    val clientId: String?,
    val grantedScopes: Set<String>,
    val grantedAudience: Set<String>,
    val session: StoredSession,
    val expiresAt: Long,
)

@Serializable
private data class StoredAccessTokenRequest(
    val id: String,
    val requestedAt: Long,
    val client: StoredClient,
    val grantTypes: Set<String>,
    val handledGrantTypes: Set<String>,
    val requestedScopes: Set<String>,
    val grantedScopes: Set<String>,
    val requestedAudience: Set<String>,
    val grantedAudience: Set<String>,
    val requestForm: Map<String, List<String>>,
    val session: StoredSession?,
    val issClaim: String?,
)

@Serializable
private data class StoredClient(
    val id: String,
    val redirectUris: List<String>,
    val grantTypes: Set<String>,
    val responseTypes: Set<String>,
    val scopes: Set<String>,
    val audience: Set<String>,
)

@Serializable
private data class StoredSession(
    val subject: String?,
    val expiresAt: Map<String, Long>,
    val customAttributes: Map<String, String> = emptyMap(),
)

private fun RefreshTokenRecord.toStoredRecord(): StoredRefreshTokenRecord =
    StoredRefreshTokenRecord(
        tokenSignature = tokenSignature,
        active = active,
        accessTokenRequest = accessTokenRequest.toStoredRequest(),
        accessTokenSignature = accessTokenSignature,
        clientId = clientId,
        grantedScopes = grantedScopes,
        grantedAudience = grantedAudience,
        session = session.toStoredSession(),
        expiresAt = expiresAt.toEpochMilliseconds(),
    )

private fun StoredRefreshTokenRecord.toLibraryRecord(): DefaultRefreshTokenRecord =
    DefaultRefreshTokenRecord(
        tokenSignature = tokenSignature,
        active = active,
        accessTokenRequest = accessTokenRequest.toAccessTokenRequest(),
        accessTokenSignature = accessTokenSignature,
        clientId = clientId,
        grantedScopes = grantedScopes,
        grantedAudience = grantedAudience,
        session = session.toSession(),
        expiresAt = Instant.fromEpochMilliseconds(expiresAt),
    )

private fun AccessTokenRequest.toStoredRequest(): StoredAccessTokenRequest =
    StoredAccessTokenRequest(
        id = id,
        requestedAt = requestedAt.toEpochMilliseconds(),
        client = client.toStoredClient(),
        grantTypes = grantTypes,
        handledGrantTypes = handledGrantTypes,
        requestedScopes = requestedScopes,
        grantedScopes = grantedScopes,
        requestedAudience = requestedAudience,
        grantedAudience = grantedAudience,
        requestForm = requestForm,
        session = session?.toStoredSession(),
        issClaim = issClaim,
    )

private fun StoredAccessTokenRequest.toAccessTokenRequest(): DefaultAccessTokenRequest =
    DefaultAccessTokenRequest(
        id = id,
        requestedAt = Instant.fromEpochMilliseconds(requestedAt),
        client = client.toClient(),
        grantTypes = grantTypes,
        handledGrantTypes = handledGrantTypes,
        requestedScopes = requestedScopes,
        grantedScopes = grantedScopes,
        requestedAudience = requestedAudience,
        grantedAudience = grantedAudience,
        requestForm = requestForm,
        session = session?.toSession(),
        issClaim = issClaim,
    )

private fun Client.toStoredClient(): StoredClient =
    StoredClient(
        id = id,
        redirectUris = redirectUris,
        grantTypes = grantTypes,
        responseTypes = responseTypes,
        scopes = scopes,
        audience = audience,
    )

private fun StoredClient.toClient(): DefaultClient =
    DefaultClient(
        id = id,
        redirectUris = redirectUris,
        grantTypes = grantTypes,
        responseTypes = responseTypes,
        scopes = scopes,
        audience = audience,
    )

private fun Session.toStoredSession(): StoredSession =
    StoredSession(
        subject = subject,
        expiresAt = expiresAt.mapKeys { it.key.name }.mapValues { it.value.toEpochMilliseconds() },
        customAttributes = customAttributes,
    )

private fun StoredSession.toSession(): DefaultSession =
    DefaultSession(
        subject = subject,
        expiresAt = expiresAt.mapKeys { TokenType.valueOf(it.key) }
            .mapValues { Instant.fromEpochMilliseconds(it.value) },
        customAttributes = customAttributes,
    )

private fun refreshTokenTtlUntil(expiresAt: Instant) =
    ttlUntil(expiresAt).coerceAtLeast(1.seconds)
