package id.walt.crypto2.signum

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyDeletionResult
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.serialization.StoredKeyCodec
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

suspend fun exercisePlatformSignumBackend(
    firstBackend: SignumPlatformBackend,
    restartedBackend: SignumPlatformBackend,
) {
    val alias = "waltid-crypto2-platform-test"
    try {
        firstBackend.delete(alias)
    } catch (_: Throwable) {
    }
    val firstProvider = SignumManagedKeyProvider(firstBackend)
    val generated = CryptoRuntime(emptyList(), managedProviders = listOf(firstProvider)).generateManagedKey(
        firstProvider.id,
        GenerateManagedKeyRequest(
            id = KeyId(alias),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            providerOptions = SignumKeyOptions(alias = alias).encode(),
        )
    )
    val stored = StoredKeyCodec.decodeFromString(StoredKeyCodec.encodeToString(generated.storedKey))
    val restartedProvider = SignumManagedKeyProvider(restartedBackend)
    val restored = CryptoRuntime(emptyList(), managedProviders = listOf(restartedProvider)).restore(stored)
    val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.IEEE_P1363)
    val message = "platform-backed message".encodeToByteArray()
    val signature = assertNotNull(restored.capabilities.signer).sign(message, algorithm)

    assertEquals(firstBackend.id, restartedBackend.id)
    assertTrue(assertNotNull(restored.capabilities.verifier).verify(signature, message, algorithm))
    assertEquals(KeyDeletionResult.Deleted, assertNotNull(restored.capabilities.deleter).delete())
    val restoreFailure = try {
        restartedProvider.restore(generated.storedKey)
        null
    } catch (cause: Throwable) {
        cause
    }
    assertIs<IllegalStateException>(restoreFailure)
}
