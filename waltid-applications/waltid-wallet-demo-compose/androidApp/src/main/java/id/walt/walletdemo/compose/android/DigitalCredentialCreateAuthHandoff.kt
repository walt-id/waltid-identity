package id.walt.walletdemo.compose.android

/**
 * Hands an `openid://` authorization callback from [MainActivity] to a pending
 * [DigitalCredentialCreateActivity] authorization-code flow.
 *
 * Credential Manager owns the create activity's task. The browser redirect still arrives through
 * the app's existing `openid://` deep link on [MainActivity], so that entry point forwards matching
 * callbacks here instead of starting a parallel Receive-tab issuance session.
 */
internal object DigitalCredentialCreateAuthHandoff {
    @Volatile
    private var pendingSessionId: String? = null

    @Volatile
    private var onCallback: ((callbackUri: String) -> Unit)? = null

    fun begin(sessionId: String, onCallback: (callbackUri: String) -> Unit) {
        pendingSessionId = sessionId
        this.onCallback = onCallback
    }

    fun clear(sessionId: String? = pendingSessionId) {
        if (sessionId == null || pendingSessionId == sessionId) {
            pendingSessionId = null
            onCallback = null
        }
    }

    fun isPending(): Boolean = pendingSessionId != null

    /**
     * Delivers [callbackUri] when a create-activity authorization is waiting.
     *
     * @return true when the create flow consumed the callback.
     */
    fun deliver(callbackUri: String): Boolean {
        val callback = onCallback ?: return false
        clear()
        callback(callbackUri)
        return true
    }
}
