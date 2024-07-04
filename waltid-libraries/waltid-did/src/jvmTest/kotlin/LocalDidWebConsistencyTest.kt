import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.registrar.dids.DidWebCreateOptions
import id.walt.did.dids.registrar.local.web.DidWebRegistrar
import id.walt.did.dids.resolver.local.DidWebResolver
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.*
import io.ktor.network.tls.*
import io.ktor.network.tls.certificates.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.File
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalDidWebConsistencyTest {

    private val localResolver = DidWebResolver(DidWebTestHttpClient.http)
    private val localRegistrar = DidWebRegistrar()

    private data class TestEntry(
        val did: String,
        val key: Key,
        val doc: DidDocument,
        val domain: String,
        val path: String,
    )

    private val didWebTestEntryList: List<TestEntry>
    private val didWebPathToDocMap = mutableMapOf<String, DidDocument>()
    private val didWebTestServer: NettyApplicationEngine

    private val keyStoreFile = File(this.javaClass.classLoader.getResource("")!!.path.plus("keystore.jks"))
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

    init {
        didWebTestEntryList = populateTestData()
        didWebTestServer = embeddedServer(
            Netty,
            environment,
        ).start(false)
    }

    @AfterTest
    fun cleanUp() {
        didWebTestServer.stop()
    }

    @Test
    fun testDidWebCreateAndResolveConsistency() = runTest {
        for (entry in didWebTestEntryList) {
            val resolvedKey = localResolver.resolveToKey(entry.did).getOrThrow()
            assertEquals(entry.key.getThumbprint(), resolvedKey.getThumbprint())
        }
    }

    private fun populateTestData(): List<TestEntry> = runBlocking {
        val keyList: List<JWKKey> = KeyType.entries.map { JWKKey.generate(it) }
        val domain = "localhost:3021"
        keyList.map {
            val path = it.keyType.toString().lowercase()
            val didResult = localRegistrar.registerByKey(
                it,
                DidWebCreateOptions(
                    domain = domain,
                    path = path,
                    it.keyType
                )
            )
            didWebPathToDocMap[path] = didResult.didDocument
            TestEntry(
                didResult.did,
                it,
                didResult.didDocument,
                domain,
                path,
            )
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
            port = 3021
            keyStorePath = keyStoreFile
        }
    }

    private fun Application.module() {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        routing {
            get("/{path}/did.json") {
                if (call.parameters["path"] != null && call.parameters["path"] != "") {
                    val doc = didWebPathToDocMap[call.parameters["path"]]
                    call.respondText(doc!!.toJsonObject().toString(), ContentType.Application.Json)
                }
                call.response.status(HttpStatusCode.NotFound)
            }
        }
    }

    private object DidWebTestHttpClient {

        val http = HttpClient(CIO) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(DidWebResolver.json)
            }
            engine {
                https {
                    trustManager = TrustAllManager(this)
                }
            }
        }

        private class TrustAllManager(config: TLSConfigBuilder) : X509TrustManager {
            private val delegate = config.build().trustManager
            override fun checkClientTrusted(certificates: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(certificates: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
        }
    }

}

