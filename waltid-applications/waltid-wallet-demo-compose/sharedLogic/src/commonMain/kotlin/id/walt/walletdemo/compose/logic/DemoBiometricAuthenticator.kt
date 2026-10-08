package id.walt.walletdemo.compose.logic

interface DemoBiometricAuthenticator {
    val kind: DemoBiometricKind get() = DemoBiometricKind.Generic
    fun availability(): DemoBiometricAvailability
    fun isAvailable(): Boolean = availability() == DemoBiometricAvailability.Available
    suspend fun authenticate(reason: String): DemoBiometricResult
}

/** Capability is independent of the user's unlock preference and signing-key policy. */
enum class DemoBiometricAvailability {
    Available, NotEnrolled, DeviceCredentialNotSet, LockedOut, Unavailable, Unsupported;

    fun authenticationResult(): DemoBiometricResult =
        if (this == LockedOut) DemoBiometricResult.LockedOut else DemoBiometricResult.Unavailable
}

enum class DemoBiometricResult {
    Succeeded,
    Cancelled,
    Unavailable,
    LockedOut,
    Failed,
}

object UnavailableDemoBiometricAuthenticator : DemoBiometricAuthenticator {
    override fun availability(): DemoBiometricAvailability = DemoBiometricAvailability.Unsupported
    override suspend fun authenticate(reason: String): DemoBiometricResult = DemoBiometricResult.Unavailable
}
