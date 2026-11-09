package id.walt.walletdemo.compose.logic

import io.ktor.client.HttpClient

// The browser demo does not decode payment logos; do not fetch bytes it cannot display.
internal actual fun paymentLogoHttpClient(): HttpClient? = null
