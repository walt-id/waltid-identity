package id.walt.verifier2

import id.walt.commons.config.NetworkFetchGuardConfig
import id.walt.commons.config.list.DevModeConfig
import id.walt.commons.featureflag.BaseFeature
import id.walt.commons.featureflag.OptionalFeature
import id.walt.commons.featureflag.ServiceFeatureCatalog
import id.walt.verifier.openid.transactiondata.TransactionDataProfilesConfig

object OSSVerifier2FeatureCatalog : ServiceFeatureCatalog {

    val verifierService =
        BaseFeature("verifier-service", "Verifier Service Implementation", OSSVerifier2ServiceConfig::class)

    val networkFetchGuard = BaseFeature(
        "network-fetch-guard",
        "Private-network guard for outbound webhooks",
        NetworkFetchGuardConfig::class,
    )

    val devModeFeature = OptionalFeature("dev-mode", "Development mode", DevModeConfig::class, default = false)

    val transactionDataProfilesFeature = OptionalFeature(
        name = "transaction-data-profiles",
        description = "Transaction data type profiles for OpenID4VP",
        config = TransactionDataProfilesConfig::class,
        default = true
    )

    override val baseFeatures = listOf(verifierService, networkFetchGuard)
    override val optionalFeatures: List<OptionalFeature> = listOf(
        devModeFeature,
        transactionDataProfilesFeature,
    )
}
