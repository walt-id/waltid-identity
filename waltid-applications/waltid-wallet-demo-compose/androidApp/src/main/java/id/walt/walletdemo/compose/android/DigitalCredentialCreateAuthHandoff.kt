package id.walt.walletdemo.compose.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import org.json.JSONObject

/**
 * Durable handoff for authorization-code issuance started from [DigitalCredentialCreateActivity].
 *
 * Credential Manager create stays on the translucent Activity while the system browser handles
 * issuer/AS login. The dedicated [DigitalCredentialCreateCallbackActivity] delivers the callback
 * here so the still-running create Activity can finish the OpenID4VCI token exchange and return
 * the CREATE_CREDENTIAL provider result.
 *
 * The pending authorization ID is persisted so a process death after browser launch can still
 * complete wallet-side issuance (the CREATE_CREDENTIAL provider result may already be lost).
 */
internal object DigitalCredentialCreateAuthHandoff {
    const val REDIRECT_URI = "walt-wallet-create://authorize"
    enum class Delivery { Unmatched, Live, Orphan, Duplicate }
    private const val PREFS = "digital_credential_create_auth"
    private const val PREFIX = "pending:"
    private val live = mutableMapOf<String, (String) -> Unit>()
    private val claimed = mutableSetOf<String>()

    @Synchronized
    fun register(context: Context, authorizationId: String, state: String, redirectUri: String, onCallback: (String) -> Unit) {
        require(authorizationId.isNotBlank() && state.isNotBlank()) { "Authorization correlation is missing" }
        val entry = JSONObject().put("state", state).put("redirect", redirectUri).toString()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREFIX + authorizationId, entry).apply()
        live[authorizationId] = onCallback
    }

    /** Cleanup is scoped to one owner, including when another provider invocation is active. */
    @Synchronized
    fun clear(context: Context, authorizationId: String) {
        live.remove(authorizationId)
        claimed.remove(authorizationId)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(PREFIX + authorizationId).apply()
    }

    /** Test-only process-loss simulation: persisted correlation remains available. */
    @Synchronized
    internal fun dropLiveContinuation() { live.clear(); claimed.clear() }

    /** SDK validation still applies. This correlation only decides which flow owns the URI. */
    @Synchronized
    fun deliver(context: Context, uri: Uri): Delivery {
        if (!uri.isHierarchical || uri.fragment != null) return Delivery.Unmatched
        val state = uri.getQueryParameters("state").singleOrNull()?.takeIf { it.isNotBlank() } ?: return Delivery.Unmatched
        val codes = uri.getQueryParameters("code")
        val errors = uri.getQueryParameters("error")
        if (!((codes.size == 1 && codes.single().isNotBlank() && errors.isEmpty()) ||
                (errors.size == 1 && errors.single().isNotBlank() && codes.isEmpty()))) return Delivery.Unmatched
        val matches = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
            .filter { (key, value) ->
                if (!key.startsWith(PREFIX) || value !is String) return@filter false
                val entry = runCatching { JSONObject(value) }.getOrNull() ?: return@filter false
                val redirect = Uri.parse(entry.optString("redirect"))
                entry.optString("state") == state && uri.scheme.equals(redirect.scheme, ignoreCase = true) &&
                    uri.encodedAuthority.orEmpty() == redirect.encodedAuthority.orEmpty() &&
                    uri.encodedPath.orEmpty() == redirect.encodedPath.orEmpty()
            }
        val authorizationId = matches.keys.singleOrNull()?.removePrefix(PREFIX) ?: return Delivery.Unmatched
        if (!claimed.add(authorizationId)) return Delivery.Duplicate
        val continuation = live.remove(authorizationId)
        return if (continuation != null) {
            continuation(uri.toString())
            Delivery.Live
        } else {
            OrphanAuthorizationCallback.queue(authorizationId, uri.toString())
            Delivery.Orphan
        }
    }

    fun openExternalBrowser(context: Context, authorizationUrl: String) {
        // A regular browser launch can clear the requesting tab's task, including this
        // provider. Authorization in a custom tab keeps the original request host alive.
        val tab = CustomTabsIntent.Builder().build()
        tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tab.launchUrl(context, Uri.parse(authorizationUrl))
    }
}

/**
 * Holds an authorization callback that arrived after [DigitalCredentialCreateActivity] was
 * destroyed. [MainActivity] passes the saved authorization ID and callback to the wallet so the
 * credential is still stored even when the CREATE_CREDENTIAL result can no longer be returned.
 */
internal object OrphanAuthorizationCallback {
    private val pending = java.util.concurrent.ConcurrentLinkedQueue<Pair<String, String>>()

    fun queue(authorizationId: String, callbackUri: String) {
        pending.add(authorizationId to callbackUri)
    }

    fun take(): Pair<String, String>? = pending.poll()
}

/**
 * Notifies [MainActivity] after CREATE_CREDENTIAL (or orphan auth) writes into the shared wallet
 * store.
 *
 * The provider's dedicated authorization callback returns to CreateActivity. MainActivity may
 * already be resumed when the provider or orphan callback finishes storage, so an `onResume`
 * refresh alone can miss the new credential until the next process foregrounding.
 */
internal object WalletDemoCredentialStoreNotifier {
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        for (listener in listeners) {
            runCatching(listener)
        }
    }
}
