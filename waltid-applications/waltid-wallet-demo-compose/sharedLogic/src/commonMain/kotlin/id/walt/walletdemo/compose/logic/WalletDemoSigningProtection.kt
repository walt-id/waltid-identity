package id.walt.walletdemo.compose.logic

/** Signing-key authorization choices exposed by the demo wallets. */
enum class WalletDemoSigningProtection {
    None,
    Biometric,
    BiometricPerUse;

    val requiresBiometrics: Boolean get() = this != None

    companion object {
        fun parse(value: String): WalletDemoSigningProtection = when (value.trim().lowercase()) {
            "none" -> None
            "biometric" -> Biometric
            "biometricperuse" -> BiometricPerUse
            else -> throw IllegalArgumentException("Signing protection must be none, biometric, or biometricPerUse")
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
        Required -> protection.requiresBiometrics
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
    DeviceCredentialNotSet,
    BiometricUnavailable,
    Unsupported,
}

fun WalletDemoSigningProtectionAvailability.displayMessage(): String? = when (this) {
    WalletDemoSigningProtectionAvailability.Available -> null
    WalletDemoSigningProtectionAvailability.BiometricNotEnrolled ->
        WalletDisplayText.BiometricNotEnrolled
    WalletDemoSigningProtectionAvailability.DeviceCredentialNotSet ->
        WalletDisplayText.DeviceCredentialNotSet
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

internal fun WalletDemoSigningProtection.approvalChoice(): WalletDemoKeyChoice = WalletDemoKeyChoice(name,
    when (this) {
        WalletDemoSigningProtection.None -> "No biometric signing"
        WalletDemoSigningProtection.Biometric -> "Biometrics with timed approval"
        WalletDemoSigningProtection.BiometricPerUse -> "Current biometrics only"
    }, when (this) {
        WalletDemoSigningProtection.None -> "Signing does not require system approval. Your wallet PIN does not authorize the signing key."
        WalletDemoSigningProtection.Biometric -> "Approve signing with biometrics. Authorization is reused for up to 10 seconds."
        WalletDemoSigningProtection.BiometricPerUse -> "Approve each signature with biometrics. Changing enrolled biometrics invalidates this key."
    })

fun WalletDemoSigningProtectionAvailability.recoveryAvailability(unlock: DemoBiometricAvailability): DemoBiometricAvailability = when (this) {
    WalletDemoSigningProtectionAvailability.Available -> DemoBiometricAvailability.Available
    WalletDemoSigningProtectionAvailability.BiometricNotEnrolled -> DemoBiometricAvailability.NotEnrolled
    WalletDemoSigningProtectionAvailability.DeviceCredentialNotSet -> DemoBiometricAvailability.DeviceCredentialNotSet
    WalletDemoSigningProtectionAvailability.Unsupported -> DemoBiometricAvailability.Unsupported
    WalletDemoSigningProtectionAvailability.BiometricUnavailable ->
        if (unlock == DemoBiometricAvailability.LockedOut) unlock else DemoBiometricAvailability.Unavailable
}
