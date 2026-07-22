@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import id.walt.cose.defaultCoseSignatureAlgorithm
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.preferredJwsAlgorithm
import id.walt.crypto2.jose.supportsJwsAlgorithm
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.verifier2.data.DcApiAnnexCFlowSetup
import id.walt.verifier2.data.DcApiAnnexDFlowSetup
import id.walt.verifier2.data.UrlBearingDeviceFlowSetup
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object OSSVerifier2Manager {
    private val config get() = ConfigManager.getConfig<OSSVerifier2ServiceConfig>()
    private val crypto2Runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
    private val keyMutex = Mutex()
    private var cachedSigningKey: ConfiguredSigningKey? = null

    suspend fun initialize() {
        configuredSigningKey()
    }

    suspend fun createVerificationSession(setup: VerificationSessionSetup): Verification2Session {
        val inlineLegacyKey = setup.core.key?.key
        val configuredKey = if (inlineLegacyKey == null) configuredSigningKey() else null
        val clientId = setup.core.clientId ?: config.clientId
        val clientMetadata = setup.core.clientMetadata ?: config.clientMetadata
        val urlPrefix = if (setup is UrlBearingDeviceFlowSetup) setup.urlConfig.urlPrefix ?: config.urlPrefix else null
        val urlHost = when (setup) {
            is UrlBearingDeviceFlowSetup -> setup.urlConfig.urlHost ?: config.urlHost
            is DcApiAnnexDFlowSetup -> setup.expectedOrigins.firstOrNull()
                ?: throw IllegalArgumentException("Missing expected origins (at '$.expectedOrigins')")
            is DcApiAnnexCFlowSetup -> setup.origin
        }
        val x5c = setup.core.x5c ?: config.x5c
        return configuredKey?.let {
            VerificationSessionCreator.createVerificationSession(
                setup = setup,
                clientId = clientId,
                clientMetadata = clientMetadata,
                urlPrefix = urlPrefix,
                urlHost = urlHost,
                key = it.key,
                x5c = x5c,
                jwsAlgorithm = it.jwsAlgorithm,
                coseAlgorithm = it.coseAlgorithm,
                signingKeyReference = it.reference,
            )
        } ?: VerificationSessionCreator.createVerificationSession(
            setup = setup,
            clientId = clientId,
            clientMetadata = clientMetadata,
            urlPrefix = urlPrefix,
            urlHost = urlHost,
            key = inlineLegacyKey,
            x5c = x5c,
        )
    }

    suspend fun resolveRequestSigningKey(session: Verification2Session): Key? =
        session.setup.core.key?.key ?: if (session.requestSigningKeyReference == null) {
            config.key?.let { KeyManager.resolveSerializedKey(it) }
        } else null

    suspend fun resolveCrypto2RequestSigningKey(session: Verification2Session): Crypto2Key? {
        val configured = configuredSigningKey() ?: return null
        return configured.key.takeIf { session.requestSigningKeyReference == configured.reference }
    }

    private suspend fun configuredSigningKey(): ConfiguredSigningKey? {
        val storedSource = config.requestSigningStoredKey
        val legacySource = config.key?.toString()
        if (storedSource == null && legacySource == null) return null
        val source = ConfiguredSigningKeySource(storedSource, legacySource)
        cachedSigningKey?.takeIf { it.source == source }?.let { return it }
        return keyMutex.withLock {
            cachedSigningKey?.takeIf { it.source == source } ?: run {
                val stored = if (storedSource != null) {
                    StoredKeyCodec.decodeFromString(storedSource)
                } else {
                    val legacyKey = KeyManager.resolveSerializedKey(requireNotNull(legacySource))
                    V1KeyMigration().migrate(
                        recordId = KeyId(legacyKey.getKeyId()),
                        serialized = legacySource,
                        usages = requestSigningUsages,
                    )
                }
                require(stored.usages == requestSigningUsages) {
                    "Verifier request-signing key usages must be exactly $requestSigningUsages"
                }
                val key = crypto2Runtime.restore(stored)
                require(key.usages == requestSigningUsages) {
                    "Verifier request-signing key usages must be exactly $requestSigningUsages"
                }
                requireNotNull(key.capabilities.signer) { "Verifier request-signing key cannot sign" }
                requireNotNull(key.capabilities.verifier) { "Verifier request-signing key cannot verify" }
                val legacyKey = legacySource?.let { KeyManager.resolveSerializedKey(it) }
                legacyKey?.let { validateLegacySidecar(it, key) }
                ConfiguredSigningKey(
                    source = source,
                    key = key,
                    reference = "config:${key.id.value}",
                    jwsAlgorithm = legacyKey?.let { JwsAlgorithm.parse(it.keyType.jwsAlg) }
                        ?: key.preferredJwsAlgorithm(),
                    coseAlgorithm = key.spec.defaultCoseSignatureAlgorithm(),
                ).also { cachedSigningKey = it }
            }
        }
    }

    private suspend fun validateLegacySidecar(legacyKey: Key, crypto2Key: Crypto2Key) {
        val algorithm = JwsAlgorithm.parse(legacyKey.keyType.jwsAlg)
        require(crypto2Key.id.value == legacyKey.getKeyId()) {
            "Verifier requestSigningStoredKey ID does not match key"
        }
        require(crypto2Key.spec.supportsJwsAlgorithm(algorithm)) {
            "Verifier requestSigningStoredKey algorithm does not match key"
        }
        val legacyPublicJwk = EncodedKey.Jwk(
            data = BinaryData(Json.encodeToString(legacyKey.getPublicKey().exportJWKObject()).encodeToByteArray()),
            privateMaterial = false,
        )
        val crypto2PublicJwk = requireNotNull(crypto2Key.capabilities.publicKeyExporter) {
            "Verifier requestSigningStoredKey does not export public material"
        }.exportPublicKey().toPublicJwk(crypto2Key.spec)
        require(Jwk.sha256Thumbprint(legacyPublicJwk) == Jwk.sha256Thumbprint(crypto2PublicJwk)) {
            "Verifier requestSigningStoredKey does not match key"
        }
    }

    private data class ConfiguredSigningKey(
        val source: ConfiguredSigningKeySource,
        val key: Crypto2Key,
        val reference: String,
        val jwsAlgorithm: JwsAlgorithm,
        val coseAlgorithm: Int,
    )

    private data class ConfiguredSigningKeySource(
        val storedKey: String?,
        val legacyKey: String?,
    )

    private val requestSigningUsages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
}
