import io.ktor.client.*
import id.walt.issuer.utils.IssuerApiTeste2e

class EndToEndTestController {
    
    // TODO add similar for wallet (and verifier) API here
    companion object{
        private var ktorTestIssuer = IssuerApiTeste2e()
        private var controllerIssuerClient: HttpClient = ktorTestIssuer.getHttpClient()
        
        fun getClient() : HttpClient {
            return controllerIssuerClient
        }
    }
}