package id.walt.walletdemo.compose.logic

/** Signing-key authorization choices exposed by the demo wallets. */
enum class WalletDemoSigningProtection {
    None,
    Biometric;

    companion object {
        fun parse(value: String): WalletDemoSigningProtection = when (value.trim().lowercase()) {
            "none" -> None
            "biometric" -> Biometric
            else -> throw IllegalArgumentException("Signing protection must be none or biometric")
        }
    }
}

/** Product constraint controlling which signing protection choices the demo exposes. */
enum class WalletDemoSigningProtectionMode {
    Required,
    Optional,
    Disabled;

    val defaultSelection: WalletDemoSigningProtection
        get() = when (this) {
            Required, Optional -> WalletDemoSigningProtection.Biometric
            Disabled -> WalletDemoSigningProtection.None
        }

    fun allows(protection: WalletDemoSigningProtection): Boolean = when (this) {
        Required -> protection == WalletDemoSigningProtection.Biometric
        Optional -> true
        Disabled -> protection == WalletDemoSigningProtection.None
    }

    fun resolve(stored: WalletDemoSigningProtection?): WalletDemoSigningProtection =
        stored?.takeIf(::allows) ?: defaultSelection

    companion object {
        fun parse(value: String): WalletDemoSigningProtectionMode = when (value.trim().lowercase()) {
            "required" -> Required
            "optional" -> Optional
            "disabled" -> Disabled
            else -> throw IllegalArgumentException(
                "Signing protection mode must be required, optional, or disabled",
            )
        }
    }
}

/** Result of checking whether a signing protection choice can be provisioned. */
enum class WalletDemoSigningProtectionAvailability {
    Available,
    BiometricNotEnrolled,
    BiometricUnavailable,
    Unsupported,
}

fun WalletDemoSigningProtectionAvailability.displayMessage(): String? = when (this) {
    WalletDemoSigningProtectionAvailability.Available -> null
    WalletDemoSigningProtectionAvailability.BiometricNotEnrolled ->
        WalletDisplayText.BiometricNotEnrolled
    WalletDemoSigningProtectionAvailability.BiometricUnavailable ->
        WalletDisplayText.BiometricUnavailable
    WalletDemoSigningProtectionAvailability.Unsupported ->
        WalletDisplayText.SigningProtectionUnsupported
}

/** App-owned persistence for the user's signing protection selection. */
interface WalletDemoSigningProtectionStore {
    fun load(): WalletDemoSigningProtection?
    fun save(protection: WalletDemoSigningProtection)
}

class InMemoryWalletDemoSigningProtectionStore(
    initial: WalletDemoSigningProtection? = null,
) : WalletDemoSigningProtectionStore {
    private var value = initial

    override fun load(): WalletDemoSigningProtection? = value

    override fun save(protection: WalletDemoSigningProtection) {
        value = protection
    }
}
