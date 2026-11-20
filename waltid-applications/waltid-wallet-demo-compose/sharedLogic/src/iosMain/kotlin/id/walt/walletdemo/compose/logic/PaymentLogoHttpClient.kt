package id.walt.walletdemo.compose.logic

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal actual fun paymentLogoHttpClient(): HttpClient? = HttpClient(Darwin) {
    followRedirects = false
    expectSuccess = false
    engine { configureSession {
        HTTPCookieStorage = null
        HTTPShouldSetCookies = false
        URLCredentialStorage = null
        URLCache = null
    } }
}
