package id.walt.walletdemo.compose.logic

interface DemoPinStore {
    fun hasPin(): Boolean
    suspend fun setPin(pin: String)
    suspend fun verifyPin(pin: String): Boolean
    fun isBiometricUnlockEnabled(): Boolean
    fun setBiometricUnlockEnabled(enabled: Boolean)
    fun isBiometricSetupPending(): Boolean
    fun setBiometricSetupPending(pending: Boolean)
    fun clear()
}

class InMemoryDemoPinStore : DemoPinStore {
    private var configuredPin: String? = null
    private var biometricUnlockEnabled: Boolean = false
    private var biometricSetupPending: Boolean = false

    override fun hasPin(): Boolean = configuredPin != null

    override suspend fun setPin(pin: String) {
        configuredPin = pin
    }

    override suspend fun verifyPin(pin: String): Boolean = configuredPin == pin

    override fun isBiometricUnlockEnabled(): Boolean = biometricUnlockEnabled

    override fun setBiometricUnlockEnabled(enabled: Boolean) {
        biometricUnlockEnabled = enabled
    }

    override fun isBiometricSetupPending(): Boolean = biometricSetupPending
    override fun setBiometricSetupPending(pending: Boolean) { biometricSetupPending = pending }

    override fun clear() {
        configuredPin = null
        biometricUnlockEnabled = false
        biometricSetupPending = false
    }
}
