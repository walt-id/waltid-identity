package id.walt.walletdemo.compose.logic

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Authenticator
import okhttp3.CookieJar

internal actual fun paymentLogoHttpClient(): HttpClient? = HttpClient(OkHttp) {
    followRedirects = false
    expectSuccess = false
    engine { config {
        cookieJar(CookieJar.NO_COOKIES)
        authenticator(Authenticator.NONE)
        proxyAuthenticator(Authenticator.NONE)
    } }
}
