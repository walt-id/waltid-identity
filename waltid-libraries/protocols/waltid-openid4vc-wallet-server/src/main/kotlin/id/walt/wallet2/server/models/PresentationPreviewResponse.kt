@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.server.models

import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.wallet2.handlers.StatelessPreviewPresentationResult
import id.waltid.openid4vp.wallet.response.ResponseEncryption
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * UI-facing result of resolving and validating an OpenID4VP request for consent review.
 *
 * Stateless: no preview handle is retained server-side. The caller holds [authorizationRequest]
 * and completes the flow with `credentials/present/build-vp-token` + `send-response`, or rejects
 * via `credentials/present/reject` with the original `requestUrl`.
 *
 * When [valid] is false, the detected [error] can be returned to the verifier via reject.
 */
@Serializable
data class PresentationPreviewResponse(
    val authorizationRequest: AuthorizationRequest,
    val valid: Boolean,
    val clientId: String? = null,
    val verifier: PreviewVerifierMetadata? = null,
    val responseUri: String? = null,
    val state: String? = null,
    val nonce: String? = null,
    val responseEncryption: PreviewResponseEncryption? = null,
    val transactionData: List<PreviewTransactionDataItem> = emptyList(),
    val credentialOptions: List<PreviewCredentialOption> = emptyList(),
    val credentialRequirements: List<PreviewCredentialRequirement> = emptyList(),
    val error: PreviewError? = null,
)

/** Localized verifier display metadata; supplied by the verifier and not on its own evidence of trust. */
@Serializable
data class PreviewVerifierMetadata(
    val name: String? = null,
    val locale: String? = null,
    val logoUri: String? = null,
    val clientUri: String? = null,
    val policyUri: String? = null,
    val termsOfServiceUri: String? = null,
)

/** Response-encryption state selected for an OpenID4VP presentation request. */
@Serializable
data class PreviewResponseEncryption(
    val required: Boolean,
    val keyManagementAlgorithm: String? = null,
    val contentEncryptionAlgorithm: String? = null,
    val verifierKeyId: String? = null,
    val verifierKeyThumbprint: String? = null,
)

/** A decoded transaction_data item attached to a presentation request. */
@Serializable
data class PreviewTransactionDataItem(
    val type: String,
    val credentialQueryIds: List<String> = emptyList(),
    val rawJson: JsonObject,
    val details: JsonObject,
)

/** A wallet credential that satisfies one DCQL credential query in the presentation request. */
@Serializable
data class PreviewCredentialOption(
    val queryId: String,
    val credentialId: String,
    val multiple: Boolean = false,
    val format: String,
    val issuer: String? = null,
    val subject: String? = null,
    val label: String? = null,
    val credentialData: JsonObject,
    val disclosures: List<PreviewCredentialDisclosure> = emptyList(),
)

/** A claim value that may be shared for a matched credential, with selective-disclosure flags. */
@Serializable
data class PreviewCredentialDisclosure(
    val path: String,
    val name: String? = null,
    val value: JsonElement,
    val selectivelyDisclosable: Boolean,
    val required: Boolean,
    val selectable: Boolean,
)

/** A required DCQL credential-query combination; at least one option must be satisfied. */
@Serializable
data class PreviewCredentialRequirement(
    val options: List<List<String>>,
)

/** Standard OpenID4VP error detected by the wallet while validating a request. */
@Serializable
data class PreviewError(
    val code: String,
    val message: String? = null,
)

/**
 * Projects a [StatelessPreviewPresentationResult] into the serializable [PresentationPreviewResponse].
 *
 * @param preferredLocales BCP 47 language tags used to pick localized verifier display values.
 */
fun StatelessPreviewPresentationResult.toPreviewResponse(
    preferredLocales: List<String> = emptyList(),
): PresentationPreviewResponse = when (this) {
    is StatelessPreviewPresentationResult.Invalid -> PresentationPreviewResponse(
        authorizationRequest = authorizationRequest,
        valid = false,
        clientId = authorizationRequest.clientId,
        verifier = authorizationRequest.clientMetadata?.toPreviewVerifierMetadata(preferredLocales),
        responseUri = authorizationRequest.responseUri,
        state = authorizationRequest.state,
        nonce = authorizationRequest.nonce,
        error = PreviewError(code = error.code.code, message = error.message),
    )

    is StatelessPreviewPresentationResult.Ready -> PresentationPreviewResponse(
        authorizationRequest = authorizationRequest,
        valid = true,
        clientId = authorizationRequest.clientId,
        verifier = authorizationRequest.clientMetadata?.toPreviewVerifierMetadata(preferredLocales),
        responseUri = authorizationRequest.responseUri,
        state = authorizationRequest.state,
        nonce = authorizationRequest.nonce,
        responseEncryption = responseEncryption.toPreviewResponseEncryption(),
        transactionData = transactionData.map { item ->
            PreviewTransactionDataItem(
                type = item.type,
                credentialQueryIds = item.credentialQueryIds,
                rawJson = item.rawJson,
                details = item.details,
            )
        },
        credentialOptions = credentialOptions.map { option ->
            PreviewCredentialOption(
                queryId = option.queryId,
                credentialId = option.credentialId,
                multiple = option.multiple,
                format = option.format,
                issuer = option.issuer,
                subject = option.subject,
                label = option.label,
                credentialData = option.credentialData,
                disclosures = option.disclosures.map { disclosure ->
                    PreviewCredentialDisclosure(
                        path = disclosure.path,
                        name = disclosure.name,
                        value = disclosure.value,
                        selectivelyDisclosable = disclosure.selectivelyDisclosable,
                        required = disclosure.required,
                        selectable = disclosure.selectable,
                    )
                },
            )
        },
        credentialRequirements = credentialRequirements.map {
            PreviewCredentialRequirement(options = it.options)
        },
    )
}

private fun ClientMetadata.toPreviewVerifierMetadata(
    preferredLocales: List<String>,
): PreviewVerifierMetadata {
    val name = selectLocalizedValue(clientName, clientNameI18n, preferredLocales)
    val logo = selectLocalizedValue(logoUri, logoUriI18n, preferredLocales)
    return PreviewVerifierMetadata(
        name = name.value,
        locale = if (name.value != null) name.locale else logo.locale,
        logoUri = logo.value,
        clientUri = selectLocalizedValue(clientUri, clientUriI18n, preferredLocales).value,
        policyUri = selectLocalizedValue(policyUri, policyUriI18n, preferredLocales).value,
        termsOfServiceUri = selectLocalizedValue(tosUri, tosUriI18n, preferredLocales).value,
    )
}

private fun ResponseEncryption.Metadata?.toPreviewResponseEncryption(): PreviewResponseEncryption =
    this?.let {
        PreviewResponseEncryption(
            required = true,
            keyManagementAlgorithm = keyManagementAlgorithm,
            contentEncryptionAlgorithm = contentEncryptionAlgorithm,
            verifierKeyId = verifierKeyId,
            verifierKeyThumbprint = verifierKeyThumbprint,
        )
    } ?: PreviewResponseEncryption(required = false)

// ---------------------------------------------------------------------------
// Localized value selection (base value + language-tagged map)
// ---------------------------------------------------------------------------

private data class LocalizedValue(val locale: String?, val value: String?)

private fun selectLocalizedValue(
    base: String?,
    localized: Map<String, String>,
    preferredLocales: List<String>,
): LocalizedValue = buildList {
    base?.takeIf { it.isNotBlank() }?.let { add(LocalizedValue(locale = null, value = it)) }
    localized.forEach { (locale, value) ->
        value.takeIf { it.isNotBlank() }?.let { add(LocalizedValue(locale = locale, value = it)) }
    }
}.selectPreferredByLocale(preferredLocales) { it.locale } ?: LocalizedValue(locale = null, value = null)
