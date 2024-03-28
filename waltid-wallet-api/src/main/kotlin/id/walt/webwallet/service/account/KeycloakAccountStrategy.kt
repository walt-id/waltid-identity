package id.walt.webwallet.service.account

import com.auth0.jwk.Jwk
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.webwallet.db.models.Accounts
import id.walt.webwallet.db.models.OidcLogins
import id.walt.webwallet.service.OidcLoginService
import id.walt.webwallet.service.WalletServiceManager.oidcConfig
import id.walt.webwallet.web.controllers.ByteLoginRequest
import id.walt.webwallet.web.model.KeycloakAccountRequest
import id.walt.webwallet.web.model.KeycloakLogoutRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey

object KeycloakAccountStrategy : PasswordAccountStrategy<KeycloakAccountRequest>() {
    val http = HttpClient {
        install(ContentNegotiation) { json() }
        defaultRequest { header(HttpHeaders.ContentType, ContentType.Application.Json) }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
    }

    override suspend fun register(
        tenant: String,
        request: KeycloakAccountRequest
    ): Result<RegistrationResult> {

        val user =
            mapOf(
                "username" to request.username,
                "email" to request.email,
                "enabled" to true,
                when (request.password) {
                    null -> "credentials" to null
                    else ->
                        "credentials" to
                                listOf(mapOf("type" to "password", "value" to request.password))
                }
            )
                .toJsonObject()

        val res =
            http.post(oidcConfig.keycloakUserApi) {
                contentType(ContentType.Application.Json)
                headers {
                    append("Content-Type", "application/json")
                    append("Authorization", "Bearer ${request.token}")
                }
                setBody(user)
            }

        if (res.status != HttpStatusCode.Created) {
            throw RuntimeException(
                "Keycloak returned error code: ${res.status} [${res.status.description}]"
            )
        }

        val oidcAccountId = res.headers["Location"]?.split("/")?.last() ?: throw RuntimeException(
            "Missing header-parameter 'Location' when creating user ${request.username} at the Keycloak user API ${oidcConfig.keycloakUserApi}"
        )

        val hash = request.password?.let {
            hashPassword(
                ByteLoginRequest(
                    request.username!!, it.toByteArray()
                ).password
            )
        }
        val createdAccountId = transaction {
            val accountId = Accounts.insert {
                it[Accounts.tenant] = tenant
                it[id] = UUID.generateUUID()
                it[name] = request.username
                it[email] = request.email
                it[password] = hash
                it[createdOn] = Clock.System.now().toJavaInstant()
            }[Accounts.id]

            OidcLogins.insert {
                it[OidcLogins.tenant] = tenant
                it[OidcLogins.accountId] = accountId
                it[oidcId] = oidcAccountId
            }

            accountId
        }

        return Result.success(RegistrationResult(createdAccountId))
    }

    private fun verifiedToken(token: String): DecodedJWT {
        val decoded = JWT.decode(token)

        val verifier =
            JWT.require(OidcLoginService.jwkProvider.get(decoded.keyId).makeAlgorithm())
                .withIssuer(OidcLoginService.oidcRealm)
                .build()

        return verifier.verify(decoded)!!
    }

    private fun Jwk.makeAlgorithm(): Algorithm =
        when (algorithm) {
            "RS256" -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
            "RS384" -> Algorithm.RSA384(publicKey as RSAPublicKey, null)
            "RS512" -> Algorithm.RSA512(publicKey as RSAPublicKey, null)
            "ES256" -> Algorithm.ECDSA256(publicKey as ECPublicKey, null)
            "ES384" -> Algorithm.ECDSA384(publicKey as ECPublicKey, null)
            "ES512" -> Algorithm.ECDSA512(publicKey as ECPublicKey, null)
            null -> Algorithm.RSA256(publicKey as RSAPublicKey, null)
            else -> throw IllegalArgumentException("Unsupported algorithm $algorithm")
        }

    override suspend fun authenticate(
        tenant: String,
        request: KeycloakAccountRequest
    ): AuthenticatedUser {

        val token =
            when {
                request.username != null && request.password != null -> {

                    getUserToken(request)
                }

                request.token != null && request.password == null && request.username != null -> {

                    getTokenExchange(request)
                }

                request.token != null && request.password == null && request.username == null -> {

                    request.token
                }

                else -> throw RuntimeException("Invalid request")
            }

        val jwt = verifiedToken(token)

        println("JWT: ${jwt.subject}")

        val registeredUserId =
            if (AccountsService.hasAccountOidcId(jwt.subject)) {
                AccountsService.getAccountByOidcId(jwt.subject)!!.id
            } else {
                AccountsService.register(tenant, request).getOrThrow().id
            }
        // TODO: change id to wallet-id (also in the frontend)
        return KeycloakAuthenticatedUser(registeredUserId, jwt.subject)
    }

    suspend fun getAccessToken(): String {
        return getToken("client_credentials")
    }

    private suspend fun getUserToken(request: KeycloakAccountRequest): String {
        return getToken("password", request.username, request.password)
    }

    private suspend fun getTokenExchange(request: KeycloakAccountRequest): String {
        val requestParams =
            mapOf(
                "client_id" to oidcConfig.clientId,
                "client_secret" to oidcConfig.clientSecret,
                "grant_type" to "urn:ietf:params:oauth:grant-type:token-exchange",
                "subject_token" to request.token,
                "subject_token_type" to "urn:ietf:params:oauth:token-type:access_token",
                "requested_subject" to request.username
            )

        val requestBody = requestParams.map { (k, v) -> "$k=$v" }.joinToString("&")
        val res =
            http.post(oidcConfig.accessTokenUrl) {
                headers { append("Content-Type", "application/x-www-form-urlencoded") }
                setBody(requestBody)
            }

        if (res.status != HttpStatusCode.OK) {
            throw RuntimeException(
                "Keycloak returned error code: ${res.status} [${res.status.description}]"
            )
        }

        val resBody = Json.parseToJsonElement(res.body())

        return resBody.jsonObject["access_token"]?.jsonPrimitive?.content
            ?: throw RuntimeException("Keycloak has not returned the required access_token ")
    }

    private suspend fun getToken(
        grantType: String,
        username: String? = null,
        password: String? = null
    ): String {
        val requestParams =
            mutableMapOf(
                "client_id" to oidcConfig.clientId,
                "client_secret" to oidcConfig.clientSecret,
                "grant_type" to grantType
            )

        if (grantType == "password") {
            require(username != null && password != null) {
                "Username and password are required for password grant type"
            }
            requestParams["username"] = username
            requestParams["password"] = password
        }

        val requestBody = requestParams.map { (k, v) -> "$k=$v" }.joinToString("&")
        val res =
            http.post(oidcConfig.accessTokenUrl) {
                headers { append("Content-Type", "application/x-www-form-urlencoded") }
                setBody(requestBody)
            }

        if (res.status != HttpStatusCode.OK) {
            throw RuntimeException(
                "Keycloak returned error code: ${res.status} [${res.status.description}]"
            )
        }

        val resBody = Json.parseToJsonElement(res.body())

        return resBody.jsonObject["access_token"]?.jsonPrimitive?.content
            ?: throw RuntimeException("Keycloak has not returned the required access_token ")
    }

    suspend fun logout(request: KeycloakLogoutRequest): String {

        val requestParams = mapOf("id" to request.keycloakUserId).toJsonObject()

        val requestBody = requestParams.map { (k, v) -> "$k=$v" }.joinToString("&")

        val res =
            http.post(oidcConfig.keycloakUserApi + "/" + request.keycloakUserId + "/logout") {
                contentType(ContentType.Application.Json)
                headers {
                    append("Content-Type", "application/json")
                    append("Authorization", "Bearer ${request.token}")
                }
                setBody(requestBody)
            }

        return res.status.toString()
    }
}
