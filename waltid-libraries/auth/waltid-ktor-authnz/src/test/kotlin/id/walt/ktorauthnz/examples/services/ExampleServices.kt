package id.walt.ktorauthnz.examples.services

import com.unboundid.ldap.listener.InMemoryDirectoryServer
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig
import com.unboundid.ldap.listener.InMemoryListenerConfig
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.time.Clock

/* Stand-ins for the services the example tenants log in with. */

private fun freePort() = java.net.ServerSocket(0).use { it.localPort }

/** An OpenID provider: discovery, token (an ID token for [subject]), userinfo and JWKS. */
class ExampleIdentityProvider(private val clientId: String) : AutoCloseable {
    var subject = "idp-user-1"
    var nonce = ""
    private val port = freePort()
    val issuer = "http://127.0.0.1:$port"
    val discoveryUrl = "$issuer/.well-known/openid-configuration"

    private val key = runBlocking {
        CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(KeyId("idp-key"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        )
    }
    private val publicJwk = runBlocking {
        Json.parseToJsonElement(key.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(key.spec).data.toByteArray().decodeToString())
            .jsonObject.let { JsonObject(it + ("kid" to JsonPrimitive("idp-key"))) }
    }

    private val server = embeddedServer(CIO, port = port) {
        install(ContentNegotiation) { json() }
        routing {
            get("/.well-known/openid-configuration") {
                call.respond(buildJsonObject {
                    put("issuer", issuer)
                    put("authorization_endpoint", "$issuer/authorize")
                    put("token_endpoint", "$issuer/token")
                    put("userinfo_endpoint", "$issuer/userinfo")
                    put("jwks_uri", "$issuer/jwks")
                    putJsonArray("id_token_signing_alg_values_supported") { add("ES256") }
                })
            }
            post("/token") {
                val now = Clock.System.now().epochSeconds
                val idToken = CompactJws.sign(
                    payload = buildJsonObject {
                        put("iss", issuer); put("aud", clientId); put("sub", subject); put("nonce", nonce)
                        put("iat", now); put("exp", now + 300)
                    }.toString().encodeToByteArray(),
                    key = key,
                    algorithm = JwsAlgorithm.ES256,
                    protectedHeader = buildJsonObject { put("kid", "idp-key") },
                )
                call.respond(buildJsonObject { put("id_token", idToken); put("access_token", "at"); put("token_type", "Bearer") })
            }
            get("/userinfo") { call.respond(buildJsonObject { put("sub", subject) }) }
            get("/jwks") { call.respond(buildJsonObject { putJsonArray("keys") { add(publicJwk) } }) }
        }
    }.start(wait = false)

    override fun close() = server.stop()
}

/** A verifier2 with one verification session, in which the wallet presents a credential of [holder]. */
class ExampleVerifier : AutoCloseable {
    var holder = "did:key:z6Mk-holder"
    var presented = false
    private val port = freePort()
    val url = "http://127.0.0.1:$port"

    private val server = embeddedServer(CIO, port = port) {
        install(ContentNegotiation) { json() }
        routing {
            post("/verification-session/create") {
                call.receive<JsonObject>()
                call.respond(buildJsonObject {
                    put("sessionId", "verification-1")
                    put("bootstrapAuthorizationRequestUrl", "openid4vp://authorize?request_uri=https%3A%2F%2Fverifier%2Frequest")
                })
            }
            get("/verification-session/verification-1/info") {
                call.respond(buildJsonObject {
                    put("status", if (presented) "SUCCESSFUL" else "IN_USE")
                    putJsonObject("presented_credentials") {
                        putJsonArray("employee") {
                            addJsonObject {
                                putJsonObject("credentialData") { putJsonObject("credentialSubject") { put("id", holder) } }
                            }
                        }
                    }
                })
            }
        }
    }.start(wait = false)

    override fun close() = server.stop()
}

/**
 * A directory under `dc=<[domain]>`: people at `<[nameAttribute]>=<name>,ou=people,dc=<domain>`, with [users] and their
 * passwords.
 */
class ExampleDirectory(users: Map<String, String>, domain: String = "org3", nameAttribute: String = "uid") : AutoCloseable {
    private val server = InMemoryDirectoryServer(InMemoryDirectoryServerConfig("dc=$domain").apply {
        setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("ldap", 0))
    }).apply {
        startListening()
        add("dn: dc=$domain", "objectClass: domain", "dc: $domain")
        add("dn: ou=people,dc=$domain", "objectClass: organizationalUnit", "ou: people")
        users.forEach { (name, password) ->
            add("dn: $nameAttribute=$name,ou=people,dc=$domain", "objectClass: inetOrgPerson", "$nameAttribute: $name", "cn: $name", "sn: $name", "userPassword: $password")
        }
    }
    val url = "ldap://127.0.0.1:${server.listenPort}"

    override fun close() = server.shutDown(true)
}
