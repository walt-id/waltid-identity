package id.walt.walletdemo.compose.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject

/**
 * Durable handoff for authorization-code issuance started from [DigitalCredentialCreateActivity].
 *
 * Credential Manager create stays on the translucent Activity while the system browser handles
 * issuer/AS login. The dedicated [DigitalCredentialCreateCallbackActivity] delivers the callback
 * here so the still-running create Activity can finish the OpenID4VCI token exchange and return
 * the CREATE_CREDENTIAL provider result.
 *
 * The pending session id is also persisted so a process death after browser launch can still
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
    fun register(context: Context, sessionId: String, state: String, redirectUri: String, onCallback: (String) -> Unit) {
        require(sessionId.isNotBlank() && state.isNotBlank()) { "Authorization correlation is missing" }
        val entry = JSONObject().put("state", state).put("redirect", redirectUri).toString()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(PREFIX + sessionId, entry).apply()
        live[sessionId] = onCallback
    }

    /** Cleanup is scoped to one owner, including when another provider invocation is active. */
    @Synchronized
    fun clear(context: Context, sessionId: String) {
        live.remove(sessionId)
        claimed.remove(sessionId)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(PREFIX + sessionId).apply()
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
        val sessionId = matches.keys.singleOrNull()?.removePrefix(PREFIX) ?: return Delivery.Unmatched
        if (!claimed.add(sessionId)) return Delivery.Duplicate
        val continuation = live.remove(sessionId)
        return if (continuation != null) {
            continuation(uri.toString())
            Delivery.Live
        } else {
            OrphanAuthorizationCallback.queue(sessionId, uri.toString())
            Delivery.Orphan
        }
    }

    fun openExternalBrowser(context: Context, authorizationUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/**
 * Holds an authorization callback that arrived after [DigitalCredentialCreateActivity] was
 * destroyed. [MainActivity] drains this into the SDK's continueAuthorizationIssuance so the
 * credential is still stored even when the CREATE_CREDENTIAL result can no longer be returned.
 */
internal object OrphanAuthorizationCallback {
    private val pending = java.util.concurrent.ConcurrentLinkedQueue<Pair<String, String>>()

    fun queue(sessionId: String, callbackUri: String) {
        pending.add(sessionId to callbackUri)
    }

    fun take(): Pair<String, String>? = pending.poll()
}

/**
 * Notifies [MainActivity] after CREATE_CREDENTIAL (or orphan auth) writes into the shared wallet
 * store.
 *
 * Needed because the `openid://` callback often resumes MainActivity *before* CreateActivity
 * finishes token exchange and storage; a plain `onResume` refresh can therefore miss the new
 * credential until the next process foregrounding.
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
