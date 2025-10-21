package id.walt.webwallet

import id.walt.commons.featureflag.BaseFeature
import id.walt.commons.featureflag.OptionalFeature
import id.walt.commons.featureflag.ServiceFeatureCatalog
import id.walt.webwallet.config.*

object FeatureCatalog : ServiceFeatureCatalog {

    val databaseFeature = BaseFeature("db", "Database manager", DatasourceConfiguration::class)

    val devModeFeature = OptionalFeature("dev-mode", "Development mode", default = false)

    val legacyAuthenticationFeature = OptionalFeature("auth", "Legacy authentication system", AuthConfig::class, true)
    val ktorAuthnzAuthenticationFeature =
        OptionalFeature("ktor-authnz", "waltid-ktor-authnz authentication system", KtorAuthnzConfig::class, false)
    // val loginsMethodFeature = BaseFeature("logins", "Logins method management", LoginMethodsConfig::class)


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

    val x5cAuthFeature = OptionalFeature(
        name = "trusted-ca",
        description = "Trusted CA configuration",
        config = TrustedCAConfig::class,
        default = false
    )

    val externalSignatureEndpointsFeature = OptionalFeature(
        name = "external-signature-endpoints",
        description = "Expose endpoints that allow clients to provide signed payloads required in OID4VC flows",
        default = false,
    )

    val stopwatchFeature = OptionalFeature(
        name = "stopwatch",
        description = "Enables the stopwatch for certain requests - at the moment the stopwatch is only used in integration-tests",
        default = false
    )

    override val baseFeatures = listOf(
        databaseFeature
    )
    override val optionalFeatures = listOf(
        devModeFeature,
        stopwatchFeature,

        legacyAuthenticationFeature,
        ktorAuthnzAuthenticationFeature,

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
        didWebRegistry,
        x5cAuthFeature,
        externalSignatureEndpointsFeature,
    )
}
