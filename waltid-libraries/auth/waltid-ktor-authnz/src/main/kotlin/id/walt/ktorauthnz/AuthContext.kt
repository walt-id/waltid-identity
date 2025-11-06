package id.walt.ktorauthnz

import id.walt.ktorauthnz.flows.AuthFlow
import kotlinx.serialization.Serializable

@Serializable
data class AuthContext(
    /**
     * Tenant discriminator in multi-tenant systems
     */
    val tenant: String? = null,

    /**
     * Session ID - will be unavailable for the initial method in an implicit session-start based flow
     */
    val sessionId: String? = null,

    /**
     * Enable implicit session generation (no explicit session start required - session started with first login method)
     */
    val implicitSessionGeneration: Boolean = false,

    /**
     * For implicit session start the auth flow is necessary
     */
    val initialFlow: AuthFlow? = null,

    /**
     * If the token should only be set as cookie, or also returned on auth success (for JavaScript use)
     */
    val revealTokenToClient: Boolean = true
) {

    init {
        if (implicitSessionGeneration) {
            check(initialFlow != null) { "For implicit session generation, an initial flow has to be passed to the AuthContext" }
        }
    }

}
