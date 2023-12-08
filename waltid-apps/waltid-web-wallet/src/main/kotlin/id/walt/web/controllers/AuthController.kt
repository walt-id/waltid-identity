package id.walt.web.controllers

//import id.walt.web.model.LoginRequestJson
import id.walt.db.models.AccountWalletMappings
import id.walt.db.models.AccountWalletPermissions
import id.walt.service.WalletServiceManager
import id.walt.service.account.AccountsService
import id.walt.utils.RandomUtils
import id.walt.web.ForbiddenException
import id.walt.web.InsufficientPermissionsException
import id.walt.web.UnauthorizedException
import id.walt.web.WebBaseRoutes.webWalletRoute
import id.walt.web.model.AccountRequest
import id.walt.web.model.EmailAccountRequest
import id.walt.web.model.LoginRequestJson
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.uuid.SecureRandom
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.collections.set
import kotlin.time.Duration.Companion.days

private val log = KotlinLogging.logger { }

@Suppress("ArrayInDataClass")
data class ByteLoginRequest(val username: String, val password: ByteArray) {
    constructor(loginRequest: EmailAccountRequest) : this(loginRequest.email, loginRequest.password.toByteArray())

    override fun toString() = "[LOGIN REQUEST FOR: $username]"
}

fun generateToken() = RandomUtils.randomBase64UrlString(256)

data class LoginTokenSession(val token: String) : Principal

object AuthKeys {
    private val secureRandom = SecureRandom

    // TODO make statically configurable for HA deployments
    val encryptionKey = secureRandom.nextBytes(16)
    val signKey = secureRandom.nextBytes(16)
}

fun Application.configureSecurity() {

    install(Sessions) {
        cookie<LoginTokenSession>("login") {
            //cookie.encoding = CookieEncoding.BASE64_ENCODING

            //cookie.httpOnly = true
            cookie.httpOnly = false // FIXME
            // TODO cookie.secure = true
            cookie.maxAge = 1.days
            cookie.extensions["SameSite"] = "Strict"
            transform(SessionTransportTransformerEncrypt(AuthKeys.encryptionKey, AuthKeys.signKey))
        }
    }

    install(Authentication) {

        bearer("authenticated-bearer") {
            authenticate { tokenCredential ->
                if (securityUserTokenMapping.contains(tokenCredential.token)) {
                    UserIdPrincipal(securityUserTokenMapping[tokenCredential.token].toString())
                } else {
                    null
                }
            }
        }

        session<LoginTokenSession>("authenticated-session") {
            validate { session ->
                if (securityUserTokenMapping.contains(session.token)) {
                    UserIdPrincipal(securityUserTokenMapping[session.token].toString())
                } else {
                    sessions.clear("login")
                    null
                }
            }

            challenge {
                call.respond(
                    HttpStatusCode.Unauthorized, JsonObject(
                        mapOf(
                            "message" to JsonPrimitive("Login Required")
                        )
                    )
                )
            }
        }
    }
}


val securityUserTokenMapping = HashMap<String, UUID>() // Token -> UUID


fun Application.auth() {
    webWalletRoute {
        route("auth", {
            tags = listOf("Authentication")
        }) {
            post("login", {
                summary = "Login with [email + password] or [wallet address + ecosystem]"
                request {
                    body<EmailAccountRequest> {
                        example("E-mail + password", buildJsonObject {
                            put("email", JsonPrimitive("user@email.com"))
                            put("password", JsonPrimitive("password"))
                            put("type", JsonPrimitive("email"))
                        }.toString())
                        example("Wallet address + ecosystem", buildJsonObject {
                            put("address", JsonPrimitive("0xABC"))
                            put("ecosystem", JsonPrimitive("ecosystem"))
                            put("type", JsonPrimitive("address"))
                        }.toString())
                    }
                }
                response {
                    HttpStatusCode.OK to { description = "Login successful" }
                    HttpStatusCode.Unauthorized to { description = "Login failed" }
                    HttpStatusCode.BadRequest to { description = "Login failed" }
                }
            }) {
                println("Login request")
                val reqBody = LoginRequestJson.decodeFromString<AccountRequest>(call.receive())
                AccountsService.authenticate(reqBody).onSuccess {
                    securityUserTokenMapping[it.token] = it.id
                    call.sessions.set(LoginTokenSession(it.token))
                    call.response.status(HttpStatusCode.OK)
                    call.respond(
                        mapOf(
                            "token" to it.token,
                            "id" to it.id.toString(),
                            "username" to it.username
                        )
                    )
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
                }
            }

            post("create", {
                summary = "Register with [email + password] or [wallet address + ecosystem]"
                request {
                    body<EmailAccountRequest> {
                        example("E-mail + password", buildJsonObject {
                            put("name", JsonPrimitive("Max Mustermann"))
                            put("email", JsonPrimitive("user@email.com"))
                            put("password", JsonPrimitive("password"))
                            put("type", JsonPrimitive("email"))
                        }.toString())
                        example("Wallet address + ecosystem", buildJsonObject {
                            put("address", JsonPrimitive("0xABC"))
                            put("ecosystem", JsonPrimitive("ecosystem"))
                            put("type", JsonPrimitive("address"))
                        }.toString())
                    }
                }
                response {
                    HttpStatusCode.Created to { description = "Register successful" }
                    HttpStatusCode.BadRequest to { description = "Register failed" }
                }
            }) {
                val req = LoginRequestJson.decodeFromString<AccountRequest>(call.receive())
                AccountsService.register(req).onSuccess {
                    println("Registration succeed.")
                    call.response.status(HttpStatusCode.Created)
                    call.respond("Registration succeed.")
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
                }
            }

            authenticate("authenticated-session", "authenticated-bearer") {
                get("user-info", {
                    summary = "Return user ID if logged in"
                }) {
                    call.respond(getUserId().name)
                }
                get("session", {
                    summary = "Return session ID if logged in"
                }) {
                    //val token = getUserId().name
                    val token = getUsersSessionToken() ?: throw UnauthorizedException("Invalid session")

                    if (securityUserTokenMapping.contains(token))
                        call.respond(mapOf("token" to mapOf("accessToken" to token)))
                    else throw UnauthorizedException("Invalid (outdated?) session!")
                }
            }

            post("logout", {
                summary = "Logout (delete session)"
                response { HttpStatusCode.OK to { description = "Logged out." } }
            }) {
                val token = getUsersSessionToken()

                securityUserTokenMapping.remove(token)

                call.sessions.clear<LoginTokenSession>()
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}

fun PipelineContext<Unit, ApplicationCall>.getUserId() =
    call.principal<UserIdPrincipal>("authenticated-session")
        ?: call.principal<UserIdPrincipal>("authenticated-bearer")
        ?: call.principal<UserIdPrincipal>() // bearer is registered with no name for some reason
        ?: throw UnauthorizedException("Could not find user authorization within request.")

fun PipelineContext<Unit, ApplicationCall>.getUserUUID() =
    runCatching { UUID(getUserId().name) }.getOrElse { throw IllegalArgumentException("Invalid user id: $it") }

fun PipelineContext<Unit, ApplicationCall>.getWalletId() =
    runCatching {
        UUID(call.parameters["wallet"] ?: throw IllegalArgumentException("No wallet ID provided"))
    }.getOrElse { throw IllegalArgumentException("Invalid wallet ID provided") }

fun PipelineContext<Unit, ApplicationCall>.getWalletService(walletId: UUID) =
    WalletServiceManager.getWalletService(getUserUUID(), walletId)

fun PipelineContext<Unit, ApplicationCall>.getWalletService() =
    WalletServiceManager.getWalletService(getUserUUID(), getWalletId())

fun PipelineContext<Unit, ApplicationCall>.getUsersSessionToken(): String? =
    call.sessions.get(LoginTokenSession::class)?.token
        ?: call.request.authorization()?.removePrefix("Bearer ")

fun getNftService() = WalletServiceManager.getNftService()

fun PipelineContext<Unit, ApplicationCall>.ensurePermissionsForWallet(required: AccountWalletPermissions): Boolean {
    val userId = getUserUUID()
    val walletId = getWalletId()

    val permissions = transaction {
        (AccountWalletMappings.select { (AccountWalletMappings.account eq userId) and (AccountWalletMappings.wallet eq walletId) }
            .firstOrNull()
            ?: throw ForbiddenException("This account does not have access to the specified wallet.")
                )[AccountWalletMappings.permissions]
    }

    if (permissions.power >= required.power) {
        return true
    } else {
        throw InsufficientPermissionsException(
            minimumRequired = required,
            current = permissions
        )
    }
}
