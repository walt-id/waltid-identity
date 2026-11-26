@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.presentation

import kotlinx.serialization.ExperimentalSerializationApi
import id.walt.wallet2.mobile.internal.encodeJsonObject

import id.walt.openid4vp.clientidprefix.prefixes.ClientId
import id.walt.openid4vp.clientidprefix.prefixes.DecentralizedIdentifier
import id.walt.openid4vp.clientidprefix.prefixes.OpenIdFederation
import id.walt.openid4vp.clientidprefix.prefixes.PreRegistered
import id.walt.openid4vp.clientidprefix.prefixes.RedirectUri
import id.walt.openid4vp.clientidprefix.prefixes.Unsupported
import id.walt.openid4vp.clientidprefix.prefixes.VerifierAttestation
import id.walt.openid4vp.clientidprefix.prefixes.X509Hash
import id.walt.openid4vp.clientidprefix.prefixes.X509SanDns
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.wallet2.handlers.PreparedPresentationReview
import id.walt.wallet2.handlers.PresentationCredentialOption as CorePresentationCredentialOption
import id.walt.wallet2.handlers.PresentationCredentialRequirement as CorePresentationCredentialRequirement
import id.walt.wallet2.mobile.credentials.WalletCredential
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import id.waltid.openid4vp.wallet.response.ResponseEncryption
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun CorePresentationCredentialOption.toPresentationCredentialOption(): PresentationCredentialOption =
    PresentationCredentialOption(
        queryId = queryId,
        credential = WalletCredential(
            id = credentialId,
            format = format,
            credentialDataJson = credentialData.encodeJsonObject(),
            issuer = issuer,
            subject = subject,
            label = label,
            metadataJson = metadata?.encodeJsonObject(),
        ),
        multiple = multiple,
        disclosures = disclosures.map { disclosure ->
            PresentationDisclosure(
                path = disclosure.path,
                name = disclosure.name,
                valueJson = Json.encodeToString(JsonElement.serializer(), disclosure.value),
                displayValue = disclosure.value.displayValue(),
                disposition = disclosureDisposition(disclosure.selectivelyDisclosable, disclosure.required, disclosure.selectable, disclosure.requested),
            )
        },
    )

internal fun CorePresentationCredentialRequirement.toPresentationCredentialRequirement(): PresentationCredentialRequirement =
    PresentationCredentialRequirement(options = options)

internal fun WalletPresentResult.toPresentationResult(): PresentationResult =
    verifierResponse?.let { Json.encodeToString(JsonElement.serializer(), it) }.let { responseJson ->
        val responseUrl = getUrl
        val formHtml = formPostHtml
        when {
            responseUrl != null -> {
                require(
                    transmissionSuccess == null &&
                        formHtml == null &&
                        responseJson == null &&
                        redirectTo == null
                ) {
                    "Prepared URL result contains incompatible protocol fields"
                }
                PresentationResult.Prepared.OpenUrl(responseUrl)
            }

            formHtml != null -> {
                require(transmissionSuccess == null && responseJson == null && redirectTo == null) {
                    "Prepared form result contains incompatible protocol fields"
                }
                PresentationResult.Prepared.SubmitForm(formHtml)
            }

            transmissionSuccess == true -> PresentationResult.Transmitted.Succeeded(
                verifierResponseJson = requireNotNull(responseJson) {
                    "Transmitted presentation result is missing the verifier response"
                },
                redirectUrl = redirectTo,
            )

            transmissionSuccess == false -> PresentationResult.Transmitted.Failed(
                verifierResponseJson = requireNotNull(responseJson) {
                    "Failed presentation transmission is missing the verifier response"
                },
            )

            else -> error("Presentation result has no protocol outcome")
        }
    }

internal fun AuthorizationRequest.toPresentationRequest(
    preferredLocales: List<String>,
    resolvedAuthorizationRequest: ResolvedAuthorizationRequest,
    responseEncryption: ResponseEncryption.Metadata? = null,
    transactionData: List<PresentationTransactionData> = emptyList(),
): PresentationRequest {
    val verifiedClientId = requireNotNull(clientId) {
            "A validated presentation request must contain client_id."
        }
    return PresentationRequest(
        clientId = verifiedClientId,
        verifierMetadata = clientMetadata?.toVerifierMetadata(preferredLocales),
        requestAuthentication = resolvedAuthorizationRequest.toPresentationRequestAuthentication(),
        responseUri = responseUri,
        state = state,
        nonce = requireNotNull(nonce) {
            "A validated presentation request must contain nonce."
        },
        responseEncryption = responseEncryption.toPresentationResponseEncryption(),
        transactionData = transactionData,
        responseMode = responseMode?.let { mode ->
            Json.encodeToString(OpenID4VPResponseMode.serializer(), mode).trim('"')
        },
    )
}

internal fun AuthorizationRequest.toPartialPresentationRequest(
    preferredLocales: List<String>,
    resolvedAuthorizationRequest: ResolvedAuthorizationRequest,
): PartialPresentationRequest {
    val verifiedClientId = requireNotNull(clientId) {
            "A reportable invalid presentation request must contain client_id."
        }
    return PartialPresentationRequest(
        clientId = verifiedClientId,
        verifierMetadata = clientMetadata?.toVerifierMetadata(preferredLocales),
        requestAuthentication = resolvedAuthorizationRequest.toPresentationRequestAuthentication(),
        responseUri = responseUri,
        state = state,
        nonce = nonce,
        responseEncryption = null.toPresentationResponseEncryption(),
    )
}

internal fun ResolvedAuthorizationRequest.toPresentationRequestAuthentication(): PresentationRequestAuthentication =
    when (this) {
        is ResolvedAuthorizationRequest.Plain -> PresentationRequestAuthentication.Unauthenticated
        is ResolvedAuthorizationRequest.UnsignedRequestObject -> PresentationRequestAuthentication.Unauthenticated
        is ResolvedAuthorizationRequest.AuthenticatedRequestObject -> PresentationRequestAuthentication.Authenticated(
            compactRequestObject = requestObject,
            algorithm = authentication.algorithm,
            keyId = authentication.keyId,
            clientIdScheme = authentication.clientId.toPresentationClientIdScheme(),
        )
    }

internal fun WalletPresentFunctionality2.OID4VPErrorCode.toPresentationErrorCode(): PresentationErrorCode = when (this) {
    WalletPresentFunctionality2.OID4VPErrorCode.ACCESS_DENIED -> PresentationErrorCode.accessDenied
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_REQUEST -> PresentationErrorCode.invalidRequest
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_CLIENT -> PresentationErrorCode.invalidClient
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_SCOPE -> PresentationErrorCode.invalidScope
    WalletPresentFunctionality2.OID4VPErrorCode.UNAUTHORIZED_CLIENT -> PresentationErrorCode.unauthorizedClient
    WalletPresentFunctionality2.OID4VPErrorCode.UNSUPPORTED_RESPONSE_TYPE -> PresentationErrorCode.unsupportedResponseType
    WalletPresentFunctionality2.OID4VPErrorCode.SERVER_ERROR -> PresentationErrorCode.serverError
    WalletPresentFunctionality2.OID4VPErrorCode.TEMPORARILY_UNAVAILABLE -> PresentationErrorCode.temporarilyUnavailable
    WalletPresentFunctionality2.OID4VPErrorCode.VP_FORMATS_NOT_SUPPORTED -> PresentationErrorCode.vpFormatsNotSupported
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_REQUEST_URI_METHOD -> PresentationErrorCode.invalidRequestUriMethod
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_TRANSACTION_DATA -> PresentationErrorCode.invalidTransactionData
    WalletPresentFunctionality2.OID4VPErrorCode.WALLET_UNAVAILABLE -> PresentationErrorCode.walletUnavailable
}

internal fun PreparedPresentationReview.toReviewedPresentationClaims() =
    ReviewedPresentationClaims(credentials.map {
        PresentationCredentialClaims(it.queryId, it.credentialId, it.format, it.claims.toString(), it.wholeCredential,
            it.locations.map { location -> PresentationClaimLocation(
                JsonArray(location.sourcePath).toString(),
                JsonArray(location.receivedPath).toString(), location.sharedBy, location.alwaysCarried, location.isBinary) })
    })

private fun JsonElement.displayValue(): String? =
    when (this) {
        is JsonPrimitive -> contentOrNull
        else -> toString()
    }

private fun ClientId.toPresentationClientIdScheme(): PresentationClientIdScheme = when (this) {
    is PreRegistered -> PresentationClientIdScheme.PRE_REGISTERED
    is RedirectUri -> PresentationClientIdScheme.REDIRECT_URI
    is X509SanDns -> PresentationClientIdScheme.X509_SAN_DNS
    is X509Hash -> PresentationClientIdScheme.X509_HASH
    is DecentralizedIdentifier -> PresentationClientIdScheme.DECENTRALIZED_IDENTIFIER
    is VerifierAttestation -> PresentationClientIdScheme.VERIFIER_ATTESTATION
    is OpenIdFederation -> PresentationClientIdScheme.OPENID_FEDERATION
    is Unsupported -> error("Unsupported client identifier cannot be authenticated: $prefix")
}
