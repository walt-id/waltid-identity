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
import java.net.URI
import javax.security.auth.x500.X500Principal

object TestServer {

    private const val DID_WEB_PORT = 10888
    const val DID_WEB_SSL_PORT = 10443

    private const val DID_WEB_PORT_PLACEHOLDER = "<DID_WEB_PORT>"

    private val keyStoreFile = File(this.javaClass.classLoader.getResource("")!!.path.plus("keystore.jks"))

    private fun loadDidWebReferenceFile(fileName: String): String =
        URI(this.javaClass.classLoader.getResource(fileName)!!.toString()).path.let {
            File(it).readText().replace(DID_WEB_PORT_PLACEHOLDER, DID_WEB_SSL_PORT.toString())
        }

    private val multiKeyDocumentResponse =
        URI(this.javaClass.classLoader.getResource("did-web/multi-key.json")!!.toString()).path.let { File(it).readText() }
    private val keyStore = buildKeyStore {
        certificate("test") {
            password = "test123"
            domains = listOf("localhost", "127.0.0.1", "0.0.0.0")
            subject = X500Principal("CN=localhost, OU=walt.id, O=walt.id, C=AT")
        }
    }.also { it.saveToFile(keyStoreFile, "test123") }

    val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> by lazy {
        println("Initializing embedded webserver...")
        embeddedServer(Netty, applicationEnvironment(), { envConfig() }, module = { module() })
    }

    private fun Application.module() {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        routing {
            get("/secp256k1/did.json") {
                call.respond<JsonObject>(Json.decodeFromString<JsonObject>(loadDidWebReferenceFile("did-web/secp256k1.json")))
            }
            get("/secp256r1/did.json") {
                call.respond<JsonObject>(Json.decodeFromString<JsonObject>(loadDidWebReferenceFile("did-web/secp256r1.json")))
            }
            get("/ed25519/did.json") {
                call.respond<JsonObject>(Json.decodeFromString<JsonObject>(loadDidWebReferenceFile("did-web/ed25519.json")))
            }
            get("/rsa/did.json") {
                call.respond<JsonObject>(Json.decodeFromString<JsonObject>(loadDidWebReferenceFile("did-web/rsa.json")))
            }
            get("/multi-key/did.json") {
                call.respond<JsonObject>(Json.decodeFromString<JsonObject>(multiKeyDocumentResponse))
            }
        }
    }

    private fun ApplicationEngine.Configuration.envConfig() {
        connector {
            port = DID_WEB_PORT
        }
        sslConnector(
            keyStore = keyStore,
            keyAlias = "test",
            keyStorePassword = { "test123".toCharArray() },
            privateKeyPassword = { "test123".toCharArray() }) {
            port = DID_WEB_SSL_PORT
            keyStorePath = keyStoreFile
        }
    }
}
