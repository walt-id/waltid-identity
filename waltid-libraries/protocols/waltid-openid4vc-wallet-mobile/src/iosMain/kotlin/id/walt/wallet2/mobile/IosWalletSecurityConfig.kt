package id.walt.wallet2.mobile

/**
 * iOS-specific security settings for [MobileWalletFactory].
 *
 * @property useSecureElement When `true`, supported keys are created in Secure Enclave where available.
 */
data class IosWalletSecurityConfig(
    val useSecureElement: Boolean = true,
)
