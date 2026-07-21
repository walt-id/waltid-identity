package id.walt.wallet2.persistence.keys

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.fragment.app.FragmentActivity
import id.walt.crypto.AndroidKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.crypto.keys.KeyUseAuthorizationPrompt
import java.lang.ref.WeakReference
import kotlin.uuid.Uuid

/**
 * [PlatformKeyProvider] implementation backed by Android KeyStore.
 */
public class AndroidPlatformKeyProvider private constructor(
    private val applicationContext: Context?,
    private val authorizationPrompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(),
    private val interactionContextProvider: () -> FragmentActivity?,
) : PlatformKeyProvider {
    /**
     * Creates a provider scoped to [context]. A supplied activity is weakly referenced and becomes
     * unavailable after destruction; use the provider-based constructor for wallets retained across recreation.
     */
    public constructor(
        context: Context? = null,
        authorizationPrompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(),
    ) : this(
        applicationContext = context?.applicationContext,
        authorizationPrompt = authorizationPrompt,
        interactionContextProvider = weakInteractionContextProvider(context as? FragmentActivity),
    )

    /**
     * Creates a provider that resolves the current prompt-hosting activity for every protected key operation.
     */
    public constructor(
        context: Context,
        interactionContextProvider: () -> FragmentActivity?,
        authorizationPrompt: KeyUseAuthorizationPrompt = KeyUseAuthorizationPrompt(),
    ) : this(
        applicationContext = context.applicationContext,
        authorizationPrompt = authorizationPrompt,
        interactionContextProvider = interactionContextProvider,
    )

    private val interactionContext: FragmentActivity?
        get() = interactionContextProvider().takeIf { it.canHostBiometricPrompt() }

    /**
     * Android platform-backed key types supported by this provider.
     */
    override val supportedPlatformKeyTypes: Set<KeyType> =
        PlatformKeyProvider.DEFAULT_SUPPORTED_PLATFORM_KEY_TYPES

    /**
     * Generates an Android platform-backed key for supported types, otherwise a software key.
     */
    override suspend fun generateKey(keyType: KeyType, keyId: String?): Key {
        return generateKey(PlatformKeyGenerationRequest(keyType = keyType, keyId = keyId))
    }

    override suspend fun generateKey(request: PlatformKeyGenerationRequest): Key {
        val capability = capability(request.keyType, request.keyUseAuthorizationPolicy)
        if (!capability.supported) {
            throw KeyUseAuthorizationException(
                failure = capability.failure ?: KeyUseAuthorizationFailure.UnsupportedCombination,
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
        return if (isPlatformBacked(request.keyType)) {
            AndroidKey.Platform.create(options)
        } else {
            AndroidKey.Software.create(options)
        }
    }

    /**
     * Loads an Android platform-backed key by alias and expected key type.
     */
    override suspend fun loadKey(keyId: String, keyType: KeyType): Key? =
        loadKey(keyId, keyType, KeyUseAuthorizationPolicy.None)

    override suspend fun loadKey(
        keyId: String,
        keyType: KeyType,
        keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy,
    ): Key? {
        val options = AndroidKey.Options(
            kid = keyId,
            keyType = keyType,
            keyUseAuthorizationPolicy = keyUseAuthorizationPolicy,
            authorizationPrompt = authorizationPrompt,
            interactionContextProvider = interactionContextProvider,
        )
        return if (keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.None) {
            runCatching { AndroidKey.Platform.load(options) }.getOrNull()
        } else {
            AndroidKey.Platform.load(options)
        }
    }

    override suspend fun capability(
        keyType: KeyType,
        keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy,
    ): PlatformKeyCapability {
        if (keyUseAuthorizationPolicy == KeyUseAuthorizationPolicy.None) {
            val supported = keyType in supportedPlatformKeyTypes ||
                keyType in PlatformKeyProvider.DEFAULT_SUPPORTED_SOFTWARE_KEY_TYPES
            return PlatformKeyCapability(
                platform = PlatformKeyPlatform.Android,
                keyType = keyType,
                keyUseAuthorizationPolicy = keyUseAuthorizationPolicy,
                supported = supported,
                platformBackingAvailable = isPlatformBacked(keyType),
                secureHardwareRequired = false,
                secureHardwareAvailable = null,
                failure = KeyUseAuthorizationFailure.UnsupportedCombination.takeUnless { supported },
            )
        }

        val failure = when {
            keyType != KeyType.secp256r1 -> KeyUseAuthorizationFailure.UnsupportedCombination
            interactionContext == null -> KeyUseAuthorizationFailure.InteractionContextUnavailable
            applicationContext == null -> KeyUseAuthorizationFailure.BiometricUnavailable
            else -> when (BiometricManager.from(applicationContext).canAuthenticate(BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> null
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> KeyUseAuthorizationFailure.BiometricNotEnrolled
                else -> KeyUseAuthorizationFailure.BiometricUnavailable
            }
        }

        return PlatformKeyCapability(
            platform = PlatformKeyPlatform.Android,
            keyType = keyType,
            keyUseAuthorizationPolicy = keyUseAuthorizationPolicy,
            supported = failure == null,
            platformBackingAvailable = true,
            secureHardwareRequired = false,
            secureHardwareAvailable = null,
            failure = failure,
        )
    }

    /**
     * Loads an Android software key from serialized JWK material.
     */
    override suspend fun loadSoftwareKey(keyId: String, keyType: KeyType, jwkMaterial: ByteArray): Key? = runCatching {
        AndroidKey.Software.load(AndroidKey.Options(kid = keyId, keyType = keyType), jwkMaterial)
    }.getOrNull()

    /**
     * Exports serialized JWK material from an Android software key.
     */
    override suspend fun exportSoftwareKeyMaterial(key: Key): ByteArray {
        require(key is AndroidKey.Software) { "Can only export material from Software keys" }
        return AndroidKey.Software.exportKeyMaterial(key)
    }

    /**
     * Deletes an Android platform-backed key by alias and expected key type.
     */
    override suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean = runCatching {
        if (isPlatformBacked(keyType)) {
            AndroidKey.Platform.delete(keyId)
        }
    }.isSuccess
}

private fun weakInteractionContextProvider(activity: FragmentActivity?): () -> FragmentActivity? {
    val reference = activity?.let(::WeakReference)
    return { reference?.get() }
}

private fun FragmentActivity?.canHostBiometricPrompt(): Boolean =
    this != null && !isFinishing && !isDestroyed && !isChangingConfigurations
