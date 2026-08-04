package id.walt.wallet2.persistence.keys

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import id.walt.crypto.AndroidKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.crypto.keys.KeyUseAuthorizationPrompt
import id.walt.wallet2.persistence.stores.MobileWalletKeyRecord
import kotlin.uuid.Uuid

/** Android Keystore-backed mobile key provider. */
public class AndroidPlatformKeyProvider private constructor(
    private val applicationContext: Context,
    private val authorizationPrompt: KeyUseAuthorizationPrompt,
    private val interactionContextProvider: () -> FragmentActivity?,
) : PlatformKeyProvider {
    /** The activity is resolved at operation time; it is not retained across recreation. */
    public constructor(
        context: Context,
        interactionContextProvider: () -> FragmentActivity?,
        authorizationPrompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(),
    ) : this(context.applicationContext, authorizationPrompt, interactionContextProvider)

    private val interactionContext: FragmentActivity?
        get() = interactionContextProvider().takeIf { it.canHostBiometricPrompt() }

    /** Checks Android support for the exact request without creating a key. */
    override suspend fun preflight(request: PlatformKeyRequest): PlatformKeyPreflight {
        if (request.keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.None) {
            val supported = request.keyType in PlatformKeyProvider.DEFAULT_SUPPORTED_PLATFORM_KEY_TYPES ||
                request.keyType in PlatformKeyProvider.DEFAULT_SUPPORTED_SOFTWARE_KEY_TYPES
            return PlatformKeyPreflight(supported, KeyUseAuthorizationFailure.UnsupportedCombination.takeUnless { supported })
        }
        val failure = when {
            request.keyType != KeyType.secp256r1 -> KeyUseAuthorizationFailure.UnsupportedCombination
            interactionContext == null -> KeyUseAuthorizationFailure.InteractionContextUnavailable
            else -> when (BiometricManager.from(applicationContext).canAuthenticate(BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> null
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> KeyUseAuthorizationFailure.BiometricNotEnrolled
                else -> KeyUseAuthorizationFailure.BiometricUnavailable
            }
        }
        return PlatformKeyPreflight(failure == null, failure)
    }

    /** Generates an Android Keystore or software key according to the exact request. */
    override suspend fun generate(request: PlatformKeyRequest): GeneratedPlatformKey {
        val preflight = preflight(request)
        if (!preflight.supported) {
            throw KeyUseAuthorizationException(
                failure = preflight.failure ?: KeyUseAuthorizationFailure.UnsupportedCombination,
                message = "Android cannot enforce ${request.keyUseAuthorizationPolicy} for ${request.keyType}",
            )
        }
        val alias = request.keyId ?: "wallet_key_${Uuid.random()}"
        val options = AndroidKey.Options(
            kid = alias,
            keyType = request.keyType,
            keyUseAuthorizationPolicy = request.keyUseAuthorizationPolicy,
            authorizationPrompt = authorizationPrompt,
            interactionContextProvider = interactionContextProvider,
        )
        val platformBacked = request.keyType in PlatformKeyProvider.DEFAULT_SUPPORTED_PLATFORM_KEY_TYPES
        val key = if (platformBacked) AndroidKey.Platform.create(options) else AndroidKey.Software.create(options)
        return GeneratedPlatformKey(
            key = key,
            record = MobileWalletKeyRecord(
                keyId = alias,
                keyType = request.keyType,
                keyUseAuthorizationPolicy = request.keyUseAuthorizationPolicy,
                isPlatformBacked = platformBacked,
            ),
        )
    }

    /** Loads the Android Keystore key described by [record]. */
    override suspend fun load(record: MobileWalletKeyRecord): Key? {
        val options = AndroidKey.Options(
            kid = record.keyId,
            keyType = record.keyType,
            keyUseAuthorizationPolicy = record.keyUseAuthorizationPolicy,
            authorizationPrompt = authorizationPrompt,
            interactionContextProvider = interactionContextProvider,
        )
        if (!record.isPlatformBacked) return null
        return try {
            AndroidKey.Platform.load(options)
        } catch (failure: Throwable) {
            if (record.keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.None && failure.isMissingPlatformKey()) {
                null
            } else {
                throw failure
            }
        }
    }

    override suspend fun delete(record: MobileWalletKeyRecord) {
        if (record.isPlatformBacked) AndroidKey.Platform.delete(record.keyId)
    }

    override suspend fun loadSoftwareKey(keyId: String, keyType: KeyType, jwkMaterial: ByteArray): Key? = runCatching {
        AndroidKey.Software.load(AndroidKey.Options(kid = keyId, keyType = keyType), jwkMaterial)
    }.getOrNull()

    override suspend fun exportSoftwareKeyMaterial(key: Key): ByteArray {
        require(key is AndroidKey.Software) { "Can only export material from software keys" }
        return AndroidKey.Software.exportKeyMaterial(key)
    }
}

private fun Throwable.isMissingPlatformKey(): Boolean =
    generateSequence(this) { it.cause }.any { it is NoSuchElementException }

private fun FragmentActivity?.canHostBiometricPrompt(): Boolean =
    this != null &&
        !isFinishing &&
        !isDestroyed &&
        !isChangingConfigurations &&
        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
