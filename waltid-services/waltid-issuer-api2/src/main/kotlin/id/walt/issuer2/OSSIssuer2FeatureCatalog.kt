package id.walt.issuer2

import id.walt.commons.config.list.DevModeConfig
import id.walt.commons.featureflag.BaseFeature
import id.walt.commons.featureflag.OptionalFeature
import id.walt.commons.featureflag.ServiceFeatureCatalog
import id.walt.issuer2.config.CredentialProfilesConfig
import id.walt.issuer2.config.OSSIssuer2ServiceConfig

object OSSIssuer2FeatureCatalog : ServiceFeatureCatalog {

    val issuerService = BaseFeature(
        "issuer-service",
        "Issuer Service Implementation",
        OSSIssuer2ServiceConfig::class
    )

    val profiles = BaseFeature(
        "profiles",
        "Credential Profiles Configuration",
        CredentialProfilesConfig::class
    )

    val devModeFeature = OptionalFeature(
        "dev-mode",
        "Development mode",
        DevModeConfig::class,
        default = false
    )

    override val baseFeatures = listOf(issuerService, profiles)
    override val optionalFeatures = listOf(devModeFeature)
}
