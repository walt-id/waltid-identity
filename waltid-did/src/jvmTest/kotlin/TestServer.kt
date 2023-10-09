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

object TestServer {
    private val keyStoreFile = File("src/jvmTest/resources/keystore.jks")
    private val keyStore = buildKeyStore {
        certificate("test") {
            password = "test123"
            domains = listOf("localhost", "127.0.0.1", "0.0.0.0")
        }
    }

    private val environment = applicationEngineEnvironment {
        envConfig()
    }
    private val ed25519DocumentResponse = File("src/jvmTest/resources/did-doc/ed25519.json").readText()
    private val secp256k1DocumentResponse = File("src/jvmTest/resources/did-doc/secp256k1.json").readText()
    private val secp256r1DocumentResponse = File("src/jvmTest/resources/did-doc/secp256r1.json").readText()
    private val rsaDocumentResponse = File("src/jvmTest/resources/did-doc/rsa.json").readText()

    val server = embeddedServer(Netty, environment) {}

    init {
        keyStore.saveToFile(keyStoreFile, "test123")
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
            port = 8080
        }
//        sslConnector(
//            keyStore = keyStore,
//            keyAlias = "test",
//            keyStorePassword = { "test123".toCharArray() },
//            privateKeyPassword = { "test123".toCharArray() }) {
//            port = 8443
//            keyStorePath = keyStoreFile
//        }
    }
}