package id.walt.wallet2

import id.walt.commons.config.list.registerTransactionDataProfileDiscoveryRoutes
import id.walt.commons.featureflag.FeatureManager
import io.ktor.server.routing.Route

/**
 * Lists OpenID4VP transaction-data type profiles when the
 * `transaction-data-profiles` feature is enabled. Runtime mutations live on
 * Enterprise Wallet2/Verifier2 services, not on Community Wallet API v2.
 */
fun Route.registerTransactionDataProfilesRoute() {
    if (!FeatureManager.isFeatureEnabled(OSSWallet2FeatureCatalog.transactionDataProfilesFeature)) return
    registerTransactionDataProfileDiscoveryRoutes()
}
