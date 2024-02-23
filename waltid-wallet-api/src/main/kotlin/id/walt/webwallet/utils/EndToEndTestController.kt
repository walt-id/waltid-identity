import io.ktor.client.*
import id.walt.issuer.utils.IssuerApiTeste2e

class EndToEndTestController {
    
    companion object{
        private var issuer = IssuerApiTeste2e()
        private var controllerIssuerClient: HttpClient = issuer.getHttpClient()
        
        fun getClient() : HttpClient {
            return controllerIssuerClient
        }
    }
}