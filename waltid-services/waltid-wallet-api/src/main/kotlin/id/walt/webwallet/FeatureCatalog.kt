package id.walt.webwallet

import id.walt.commons.featureflag.BaseFeature
import id.walt.commons.featureflag.OptionalFeature
import id.walt.commons.featureflag.ServiceFeatureCatalog
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.webwallet.config.*
import kotlinx.coroutines.runBlocking

object FeatureCatalog : ServiceFeatureCatalog {

    val databaseFeature = BaseFeature("db", "Database manager", DatasourceConfiguration::class)

    // val loginsMethodFeature = BaseFeature("logins", "Logins method management", LoginMethodsConfig::class)
    val authenticationFeature = BaseFeature("auth", "Base authentication system", AuthConfig::class)

    val tenantFeature = OptionalFeature("tenant", "Cloud-based tenant management", TenantConfig::class, false)
    val pushFeature = OptionalFeature("push", "Push notifications", PushConfig::class, false)

    val web3 = OptionalFeature("web3", "Web3 account management", default = false)

    val runtimeMockFeature = OptionalFeature("runtime", "Runtime mock provider configuration", RuntimeConfig::class, false)

    val oidcAuthenticationFeature = OptionalFeature("oidc", "OIDC login feature", OidcConfiguration::class, false)
    val silentExchange = OptionalFeature(
        "silent-exchange", "Silent exchange",
        configs = mapOf(
            "trust" to TrustConfig::class,
            "notification" to NotificationConfig::class
        ),
        default = false
    )
    val rejectionReasonsFeature = OptionalFeature("rejectionreason", "Rejection reasons use case", RejectionReasonConfig::class, false)

    val registrationDefaultsFeature =
        OptionalFeature("registration-defaults", "Registration defaults (key, did) configuration", RegistrationDefaultsConfig::class, true)
    val keyGenerationDefaultsFeature = OptionalFeature(
        "key-generation-defaults",
        "Key generation defaults (key backend & generation config) configuration",
        KeyGenerationDefaultsConfig::class,
        true
    )

    val didWebRegistry = OptionalFeature("did-web-registry", "Enables the automatic did:web registry", default = true)

    override val baseFeatures = listOf(
        databaseFeature,
//        loginsMethodFeature,
        authenticationFeature
    )
    override val optionalFeatures = listOf(
        web3,
        tenantFeature,
        pushFeature,
        runtimeMockFeature,
        oidcAuthenticationFeature,
        silentExchange,
        rejectionReasonsFeature,
        registrationDefaultsFeature,
        keyGenerationDefaultsFeature,
        runtimeMockFeature,
        didWebRegistry
    )
}

fun main() = runBlocking {
    DidService.minimalInit()
    val type = KeyType.secp256r1
    val key = KeyManager.createKey(KeyGenerationRequest(backend = "jwk", keyType = type))
    println("Generated kid: ${key.getKeyId()}")
    val did = DidService.registerByKey("key", key, DidKeyCreateOptions()).did
    println("Generated did: $did")
    val resolved = DidService.resolveToKey(did)
    println("Resolved key: $resolved")
}