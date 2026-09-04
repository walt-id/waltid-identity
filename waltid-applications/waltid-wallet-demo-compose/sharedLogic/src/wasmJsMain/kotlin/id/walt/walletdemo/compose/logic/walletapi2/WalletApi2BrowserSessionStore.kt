package id.walt.walletdemo.compose.logic.walletapi2

import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage
import kotlinx.browser.window
import kotlinx.serialization.encodeToString
import org.w3c.dom.HTMLMetaElement

private const val TokenKey = "waltid.wallet2.token"
private const val WalletIdKey = "waltid.wallet2.walletId"
private const val EmailKey = "waltid.wallet2.email"
private const val WalletIdCookie = "waltid_wallet_id"
private const val PendingIssuanceKey = "waltid.wallet2.pendingIssuance"

object WalletApi2BrowserSessionStore {
    fun load(): WalletApi2Session? {
        val token = localStorage.getItem(TokenKey)?.takeIf { it.isNotBlank() } ?: return null
        val walletId = localStorage.getItem(WalletIdKey)?.takeIf { it.isNotBlank() }
            ?: readCookie(WalletIdCookie)
            ?: return null
        val email = localStorage.getItem(EmailKey).orEmpty()
        return WalletApi2Session(
            baseUrl = walletApi2BaseUrl(),
            token = token,
            walletId = walletId,
            email = email,
        )
    }

    fun save(session: WalletApi2Session) {
        localStorage.setItem(TokenKey, session.token)
        localStorage.setItem(WalletIdKey, session.walletId)
        localStorage.setItem(EmailKey, session.email)
        writeCookie(WalletIdCookie, session.walletId)
    }

    fun updateWalletId(walletId: String) {
        localStorage.setItem(WalletIdKey, walletId)
        writeCookie(WalletIdCookie, walletId)
    }

    internal fun savePendingIssuance(session: PersistedAuthorizationIssuance) {
        val encoded = walletApi2Json.encodeToString(session)
        localStorage.setItem(PendingIssuanceKey, encoded)
        sessionStorage.setItem(PendingIssuanceKey, encoded)
    }

    internal fun loadPendingIssuance(): PersistedAuthorizationIssuance? {
        val raw = sessionStorage.getItem(PendingIssuanceKey)?.takeIf { it.isNotBlank() }
            ?: localStorage.getItem(PendingIssuanceKey)?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching { walletApi2Json.decodeFromString<PersistedAuthorizationIssuance>(raw) }.getOrNull()
    }

    internal fun clearPendingIssuance() {
        sessionStorage.removeItem(PendingIssuanceKey)
        localStorage.removeItem(PendingIssuanceKey)
    }

    fun clear() {
        localStorage.removeItem(TokenKey)
        localStorage.removeItem(WalletIdKey)
        localStorage.removeItem(EmailKey)
        writeCookie(WalletIdCookie, "", maxAge = 0)
        clearPendingIssuance()
    }

    suspend fun signOut(session: WalletApi2Session) {
        runCatching { WalletApi2AuthClient(session.baseUrl).logout(session.token) }
        clear()
    }
}

private const val WalletApi2BaseUrlPlaceholder = "__WALLET_API2_BASE_URL__"
private const val DefaultWalletApi2BaseUrl = "http://localhost:7006"

fun walletApi2BaseUrl(): String =
    localStorage.getItem("waltid.wallet2.baseUrl")?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
        ?: configuredWalletApi2BaseUrl()
        ?: DefaultWalletApi2BaseUrl

private fun configuredWalletApi2BaseUrl(): String? {
    val meta = document.querySelector("meta[name='waltid-wallet-api2']") as? HTMLMetaElement ?: return null
    val content = meta.content.trim().trimEnd('/')
    if (content.isBlank() || content == WalletApi2BaseUrlPlaceholder) return null
    return content
}

fun webIssuanceRedirectUri(): String {
    val origin = window.location.origin
    val path = window.location.pathname.ifBlank { "/" }
    return origin + path
}

private fun readCookie(name: String): String? {
    val prefix = "$name="
    return document.cookie.split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith(prefix) }
        ?.removePrefix(prefix)
        ?.takeIf { it.isNotBlank() }
}

private fun writeCookie(name: String, value: String, maxAge: Int = 31_536_000) {
    document.cookie = "$name=$value; path=/; max-age=$maxAge"
}
