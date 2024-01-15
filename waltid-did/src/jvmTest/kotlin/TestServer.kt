import io.ktor.network.tls.certificates.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import javax.security.auth.x500.X500Principal

object TestServer {
    private val keyStoreFile = File(this.javaClass.classLoader.getResource("")!!.path.plus("keystore.jks"))
    private val ed25519DocumentResponse =
        this.javaClass.classLoader.getResource("did-doc/ed25519.json")!!.path.let { File(it).readText() }
    private val secp256k1DocumentResponse =
        this.javaClass.classLoader.getResource("did-doc/secp256k1.json")!!.path.let { File(it).readText() }
    private val secp256r1DocumentResponse =
        this.javaClass.classLoader.getResource("did-doc/secp256r1.json")!!.path.let { File(it).readText() }
    private val rsaDocumentResponse =
        this.javaClass.classLoader.getResource("did-doc/rsa.json")!!.path.let { File(it).readText() }
    private val keyStore = buildKeyStore {
        certificate("test") {
            password = "test123"
            domains = listOf("localhost", "127.0.0.1", "0.0.0.0")
            subject = X500Principal("CN=localhost, OU=walt.id, O=walt.id, C=AT")
        }
    }.also { it.saveToFile(keyStoreFile, "test123") }
    private val environment = applicationEngineEnvironment {
        envConfig()
    }
    val server: ApplicationEngine by lazy {
        println("Initializing embedded webserver...")
        embeddedServer(Netty, environment)
    }

    private fun Application.module() {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        routing {
            get("/secp256k1/did.json") {
                call.respond<JsonObject>(Json.decodeFromString<JsonObject>(secp256k1DocumentResponse))
            }
            get("/secp256r1/did.json") {
                call.respond<JsonObject>(Json.decodeFromString<JsonObject>(secp256r1DocumentResponse))
            }
            get("/ed25519/did.json") {
                call.respond<JsonObject>(Json.decodeFromString<JsonObject>(ed25519DocumentResponse))
            }
            get("/rsa/did.json") {
                call.respond<JsonObject>(Json.decodeFromString<JsonObject>(rsaDocumentResponse))
            }
        }
    }

    private fun ApplicationEngineEnvironmentBuilder.envConfig() {
        module {
            module()
        }
        connector {
            port = 8000
        }
        sslConnector(
            keyStore = keyStore,
            keyAlias = "test",
            keyStorePassword = { "test123".toCharArray() },
            privateKeyPassword = { "test123".toCharArray() }) {
            port = 8080
            keyStorePath = keyStoreFile
        }
    }

}
