package id.walt.walletdemo.compose.logic

interface DemoPinStore {
    fun hasPin(): Boolean
    suspend fun setPin(pin: String)
    suspend fun verifyPin(pin: String): Boolean
}

class InMemoryDemoPinStore : DemoPinStore {
    private var configuredPin: String? = null

    override fun hasPin(): Boolean = configuredPin != null

    override suspend fun setPin(pin: String) {
        configuredPin = pin
    }

    override suspend fun verifyPin(pin: String): Boolean = configuredPin == pin
}
