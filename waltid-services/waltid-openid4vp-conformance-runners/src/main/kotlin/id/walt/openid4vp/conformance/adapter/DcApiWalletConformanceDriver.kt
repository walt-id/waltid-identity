package id.walt.openid4vp.conformance.adapter

import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.handlers.PresentationCredentialSelection
import id.walt.wallet2.handlers.PreviewDcApiPresentationRequest
import id.walt.wallet2.handlers.SubmitDcApiPresentationRequest
import id.walt.wallet2.handlers.WalletPresentationHandler
import id.waltid.openid4vp.wallet.DcApiCredentialResponse
import kotlinx.serialization.json.JsonObject

/**
 * Runs the real Wallet2 DC API request/presentation handlers behind a simulated platform boundary.
 * The caller origin comes from the harness's suite configuration, never from the request payload.
 * This is SDK protocol evidence; it does not exercise a browser, OS picker or wallet UI.
 */
class DcApiWalletConformanceDriver(
    private val wallet: Wallet,
    private val trust: ClientIdTrustConfiguration,
) {
    private val transactionTypes = TransactionDataTypeRegistry()

    suspend fun present(protocol: String, data: JsonObject, origin: String): DcApiCredentialResponse {
        val preview = WalletPresentationHandler.previewDcApiPresentation(
            wallet, PreviewDcApiPresentationRequest(protocol, data, origin),
            transactionDataTypeRegistry = transactionTypes,
            clientIdTrustConfiguration = trust,
        )
        val available = preview.credentialOptions.groupBy { it.queryId }
        val requiredQueries = preview.credentialRequirements.flatMap { requirement ->
            requirement.options.firstOrNull { option -> option.all { it in available } }
                ?: error("Wallet preview has no satisfiable credential alternative")
        }.toSet()
        val selected = requiredQueries.ifEmpty { setOf(available.keys.first()) }.map { queryId ->
            PresentationCredentialSelection(queryId, available.getValue(queryId).first().credentialId)
        }
        return WalletPresentationHandler.submitDcApiPresentation(
            wallet, SubmitDcApiPresentationRequest(preview.requestId, selected),
            transactionDataTypeRegistry = transactionTypes,
        )
    }
}
