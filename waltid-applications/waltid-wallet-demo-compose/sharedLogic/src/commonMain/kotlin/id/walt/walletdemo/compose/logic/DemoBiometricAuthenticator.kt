package id.walt.walletdemo.compose.logic

interface DemoBiometricAuthenticator {
    fun isAvailable(): Boolean
    suspend fun authenticate(reason: String): DemoBiometricResult
}

enum class DemoBiometricResult {
    Succeeded,
    Cancelled,
    Unavailable,
    LockedOut,
    Failed,
}

object UnavailableDemoBiometricAuthenticator : DemoBiometricAuthenticator {
    override fun isAvailable(): Boolean = false
    override suspend fun authenticate(reason: String): DemoBiometricResult = DemoBiometricResult.Unavailable
}
