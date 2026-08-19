package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.commons.config.list.TransactionDataProfile
import id.walt.commons.config.list.TransactionDataProfilesConfig
import id.walt.commons.featureflag.FeatureManager
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Lists configured OpenID4VP transaction-data type profiles when the
 * `transaction-data-profiles` feature is enabled.
 */
fun Route.registerTransactionDataProfilesRoute() {
    if (!FeatureManager.isFeatureEnabled(OSSWallet2FeatureCatalog.transactionDataProfilesFeature)) return

    get("transaction-data-profiles", {
        tags = listOf("Transaction Data")
        summary = "List available transaction data type profiles"
        response { HttpStatusCode.OK to { body<List<TransactionDataProfile>>() } }
    }) {
        val config = ConfigManager.getConfig<TransactionDataProfilesConfig>()
        call.respond(config.transactionDataProfiles)
    }
}
