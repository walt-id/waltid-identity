@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.web.controllers.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import id.walt.commons.config.ConfigManager
import id.walt.commons.featureflag.FeatureManager
import id.walt.commons.web.ForbiddenException
import id.walt.commons.web.UnauthorizedException
import id.walt.commons.web.WebException
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.entrawallet.core.utils.UuidSerializer
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.webwallet.FeatureCatalog
import id.walt.webwallet.config.AuthConfig
import id.walt.webwallet.db.models.AccountWalletMappings
import id.walt.webwallet.db.models.AccountWalletPermissions
import id.walt.webwallet.service.WalletServiceManager
import id.walt.webwallet.service.account.*
import id.walt.webwallet.web.InsufficientPermissionsException
import id.walt.webwallet.web.model.AccountRequest
import id.walt.webwallet.web.model.EmailAccountRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.temporal.ChronoUnit
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@Suppress("ArrayInDataClass")
data class ByteLoginRequest(val username: String, val password: ByteArray) {
    constructor(
        loginRequest: EmailAccountRequest,
    ) : this(loginRequest.email, loginRequest.password.toByteArray())

    override fun toString() = "[LOGIN REQUEST FOR: $username]"
}

@Serializable
data class LoginTokenSession(val token: String)

@Serializable
data class OidcTokenSession(val token: String)

/*
Use this data class for login response data, so schema generator can generate
openapi schema from it.
 */
@Serializable
data class LoginResponseData(
    @Serializable(with = UuidSerializer::class) val id: Uuid,
    val token: String,
    val username: String? = null,
    val address: String? = null,
    val keycloakUserId: String? = null,
) {

    companion object {
        fun of(authenticatedUser: AuthenticatedUser, token: String): LoginResponseData {
            return when (authenticatedUser) {
                is UsernameAuthenticatedUser -> LoginResponseData(
                    id = authenticatedUser.id,
                    token = token,
                    username = authenticatedUser.username
                )

                is AddressAuthenticatedUser -> LoginResponseData(
                    id = authenticatedUser.id,
                    token = token,
                    address = authenticatedUser.address
                )

                is KeycloakAuthenticatedUser -> LoginResponseData(
                    id = authenticatedUser.id,
                    token = token,
                    keycloakUserId = authenticatedUser.keycloakUserId
                )

                is X5CAuthenticatedUser -> LoginResponseData(authenticatedUser.id, token)
            }
        }
    }
}

object AuthKeys {
    private val config = ConfigManager.getConfig<AuthConfig>()
    val encryptionKey: ByteArray = config.encryptionKey.encodeToByteArray()
    val signKey: ByteArray = config.signKey.encodeToByteArray()

    val tokenKey: ByteArray = config.tokenKey.encodeToByteArray()
    val issTokenClaim: String = config.issTokenClaim
    val audTokenClaim: String? = config.audTokenClaim
    val tokenLifetime: Long = config.tokenLifetime.toLongOrNull() ?: 1

}

/**
 * @param token JWS token provided by user
 * @return user/account ID if token is valid
 */
suspend fun verifyToken(token: String): Result<String> {
    val jwsObject = JWSObject.parse(token)

    val key = JWKKey.importJWK(AuthKeys.tokenKey.decodeToString()).getOrNull()
    return if (key == null) {
        val verifier = MACVerifier(AuthKeys.tokenKey)
        runCatching { jwsObject.verify(verifier) }
            .mapCatching { valid ->
                if (valid) Json.parseToJsonElement(jwsObject.payload.toString()).jsonObject["sub"]?.jsonPrimitive?.content.toString() else throw IllegalArgumentException(
                    "Token is not valid."
                )
            }
    } else {
        val verified = JWKKey.importJWK(AuthKeys.tokenKey.decodeToString()).getOrThrow().verifyJws(token)
        runCatching { verified }
            .mapCatching {
                if (verified.isSuccess) {
                    Json.parseToJsonElement(jwsObject.payload.toString()).jsonObject["sub"]?.jsonPrimitive?.content.toString()
                } else throw IllegalArgumentException("Token is not valid.")
            }
    }
}

data class LoginRequestError(override val message: String) : WebException(
    message = message,
    status = HttpStatusCode.BadRequest.value
) {
    constructor(throwable: Throwable) : this(
        when (throwable) {
            is BadRequestException -> "Error processing request: ${throwable.localizedMessage ?: "Unknown reason"}"
            is SerializationException -> "Failed to parse JSON string: ${throwable.localizedMessage ?: "Unknown reason"}"
            is IllegalStateException -> "Invalid request: ${throwable.localizedMessage ?: "Unknown reason"}"
            else -> "Unexpected error: ${throwable.message ?: "Unknown reason"}"
        }
    )
}

suspend fun ApplicationCall.getLoginRequest(): AccountRequest = runCatching {
    receive<AccountRequest>()
}.getOrElse { throw LoginRequestError(it) }


suspend fun RoutingContext.doLogin() {
    val reqBody = call.getLoginRequest()
    val authenticatedUser = AccountsService.authenticate("", reqBody).getOrThrow()
    val now = Clock.System.now().toJavaInstant()
    val tokenPayload = Json.encodeToString(
        AuthTokenPayload(
            jti = Uuid.random().toString(),
            sub = authenticatedUser.id.toString(),
            iss = AuthKeys.issTokenClaim,
            aud = AuthKeys.audTokenClaim.takeIf { !it.isNullOrEmpty() }
                ?: let { call.request.headers["Origin"] ?: "n/a" },
            iat = now.epochSecond,
            nbf = now.epochSecond,
            exp = now.plus(AuthKeys.tokenLifetime, ChronoUnit.DAYS).epochSecond,
        )
    )

    val token = JWKKey.importJWK(AuthKeys.tokenKey.decodeToString()).getOrNull()?.let {
        createRsaToken(it, tokenPayload)
    } ?: createHS256Token(tokenPayload)
    call.sessions.set(LoginTokenSession(token))
    call.response.status(HttpStatusCode.OK)
    call.respond(LoginResponseData.of(authenticatedUser, token))
}

fun ApplicationCall.getUserId() =
    principal<UserIdPrincipal>("auth-session")
        ?: principal<UserIdPrincipal>("auth-bearer")
        ?: principal<UserIdPrincipal>("auth-bearer-alternative")
        ?: principal<UserIdPrincipal>() // bearer is registered with no name for some reason
        ?: throw UnauthorizedException("Could not find user authorization within request.")

@OptIn(ExperimentalUuidApi::class)
suspend fun ApplicationCall.getUserUUID() =
    runCatching {
        when {
            FeatureManager.isFeatureEnabled(FeatureCatalog.legacyAuthenticationFeature) -> Uuid.parse(getUserId().name)
            FeatureManager.isFeatureEnabled(FeatureCatalog.ktorAuthnzAuthenticationFeature) -> Uuid.parse(getAuthenticatedAccount())
            else -> error("No authentication feature enabled")
        }
    }.getOrElse { throw IllegalArgumentException("Invalid user id: $it") }

@OptIn(ExperimentalUuidApi::class)
fun ApplicationCall.getWalletId() =
    runCatching {
        Uuid.parse(parameters["wallet"] ?: throw IllegalArgumentException("No wallet ID provided"))
    }.getOrElse { throw IllegalArgumentException("Invalid wallet ID provided: ${it.message}") }
        .also {
            ensurePermissionsForWallet(AccountWalletPermissions.READ_ONLY, walletId = it)
        }

@OptIn(ExperimentalUuidApi::class)
suspend fun ApplicationCall.getWalletService(walletId: Uuid? = null) =
    WalletServiceManager.getWalletService("", getUserUUID(), walletId ?: getWalletId()) // FIXME -> TENANT HERE

fun ApplicationCall.getUsersSessionToken(): String? =
    sessions.get(LoginTokenSession::class)?.token
        ?: request.authorization()?.removePrefix("Bearer ")

fun ApplicationCall.ensurePermissionsForWallet(
    required: AccountWalletPermissions,

    userId: Uuid = runBlocking { getUserUUID() },
    walletId: Uuid = getWalletId(),
): Boolean {


    val permissions = transaction {
        (AccountWalletMappings.selectAll()
            .where {
                (AccountWalletMappings.tenant eq "") and // FIXME -> TENANT HERE
                        (AccountWalletMappings.accountId eq userId) and
                        (AccountWalletMappings.wallet eq walletId.toJavaUuid())
            }
            .firstOrNull()
            ?: throw ForbiddenException("This account does not have access to the specified wallet."))[
            AccountWalletMappings.permissions]
    }

    if (permissions.power >= required.power) {
        return true
    } else {
        throw InsufficientPermissionsException(minimumRequired = required, current = permissions)
    }
}

private fun createHS256Token(tokenPayload: String) =
    JWSObject(JWSHeader(JWSAlgorithm.HS256), Payload(tokenPayload)).apply {
        sign(MACSigner(AuthKeys.tokenKey))
    }.serialize()

private suspend fun createRsaToken(key: JWKKey, tokenPayload: String) =
    mapOf(
        JWTClaims.Header.keyID to key.getPublicKey().getKeyId().toJsonElement(),
        JWTClaims.Header.type to "JWT".toJsonElement()
    ).let {
        key.signJws(tokenPayload.toByteArray(), it)
    }


@Serializable
private data class AuthTokenPayload<T>(
    val nbf: Long,
    val exp: Long,
    val iat: Long,
    val jti: String,
    val iss: String,
    val aud: String,
    val sub: T,
)
