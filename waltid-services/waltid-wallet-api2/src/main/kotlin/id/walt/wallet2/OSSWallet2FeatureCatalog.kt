package id.walt.wallet2

import id.walt.commons.config.list.DevModeConfig
import id.walt.commons.featureflag.BaseFeature
import id.walt.commons.featureflag.OptionalFeature
import id.walt.commons.featureflag.ServiceFeatureCatalog
import id.walt.wallet2.persistence.Wallet2PersistenceConfig

object OSSWallet2FeatureCatalog : ServiceFeatureCatalog {

    val walletService =
        BaseFeature("wallet-service", "Wallet Service Configuration", OSSWallet2ServiceConfig::class)

    val devModeFeature =
        OptionalFeature("dev-mode", "Development mode (disables authentication)", DevModeConfig::class, default = false)

    val authFeature =
        OptionalFeature("auth", "User authentication via waltid-ktor-authnz", OSSWallet2AuthConfig::class, default = false)

    /**
     * When enabled, wallet data is persisted to a SQL database (SQLite by default, Postgres optional).
     * Configure via the `wallet2-persistence` HOCON block.
     * When disabled (default), an in-memory store is used — data is lost on restart.
     */
    val persistenceFeature =
        OptionalFeature("wallet2-persistence", "SQL persistence for wallets, credentials, keys and DIDs", Wallet2PersistenceConfig::class, default = false)

    override val baseFeatures = listOf(walletService)
    override val optionalFeatures: List<OptionalFeature> = listOf(devModeFeature, authFeature, persistenceFeature)
}
