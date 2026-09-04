package id.walt.walletdemo.compose.web

import id.walt.walletdemo.compose.ui.WalletDemoBranding
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window

private const val BrandingStorageKey = "waltid.wallet.branding"

internal suspend fun loadWebBranding(): WalletDemoBranding {
    var branding = WalletDemoBranding()
    runCatching { fetchBrandingFile() }.getOrNull()?.let { branding = branding.overlayJson(it) }
    localStorage.getItem(BrandingStorageKey)?.let { branding = branding.overlayJson(it) }
    document.title = branding.appTitle
    return branding
}

private suspend fun fetchBrandingFile(): String? {
    val client = HttpClient(Js)
    return try {
        val response = client.get(brandingFileUrl())
        if (!response.status.isSuccess()) null else response.bodyAsText().trim().takeIf { it.isNotEmpty() }
    } finally {
        client.close()
    }
}

private fun brandingFileUrl(): String {
    val path = window.location.pathname
    val directory = if (path.endsWith("/")) path else path.substringBeforeLast('/') + "/"
    return window.location.origin + directory + "branding.json"
}
