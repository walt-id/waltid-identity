import id.walt.did.dids.resolver.local.DidWebResolver
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.network.tls.*
import io.ktor.serialization.kotlinx.json.*
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

object TestClient {

    val http = HttpClient(CIO) {
        install(ContentNegotiation) {
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