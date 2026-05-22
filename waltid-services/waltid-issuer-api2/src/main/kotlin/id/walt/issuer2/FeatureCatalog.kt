package id.walt.issuer2

import id.walt.commons.config.list.DevModeConfig
import id.walt.commons.featureflag.BaseFeature
import id.walt.commons.featureflag.OptionalFeature
import id.walt.commons.featureflag.ServiceFeatureCatalog
import id.walt.issuer2.config.AuthenticationServiceConfig
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig

object FeatureCatalog : ServiceFeatureCatalog {

    /*
     * Keep the issuer1 feature name for compatibility with existing service configuration
     * and operator expectations. In service-commons, enabling a feature also registers
     * and loads every config file listed in its config map.
     *
     * Issuer1 uses this feature to load credential-issuer-metadata.conf. Issuer2 still
     * needs that metadata, but also needs issuer2-profiles.conf because profiles are the
     * config-backed deployment unit that links metadata entries, issuer keys/DIDs, claims
     * mapping, mDOC namespace mapping, and webhook behavior.
     */
    private val credentialTypes = BaseFeature(
        "credential-types",
        "Configure the credential types available in this issuer instance",
        mapOf(
            "credential-issuer-metadata" to Issuer2MetadataConfig::class,
            "issuer2-profiles" to Issuer2ProfilesConfig::class,
        )
    )

    private val issuerService = BaseFeature(
        "issuer-service",
        "Issuer Service Implementation",
        Issuer2ServiceConfig::class
    )

    private val authenticationService = BaseFeature(
        "authentication-service",
        "Authentication Service Implementation",
        AuthenticationServiceConfig::class
    )

    val entra = OptionalFeature("entra", "Enable support for Microsoft Entra", default = false)

    val devModeFeature = OptionalFeature("dev-mode", "Development mode", DevModeConfig::class, default = false)

    override val baseFeatures = listOf(credentialTypes, issuerService, authenticationService)
    override val optionalFeatures = listOf(entra, devModeFeature)

}
