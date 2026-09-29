package id.walt.wallet2.server.handlers

import id.walt.wallet2.handlers.CredentialIssuanceStage
import id.walt.wallet2.handlers.FetchCredentialResult
import id.walt.wallet2.handlers.PollDeferredResult
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.handlers.ReceiveCredentialResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/** Preserve committed progress in the response body even when a later target fails. */
suspend fun ApplicationCall.respondIssuanceResult(result: ReceiveCredentialResult) {
    val failure = result.failure
    val status = when {
        failure == null -> HttpStatusCode.OK
        result.credentialIds.isNotEmpty() || result.deferredCredentials.isNotEmpty() ||
            result.storageOutcome?.deferredCredentials?.isNotEmpty() == true -> HttpStatusCode.MultiStatus
        else -> when (failure.stage) {
            CredentialIssuanceStage.PROOF -> HttpStatusCode.UnprocessableEntity
            CredentialIssuanceStage.REQUEST, CredentialIssuanceStage.RESPONSE -> HttpStatusCode.BadGateway
            CredentialIssuanceStage.STORAGE, CredentialIssuanceStage.OBSERVER -> HttpStatusCode.InternalServerError
        }
    }
    respond(status, result)
}

/** The issuer response remains available when storing an isolated fetch is only partly complete. */
suspend fun ApplicationCall.respondFetchCredentialResult(result: FetchCredentialResult) {
    respond(storageOutcomeStatus(result.storageOutcome), result)
}

/** Preserve local recovery after an isolated deferred response was already received. */
suspend fun ApplicationCall.respondPollDeferredResult(result: PollDeferredResult) {
    respond(storageOutcomeStatus(result.storageOutcome), result)
}

private fun storageOutcomeStatus(outcome: WalletIssuanceOutcome?): HttpStatusCode {
    val failed = outcome as? WalletIssuanceOutcome.Failed ?: return HttpStatusCode.OK
    return if (failed.storedCredentialIds.isNotEmpty() || failed.deferredCredentials.isNotEmpty())
        HttpStatusCode.MultiStatus else HttpStatusCode.InternalServerError
}
