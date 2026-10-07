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
private const val BaseUrlKey = "waltid.wallet2.baseUrl"
private const val TargetsKey = "waltid.wallet2.walletTargets"
private const val ApiKindKey = "waltid.wallet2.apiKind"
private const val WalletIdCookie = "waltid_wallet_id"
private const val PendingIssuanceKey = "waltid.wallet2.pendingIssuance"

object WalletApi2BrowserSessionStore {
    fun load(): WalletApi2Session? {
        val token = localStorage.getItem(TokenKey)?.takeIf { it.isNotBlank() } ?: return null
        val walletId = localStorage.getItem(WalletIdKey)?.takeIf { it.isNotBlank() }
            ?: readCookie(WalletIdCookie)
            ?: return null
        val email = localStorage.getItem(EmailKey).orEmpty()
        val deployedKind = walletApiKind()
        val savedProfile = localStorage.getItem(ApiKindKey)
        if (!sessionMatchesDeployedProfile(savedProfile, deployedKind)) {
            clearAuth()
            return null
        }
        if (savedProfile.isNullOrBlank()) localStorage.setItem(ApiKindKey, deployedKind.name)
        return WalletApi2Session(
            kind = deployedKind,
            baseUrl = walletApi2BaseUrl(),
            token = token,
            walletId = walletId,
            email = email,
            walletTargets = loadWalletTargets(),
        )
    }

    fun save(session: WalletApi2Session) {
        localStorage.setItem(TokenKey, session.token)
        localStorage.setItem(WalletIdKey, session.walletId)
        localStorage.setItem(EmailKey, session.email)
        localStorage.setItem(ApiKindKey, session.kind.name)
        localStorage.setItem(TargetsKey, walletApi2Json.encodeToString(session.walletTargets))
        writeCookie(WalletIdCookie, session.walletId)
    }

    fun updateWalletId(walletId: String) {
        localStorage.setItem(WalletIdKey, walletId)
        writeCookie(WalletIdCookie, walletId)
    }

    fun prepareIncomingAuthorizationWallet(
        session: WalletApi2Session,
        availableWalletIds: List<String>,
    ): WalletApi2Session? {
        val selected = walletForAuthorizationCallback(
            currentWalletId = session.walletId,
            availableWalletIds = availableWalletIds,
            pendingWalletId = loadPendingIssuance()?.walletId,
        ) ?: run {
            clearPendingIssuance()
            return null
        }
        if (selected == session.walletId) return session
        return session.copy(
            walletId = selected,
            walletTargets = availableWalletIds.ifEmpty { session.walletTargets },
        ).also(::save)
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

    internal fun clearPendingIssuance(sessionId: String? = null) {
        for (storage in listOf(sessionStorage, localStorage)) {
            if (sessionId != null) {
                val raw = storage.getItem(PendingIssuanceKey) ?: continue
                val pending = runCatching { walletApi2Json.decodeFromString<PersistedAuthorizationIssuance>(raw) }.getOrNull()
                if (pending?.id != sessionId) continue
            }
            storage.removeItem(PendingIssuanceKey)
        }
    }

    fun clearAuth() {
        localStorage.removeItem(TokenKey)
        localStorage.removeItem(EmailKey)
        localStorage.removeItem(ApiKindKey)
        clearPendingIssuance()
    }

    fun clear() {
        clearAuth()
        localStorage.removeItem(WalletIdKey)
        localStorage.removeItem(TargetsKey)
        writeCookie(WalletIdCookie, "", maxAge = 0)
    }

    suspend fun signOut(session: WalletApi2Session) {
        runCatching { WalletApi2AuthClient(session.baseUrl, session.kind).logout(session.token) }
        clearAuth()
    }
}

private const val WalletApi2BaseUrlPlaceholder = "__WALLET_API2_BASE_URL__"
private const val WalletApiFlavorPlaceholder = "__WALLET_API_FLAVOR__"
private const val DefaultWalletApi2BaseUrl = "http://localhost:7006"

fun walletApiKind(): WalletApiKind = parseWalletApiKind(configuredWalletApiKind())

fun walletApi2BaseUrl(): String =
    localStorage.getItem(BaseUrlKey)?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
        ?: configuredWalletApi2BaseUrl()
        ?: DefaultWalletApi2BaseUrl

fun rememberedWalletId(): String? =
    localStorage.getItem(WalletIdKey)?.takeIf { it.isNotBlank() }
        ?: readCookie(WalletIdCookie)

private fun loadWalletTargets(): List<String> {
    val raw = localStorage.getItem(TargetsKey)?.takeIf { it.isNotBlank() } ?: return emptyList()
    return runCatching { walletApi2Json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
}

private fun configuredWalletApiKind(): String? {
    val meta = document.querySelector("meta[name='waltid-wallet-api-flavor']") as? HTMLMetaElement ?: return null
    val content = meta.content.trim()
    if (content.isBlank() || content == WalletApiFlavorPlaceholder) return null
    return content
}

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
