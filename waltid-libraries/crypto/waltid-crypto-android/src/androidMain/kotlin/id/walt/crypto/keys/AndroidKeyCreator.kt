package id.walt.crypto.keys

interface AndroidKeyCreator {
    suspend fun generate(type: KeyType, metadata: AndroidKeyParameters? = null): AndroidKey
}

data class AndroidKeyParameters(
    val keyId: String,
    val isProtected: Boolean = false
)

