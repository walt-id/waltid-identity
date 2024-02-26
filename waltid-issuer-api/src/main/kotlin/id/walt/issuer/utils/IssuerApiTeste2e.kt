package id.walt.issuer.utils
import id.walt.credentials.issuance.Issuer
import id.walt.issuer.OidcApi.oidcApi
import id.walt.issuer.base.config.ConfigManager
import id.walt.issuer.configurePlugins
import id.walt.issuer.entraIssuance
import id.walt.issuer.issuerApi
import id.walt.issuer.main
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking

class IssuerApiTeste2e {
  
  companion object {
    private lateinit var localClient: HttpClient
    init {
//      startLocalIssuer()
//      startTestApplicationIssuer()
    }
    
    private fun startLocalIssuer() {
      runBlocking {
        main(emptyArray()) // call issuer main function to start issuer api
      }
    }
    private fun startTestApplicationIssuer() {
      println("Issuer API : Test Application starting...")
      val args = emptyArray<String>()
      ConfigManager.loadConfigs(args)
      val testApp = TestApplication {
        application {
          configurePlugins()
          oidcApi()
          issuerApi()
          entraIssuance()
        }
      }
      localClient = testApp.createClient {
        install(ContentNegotiation) {
          json()
        }
      }
    }
  }
  
  fun getHttpClient(): HttpClient {
    return localClient
  }
}
