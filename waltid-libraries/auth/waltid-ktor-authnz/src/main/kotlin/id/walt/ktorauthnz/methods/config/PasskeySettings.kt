package id.walt.ktorauthnz.methods.config

/**
 * The WebAuthn relying party for passkeys: [rpId] is the registrable domain passkeys are bound to (e.g.
 * `example.com`), [origins] the web origins logins come from (e.g. `https://app.example.com`).
 */
data class PasskeySettings(
    val rpId: String,
    val rpName: String,
    val origins: Set<String>,
    /** Require user verification (PIN, biometrics) on every use, not only presence. */
    val requireUserVerification: Boolean = false,
)
