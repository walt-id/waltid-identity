package id.walt.wallet2.server.handlers

import id.walt.wallet2.handlers.CredentialIssuanceStage
import id.walt.wallet2.handlers.FetchCredentialResult
import id.walt.wallet2.handlers.FetchCredentialsResult
import id.walt.wallet2.handlers.FetchCredentialRequest
import id.walt.wallet2.handlers.PollDeferredRequest
import id.walt.wallet2.handlers.CredentialHolderBinding
import id.walt.wallet2.handlers.PollDeferredResult
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.handlers.ReceiveCredentialResult
import id.walt.wallet2.handlers.ReceiveCredentialsResult
import id.walt.wallet2.handlers.CredentialReceiveException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/** Preserve committed progress in the response body even when a later target fails. */
suspend fun ApplicationCall.respondIssuanceResult(result: ReceiveCredentialResult) = respond(HttpStatusCode.OK, result)

suspend fun ApplicationCall.respondIssuanceResult(result: ReceiveCredentialsResult, legacy: Boolean = false) {
    if (legacy && result.failure == null) {
        val released = try { ReceiveCredentialResult(result) } catch (_: CredentialReceiveException) { null }
        if (released != null) respond(HttpStatusCode.OK, released)
        else respond(HttpStatusCode.Conflict, result)
        return
    }
    val failure = result.failure
    val status = when {
        failure == null -> HttpStatusCode.OK
        (result.credentialIds.isNotEmpty() || result.deferredCredentials.isNotEmpty() ||
            result.storageOutcome?.deferredCredentials?.isNotEmpty() == true) && !legacy -> HttpStatusCode.MultiStatus
        else -> when (failure.stage) {
            CredentialIssuanceStage.PROOF -> HttpStatusCode.UnprocessableEntity
            CredentialIssuanceStage.REQUEST, CredentialIssuanceStage.RESPONSE -> HttpStatusCode.BadGateway
            CredentialIssuanceStage.STORAGE, CredentialIssuanceStage.OBSERVER -> HttpStatusCode.InternalServerError
        }
    }
    respond(status, result)
}

/** Keep released success bodies for requests using only released fields. */
suspend fun ApplicationCall.respondFetchCredentialResult(result: FetchCredentialsResult, request: FetchCredentialRequest) {
    val legacy = request.proofs == null && request.credentialIdentifier == null &&
            request.holderBindings == listOf(CredentialHolderBinding()) &&
            request.tokenType.equals("Bearer", ignoreCase = true) && request.dpopKeyId == null
    if (legacy && result.deferredCredential == null && result.storageOutcome !is WalletIssuanceOutcome.Failed) {
        respond(HttpStatusCode.OK, FetchCredentialResult(result))
    } else {
        respond(if (legacy && result.deferredCredential != null) HttpStatusCode.Conflict
            else storageOutcomeStatus(result.storageOutcome, legacy), result)
    }
}

/** Preserve local recovery after an isolated deferred response was already received. */
suspend fun ApplicationCall.respondPollDeferredResult(result: PollDeferredResult, request: PollDeferredRequest) {
    val legacy = !request.proofRequired && request.credentialIdentifier == null &&
            request.holderBindings == listOf(CredentialHolderBinding()) &&
            request.tokenType.equals("Bearer", ignoreCase = true) && request.dpopKeyId == null
    if (legacy && result.pending == null && result.storageOutcome == null) {
        respond(HttpStatusCode.OK, ReceiveCredentialResult(result.credentialIds))
    } else {
        respond(if (legacy && result.pending != null) HttpStatusCode.Conflict
            else storageOutcomeStatus(result.storageOutcome, legacy), result)
    }
}

private fun storageOutcomeStatus(outcome: WalletIssuanceOutcome?, legacy: Boolean): HttpStatusCode {
    val failed = outcome as? WalletIssuanceOutcome.Failed ?: return HttpStatusCode.OK
    return if (!legacy && (failed.storedCredentialIds.isNotEmpty() || failed.deferredCredentials.isNotEmpty()))
        HttpStatusCode.MultiStatus else HttpStatusCode.InternalServerError
}
