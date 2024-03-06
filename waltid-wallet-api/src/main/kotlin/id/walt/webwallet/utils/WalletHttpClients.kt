package id.walt.webwallet.utils

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*

object WalletHttpClients {

    var defaultMethod = {
        HttpClient() {
            install(ContentNegotiation) {
                json()
            }
            install(Logging) {
                logger = Logger.SIMPLE
                level = LogLevel.ALL
            }
            followRedirects = false
        }
    }

    fun getHttpClient(): HttpClient = defaultMethod()

}
