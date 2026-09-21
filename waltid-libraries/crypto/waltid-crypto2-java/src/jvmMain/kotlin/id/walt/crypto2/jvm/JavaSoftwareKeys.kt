package id.walt.crypto2.jvm

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.future
import kotlinx.serialization.json.Json
import java.util.concurrent.CompletionStage

private val javaSoftwareKeyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Java-callable entry point for generating and using software keys, mirroring the
 * [JavaManagedKeyProvider] SPI's role for managed keys: [id.walt.crypto2.providers.GenerateSoftwareKeyRequest],
 * [id.walt.crypto2.keys.KeyId] and the suspend `sign`/`verify`/`close` calls on the Kotlin API have no
 * Java-callable form, so this object adapts them to plain types and [CompletionStage]s.
 */
object JavaSoftwareKeys {
    @JvmStatic
    fun defaultRuntime(): CryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders())

    @JvmStatic
    fun generate(runtime: CryptoRuntime, request: JavaGenerateSoftwareKeyRequest): CompletionStage<JavaSoftwareKey> =
        javaSoftwareKeyScope.future {
            JavaSoftwareKeyAdapter(runtime.generateSoftwareKey(request.toKotlin()))
        }

    /**
     * Like [generate], but returns the real [SoftwareKey] instead of the plain-Java [JavaSoftwareKey]
     * facade - for Java callers that need to pass the generated key into other crypto2-consuming
     * Kotlin APIs (e.g. `waltid-x509`'s certificate builders) rather than just sign/verify with it.
     */
    @JvmStatic
    fun generateKey(runtime: CryptoRuntime, request: JavaGenerateSoftwareKeyRequest): CompletionStage<SoftwareKey> =
        javaSoftwareKeyScope.future { runtime.generateSoftwareKey(request.toKotlin()) }

    @JvmStatic
    fun close(runtime: CryptoRuntime): CompletionStage<Void?> = javaSoftwareKeyScope.future {
        runtime.close()
        null
    }

    /** Converts a [JavaSignatureAlgorithm] to the real [SignatureAlgorithm], for handing to Kotlin APIs. */
    @JvmStatic
    fun toKotlin(algorithm: JavaSignatureAlgorithm): SignatureAlgorithm = algorithm.toKotlin()
}

private fun JavaGenerateSoftwareKeyRequest.toKotlin() = GenerateSoftwareKeyRequest(
    id = KeyId(id()),
    spec = spec().toKotlin(),
    usages = usages(),
    keyEncoding = keyEncoding(),
    metadata = metadata(),
)

private class JavaSoftwareKeyAdapter(private val key: SoftwareKey) : JavaSoftwareKey {
    override fun id(): String = key.id.value

    override fun storedKeyJson(): String = Json.encodeToString(StoredKey.Software.serializer(), key.storedKey)

    override fun signer(): JavaSigner? = key.capabilities.signer?.let { signer ->
        JavaSigner { data, algorithm -> javaSoftwareKeyScope.future { signer.sign(data, algorithm.toKotlin()) } }
    }

    override fun verifier(): JavaVerifier? = key.capabilities.verifier?.let { verifier ->
        JavaVerifier { data, signature, algorithm ->
            javaSoftwareKeyScope.future { verifier.verify(data, signature, algorithm.toKotlin()) }
        }
    }
}
