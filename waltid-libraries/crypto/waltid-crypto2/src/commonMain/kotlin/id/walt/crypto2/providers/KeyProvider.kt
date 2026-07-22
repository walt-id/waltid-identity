package id.walt.crypto2.providers

import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.KeyAgreementAlgorithm
import id.walt.crypto2.algorithms.KeyWrappingAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyEncodingFormat
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.serialization.BinaryData

enum class CryptoOperation {
    GENERATE_KEY,
    IMPORT_KEY,
    SIGN,
    VERIFY,
    ENCRYPT,
    DECRYPT,
    KEY_AGREEMENT,
    WRAP,
    UNWRAP,
    DELETE,
    EXPORT_PUBLIC,
    EXPORT_PRIVATE,
}

data class CryptoRequirement(
    val operation: CryptoOperation,
    val spec: KeySpec,
    val usages: Set<KeyUsage>,
    val signatureAlgorithm: SignatureAlgorithm? = null,
    val encryptionAlgorithm: AsymmetricEncryptionAlgorithm? = null,
    val keyAgreementAlgorithm: KeyAgreementAlgorithm? = null,
    val keyWrappingAlgorithm: KeyWrappingAlgorithm? = null,
    val keyEncoding: KeyEncodingFormat? = null,
)

data class GenerateSoftwareKeyRequest(
    val id: KeyId,
    val spec: KeySpec,
    val usages: Set<KeyUsage>,
    val keyEncoding: KeyEncodingFormat = KeyEncodingFormat.JWK,
    val metadata: Map<String, String> = emptyMap(),
)

data class GenerateManagedKeyRequest(
    val id: KeyId,
    val spec: KeySpec,
    val usages: Set<KeyUsage>,
    val providerOptions: BinaryData = BinaryData(byteArrayOf()),
    val metadata: Map<String, String> = emptyMap(),
)

interface SoftwareKeyProvider {
    val id: ProviderId

    fun supports(requirement: CryptoRequirement): Boolean

    suspend fun generate(request: GenerateSoftwareKeyRequest): SoftwareKey

    suspend fun restore(stored: StoredKey.Software): SoftwareKey

    suspend fun close() = Unit
}

interface ManagedKeyProvider {
    val id: ProviderId

    suspend fun generate(request: GenerateManagedKeyRequest): ManagedKey

    suspend fun restore(stored: StoredKey.Managed): ManagedKey

    suspend fun close() = Unit
}

sealed interface ProviderSelection {
    data object Automatic : ProviderSelection
    data class Only(val provider: ProviderId) : ProviderSelection
    data class FirstAvailable(val providers: List<ProviderId>) : ProviderSelection {
        init {
            require(providers.isNotEmpty()) { "At least one provider must be selected" }
        }
    }
}
