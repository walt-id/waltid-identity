package id.walt.walletdemo.compose.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.util.concurrent.atomic.AtomicReference

/**
 * Durable handoff for authorization-code issuance started from [DigitalCredentialCreateActivity].
 *
 * Credential Manager create stays on the translucent Activity while the system browser handles
 * issuer/AS login. When the AS redirects to `openid://…`, [MainActivity] delivers the callback
 * here so the still-running create Activity can finish the OpenID4VCI token exchange and return
 * the CREATE_CREDENTIAL provider result.
 *
 * The pending session id is also persisted so a process death after browser launch can still
 * complete wallet-side issuance (the CREATE_CREDENTIAL provider result may already be lost).
 */
internal object DigitalCredentialCreateAuthHandoff {
    private const val PREFS = "digital_credential_create_auth"
    private const val KEY_SESSION_ID = "session_id"
    private const val TAG = "WaltDigitalCredentials"

    private val pendingContinuation = AtomicReference<((String) -> Unit)?>(null)

    fun register(context: Context, sessionId: String, onCallback: (String) -> Unit) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SESSION_ID, sessionId)
            .apply()
        pendingContinuation.set(onCallback)
    }

    fun clear(context: Context) {
        pendingContinuation.set(null)
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SESSION_ID)
            .apply()
    }

    /** Test-only: drop the in-memory continuation while leaving the persisted session id. */
    internal fun dropLiveContinuation() {
        pendingContinuation.set(null)
    }

    fun pendingSessionId(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SESSION_ID, null)
            ?.takeIf { it.isNotBlank() }

    /** @return true when this URI was consumed as a create-flow authorization callback. */
    fun deliver(context: Context, uri: Uri): Boolean {
        if (!isOpenIdCallback(uri)) return false
        val callback = uri.toString()
        val continuation = pendingContinuation.getAndSet(null)
        if (continuation != null) {
            Log.i(TAG, "Delivering authorization callback to create Activity")
            continuation(callback)
            return true
        }
        val sessionId = pendingSessionId(context) ?: return false
        Log.i(TAG, "Create Activity gone; queuing orphan authorization callback for session=$sessionId")
        OrphanAuthorizationCallback.queue(sessionId, callback)
        return true
    }

    fun isOpenIdCallback(uri: Uri): Boolean =
        uri.scheme.equals("openid", ignoreCase = true)

    fun openExternalBrowser(context: Context, authorizationUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/**
 * Holds an authorization callback that arrived after [DigitalCredentialCreateActivity] was
 * destroyed. [MainActivity] drains this into [MobileWallet.continueAuthorizationIssuance] so the
 * credential is still stored even when the CREATE_CREDENTIAL result can no longer be returned.
 */
internal object OrphanAuthorizationCallback {
    private val pending = AtomicReference<Pair<String, String>?>(null)

    fun queue(sessionId: String, callbackUri: String) {
        pending.set(sessionId to callbackUri)
    }

    fun take(): Pair<String, String>? = pending.getAndSet(null)
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
