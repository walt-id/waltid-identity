package id.walt.ktorauthnz.examples.services

import java.net.InetAddress
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

/** Resources acquired by one test, closed in reverse order even after partial setup. */
internal class ExampleServiceScope : AutoCloseable {
    private val resources = ArrayDeque<AutoCloseable>()

    fun <T : AutoCloseable> own(resource: T): T = resource.also { resources.addFirst(it) }

    fun onClose(action: () -> Unit) { own(AutoCloseable(action)) }

    fun setUp(action: () -> Unit) {
        try {
            action()
        } catch (failure: Throwable) {
            close(failure)
            throw failure
        }
    }

    override fun close() { close(null)?.let { throw it } }

    private fun close(primary: Throwable?): Throwable? {
        var failure = primary
        while (resources.isNotEmpty()) {
            try {
                resources.removeFirst().close()
            } catch (cleanup: Throwable) {
                if (failure == null) failure = cleanup
                else if (failure !== cleanup) failure.addSuppressed(cleanup)
            }
        }
        return failure
    }
}

/** The handle exists before startup, so an unsuccessful start can still be stopped. */
internal fun EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>.startAndGetPort(): Int =
    try {
        start(wait = false)
        runBlocking { engine.resolvedConnectors().single().port }
    } catch (failure: Throwable) {
        runCatching { stop(0, 1_000) }.exceptionOrNull()?.let(failure::addSuppressed)
        throw failure
    }

/** An OpenID provider: discovery, token (an ID token for [subject]), userinfo and JWKS. */
class ExampleIdentityProvider(private val clientId: String) : AutoCloseable {
    var subject = "idp-user-1"
    var nonce = ""
    val issuer: String get() = "http://127.0.0.1:$port"
    val discoveryUrl: String get() = "$issuer/.well-known/openid-configuration"

    /** Requests for the provider's keys, to see how often they are fetched. */
    val jwksRequests = java.util.concurrent.atomic.AtomicInteger()

    private var kid = "idp-key"
    private var key = newKey()
    private var publicJwk = publicJwkOf(key)
    /** The key id ID tokens name; null names the current key. */
    var signedKid: String? = null

    private fun newKey() = runBlocking {
        CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(KeyId(kid), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        )
    }
    private fun publicJwkOf(key: id.walt.crypto2.keys.Key) = runBlocking {
        Json.parseToJsonElement(key.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(key.spec).data.toByteArray().decodeToString())
            .jsonObject.let { JsonObject(it + ("kid" to JsonPrimitive(kid))) }
    }

    /** Signs with a new key under [newKid] from now on, and publishes only that one. */
    fun rotateKey(newKid: String) {
        kid = newKid
        key = newKey()
        publicJwk = publicJwkOf(key)
    }

    private val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
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
                    protectedHeader = buildJsonObject { put("kid", signedKid ?: kid) },
                )
                call.respond(buildJsonObject { put("id_token", idToken); put("access_token", "at"); put("token_type", "Bearer") })
            }
            get("/userinfo") { call.respond(buildJsonObject { put("sub", subject) }) }
            get("/jwks") {
                jwksRequests.incrementAndGet()
                call.respond(buildJsonObject { putJsonArray("keys") { add(publicJwk) } })
            }
        }
    }
    private val port: Int = server.startAndGetPort()

    override fun close() = server.stop(0, 1_000)
}

/** A verifier2 with one verification session, in which the wallet presents a credential of [holder]. */
class ExampleVerifier : AutoCloseable {
    var holder = "did:key:z6Mk-holder"
    var presented = false
    val url: String get() = "http://127.0.0.1:$port"

    private val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
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
    }
    private val port: Int = server.startAndGetPort()

    override fun close() = server.stop(0, 1_000)
}

/**
 * A directory under `dc=<[domain]>`: people at `<[nameAttribute]>=<name>,ou=people,dc=<domain>`, with [users] and their
 * passwords.
 */
class ExampleDirectory(users: Map<String, String>, domain: String = "org3", nameAttribute: String = "uid") : AutoCloseable {
    private val server = InMemoryDirectoryServer(InMemoryDirectoryServerConfig("dc=$domain").apply {
        setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("ldap", InetAddress.getByName("127.0.0.1"), 0, null))
    }).also { server ->
        try {
            server.startListening()
            server.add("dn: dc=$domain", "objectClass: domain", "dc: $domain")
            server.add("dn: ou=people,dc=$domain", "objectClass: organizationalUnit", "ou: people")
            users.forEach { (name, password) ->
                server.add("dn: $nameAttribute=$name,ou=people,dc=$domain", "objectClass: inetOrgPerson", "$nameAttribute: $name", "cn: $name", "sn: $name", "userPassword: $password")
            }
        } catch (failure: Throwable) {
            runCatching { server.shutDown(true) }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }
    val url = "ldap://127.0.0.1:${server.listenPort}"

    override fun close() = server.shutDown(true)
}
