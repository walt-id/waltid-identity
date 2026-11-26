package id.walt.wallet2

import id.walt.commons.config.list.DevModeConfig
import id.walt.verifier.openid.transactiondata.TransactionDataProfilesConfig
import id.walt.commons.featureflag.BaseFeature
import id.walt.commons.featureflag.CommonsFeatureCatalog
import id.walt.commons.featureflag.OptionalFeature
import id.walt.commons.featureflag.ServiceFeatureCatalog
import id.walt.wallet2.persistence.Wallet2PersistenceConfig

object OSSWallet2FeatureCatalog : ServiceFeatureCatalog {

    val walletService =
        BaseFeature("wallet-service", "Wallet Service Configuration", OSSWallet2ServiceConfig::class)

    val authFeature =
        OptionalFeature("auth", "User authentication via waltid-ktor-authnz", OSSWallet2AuthConfig::class, default = false)

    val devModeFeature = OptionalFeature("dev-mode", "Development mode", DevModeConfig::class, default = false)

    /**
     * When enabled, wallet and auth account data are persisted to SQL (SQLite by default, Postgres optional).
     * Configure through the `wallet2-persistence.conf` file.
     * When disabled (default), an in-memory store is used - data is lost on restart.
     */
    val persistenceFeature =
        OptionalFeature("wallet2-persistence", "SQL persistence for wallets, credentials, keys, DIDs and auth accounts", Wallet2PersistenceConfig::class, default = false)

    val transactionDataProfilesFeature = OptionalFeature(
        name = "transaction-data-profiles",
        description = "Transaction data type profiles for OpenID4VP",
        config = TransactionDataProfilesConfig::class,
        default = true,
    )

    override val baseFeatures = listOf(walletService, CommonsFeatureCatalog.networkFetchGuardFeature)
    override val optionalFeatures: List<OptionalFeature> = listOf(
        devModeFeature,
        authFeature,
        persistenceFeature,
        transactionDataProfilesFeature,
    )
}
