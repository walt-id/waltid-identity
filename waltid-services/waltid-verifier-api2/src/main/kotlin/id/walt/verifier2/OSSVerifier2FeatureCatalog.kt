package id.walt.verifier2

import id.walt.commons.config.list.DevModeConfig
import id.walt.commons.featureflag.BaseFeature
import id.walt.commons.featureflag.OptionalFeature
import id.walt.commons.featureflag.ServiceFeatureCatalog
import id.walt.commons.config.list.TransactionDataProfilesConfig

object OSSVerifier2FeatureCatalog : ServiceFeatureCatalog {

    val verifierService =
        BaseFeature("verifier-service", "Verifier Service Implementation", OSSVerifier2ServiceConfig::class)

    val devModeFeature = OptionalFeature("dev-mode", "Development mode", DevModeConfig::class, default = false)

    val transactionDataProfilesFeature = OptionalFeature(
        name = "transaction-data-profiles",
        description = "Transaction data type profiles for OpenID4VP",
        config = TransactionDataProfilesConfig::class,
        default = true
    )

    override val baseFeatures = listOf(verifierService)
    override val optionalFeatures: List<OptionalFeature> = listOf(
        devModeFeature,
        transactionDataProfilesFeature,
    )
}
