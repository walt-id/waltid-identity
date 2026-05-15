package id.walt.wallet2

import id.walt.commons.config.list.DevModeConfig
import id.walt.commons.featureflag.BaseFeature
import id.walt.commons.featureflag.OptionalFeature
import id.walt.commons.featureflag.ServiceFeatureCatalog

object OSSWallet2FeatureCatalog : ServiceFeatureCatalog {

    val walletService =
        BaseFeature("wallet-service", "Wallet Service Configuration", OSSWallet2ServiceConfig::class)

    val devModeFeature =
        OptionalFeature("dev-mode", "Development mode (disables authentication)", DevModeConfig::class, default = false)

    val authFeature =
        OptionalFeature("auth", "User authentication via waltid-ktor-authnz", OSSWallet2AuthConfig::class, default = false)

    override val baseFeatures = listOf(walletService)
    override val optionalFeatures: List<OptionalFeature> = listOf(devModeFeature, authFeature)
}
