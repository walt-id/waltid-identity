package id.walt.wallet2

import id.walt.commons.config.list.registerTransactionDataProfileCrudRoutes
import id.walt.commons.featureflag.FeatureManager
import io.ktor.server.routing.Route

/**
 * Lists and mutates OpenID4VP transaction-data type profiles when the
 * `transaction-data-profiles` feature is enabled.
 */
fun Route.registerTransactionDataProfilesRoute() {
    if (!FeatureManager.isFeatureEnabled(OSSWallet2FeatureCatalog.transactionDataProfilesFeature)) return
    registerTransactionDataProfileCrudRoutes()
}
