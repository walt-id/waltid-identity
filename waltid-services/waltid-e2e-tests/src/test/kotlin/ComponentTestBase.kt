import extensions.service.IssuerServiceExtension
import extensions.service.VerifierServiceExtension
import extensions.service.WalletServiceExtension
import id.walt.commons.web.plugins.httpJson
import id.walt.crypto.utils.JsonUtils.toJsonElement
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File
import java.net.URLDecoder
import kotlin.test.Test


//@ExtendWith(StatsExtension::class)
//@ExtendWith(IssuerServiceExtension::class)
//@ExtendWith(VerifierServiceExtension::class)
@ExtendWith(WalletServiceExtension::class)
class ComponentTestBase {

    @Test
    fun testTest() = runTest {
        val client = testHttpClient()
//        val issuer = client.post("http://127.0.0.1:7002/onboard/issuer") {
//            contentType(ContentType.Application.Json)
//            setBody(buildJsonObject {
//                put("key", buildJsonObject {
//                    put("backend", "jwk")
//                    put("keyType", "Ed25519")
//                })
//                put("did", buildJsonObject {
//                    put("method", "key")
//                })
//            })
//        }.bodyAsText()
//        println(issuer)
//        val verifier = client.post("http://127.0.0.1:7002/openid4vc/verify") {
//            contentType(ContentType.Application.Json)
//            setBody(buildJsonObject {
//                put("request_credentials", buildJsonArray {
//                    add("OpenBadgeCredential".toJsonElement())
//                })
//            })
//        }.bodyAsText()
//        println(verifier)
        val wallet = client.post("http://127.0.0.1:7002/wallet-api/auth/login"){
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("type", "email")
                put("email", "user@email.com")
                put("password", "password")
            })
        }.bodyAsText()
        println(wallet)
    }

    protected fun testHttpClient(token: String? = null) = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(httpJson)
        }
        install(DefaultRequest) {
            contentType(ContentType.Application.Json)
//            host = "127.0.0.1"
//            port = 3000

            if (token != null) bearerAuth(token)
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }

    protected fun loadResource(relativePath: String): String =
        URLDecoder.decode(this.javaClass.getResource(relativePath)!!.path, "UTF-8").let { File(it).readText() }
}