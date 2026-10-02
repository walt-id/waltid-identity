package id.walt.walletdemo.compose.logic.walletapi2

import id.walt.walletdemo.compose.logic.ClaimGroup
import id.walt.walletdemo.compose.logic.CredentialDisplayNormalizer
import id.walt.walletdemo.compose.logic.CredentialDisplayVocabulary
import id.walt.walletdemo.compose.logic.WalletDemoCredential
import id.walt.walletdemo.compose.logic.WalletDemoCredentialClaimMetadata
import id.walt.walletdemo.compose.logic.WalletDemoDeferredCredential
import id.walt.walletdemo.compose.logic.WalletDemoContinuationStatus
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceFailureKind
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceOutcome
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceGrant
import id.walt.walletdemo.compose.logic.WalletDemoIssuerMetadata
import id.walt.walletdemo.compose.logic.WalletDemoMetadataDisplay
import id.walt.walletdemo.compose.logic.WalletDemoOfferPreview
import id.walt.walletdemo.compose.logic.WalletDemoOfferedCredentialMetadata
import id.walt.walletdemo.compose.logic.WalletDemoOperationResult
import id.walt.walletdemo.compose.logic.WalletDemoPresentationContinuation
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialOption
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialRequirement
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosure
import id.walt.walletdemo.compose.logic.WalletDemoPresentationError
import id.walt.walletdemo.compose.logic.WalletDemoPresentationPreview
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosureSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationPreviewHandle
import id.walt.walletdemo.compose.logic.WalletDemoPresentationPreviewResult
import id.walt.walletdemo.compose.logic.WalletDemoResponseEncryption
import id.walt.walletdemo.compose.logic.WalletDemoTransactionCodeInputMode
import id.walt.walletdemo.compose.logic.WalletDemoTransactionCodeRequirement
import id.walt.walletdemo.compose.logic.WalletDemoTransactionDataItem
import id.walt.walletdemo.compose.logic.WalletDemoVerifierMetadata
import id.walt.walletdemo.compose.logic.resolveCardTitle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal val walletApi2Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
    explicitNulls = false
}

internal fun ResolveOfferDetailedResponseDto.toDemoPreview(batchSize: Int? = null): WalletDemoOfferPreview =
    WalletDemoOfferPreview(
        issuer = WalletDemoIssuerMetadata(
            credentialIssuer = issuer.credentialIssuer,
            display = issuer.display?.toDemoDisplay(),
        ),
        offeredCredentials = offeredCredentials.map { offered ->
            WalletDemoOfferedCredentialMetadata(
                configurationId = offered.configurationId,
                format = offered.format,
                vct = offered.vct,
                doctype = offered.doctype,
                display = offered.display?.toDemoDisplay(),
                claims = offered.claims.map { claim ->
                    WalletDemoCredentialClaimMetadata(
                        path = claim.path,
                        mandatory = claim.mandatory,
                        displayName = claim.displayName,
                    )
                },
            )
        },
        transactionCode = transactionCode?.toDemoRequirement(),
        requiresIssuerAuthentication = toDemoGrant() == WalletDemoIssuanceGrant.AuthorizationCode,
        batchSize = batchSize,
    )

internal fun List<WalletDemoPresentationDisclosureSelection>.toDisclosureSelectionDtos(): List<DisclosureSelectionDto> =
    map { selection ->
        DisclosureSelectionDto(
            queryId = selection.queryId,
            credentialId = selection.credentialId,
            path = selection.path,
        )
    }

internal fun ResolveOfferDetailedResponseDto.toDemoGrant(): WalletDemoIssuanceGrant {
    val grant = grantType.orEmpty()
    return if (
        preAuthorizedCode != null ||
        grant.contains("pre-authorized", ignoreCase = true)
    ) {
        WalletDemoIssuanceGrant.PreAuthorizedCode
    } else {
        WalletDemoIssuanceGrant.AuthorizationCode
    }
}

internal fun OfferMetadataDisplayDto.toDemoDisplay(): WalletDemoMetadataDisplay =
    WalletDemoMetadataDisplay(
        name = name,
        logoUri = logoUri,
        logoAltText = logoAltText,
        description = description,
        backgroundColor = backgroundColor,
        backgroundImageUri = backgroundImageUri,
        textColor = textColor,
    )

internal fun OfferTransactionCodeRequirementDto.toDemoRequirement(): WalletDemoTransactionCodeRequirement =
    WalletDemoTransactionCodeRequirement(
        inputMode = if (inputMode.equals("text", ignoreCase = true)) {
            WalletDemoTransactionCodeInputMode.Text
        } else {
            WalletDemoTransactionCodeInputMode.Numeric
        },
        length = length,
        description = description,
    )

internal fun PresentationPreviewResponseDto.toDemoPreview(
    requestUrl: String,
): WalletDemoPresentationPreviewResult {
    val handle = WalletDemoPresentationPreviewHandle(requestUrl)
    val encryption = responseEncryption.toDemoEncryption()
    val verifier = verifier?.let { metadata ->
        WalletDemoVerifierMetadata(
            display = WalletDemoMetadataDisplay(
                name = metadata.name,
                logoUri = metadata.logoUri,
                logoAltText = null,
            ),
            clientUri = metadata.clientUri,
            policyUri = metadata.policyUri,
            termsOfServiceUri = metadata.termsOfServiceUri,
        )
    }
    val transactionData = transactionData.toDemoTransactionDataGroups()
    if (!valid) {
        return WalletDemoPresentationPreviewResult.Invalid(
            WalletDemoPresentationError(
                previewHandle = handle,
                verifierMetadata = verifier,
                clientId = clientId,
                responseUri = responseUri,
                state = state,
                nonce = nonce,
                responseEncryption = encryption,
                transactionData = transactionData,
                errorCode = error?.code ?: "invalid_request",
                message = error?.message ?: "Presentation request is invalid",
            ),
        )
    }
    return WalletDemoPresentationPreviewResult.Ready(
        WalletDemoPresentationPreview(
            previewHandle = handle,
            verifierMetadata = verifier,
            clientId = clientId,
            responseUri = responseUri,
            state = state,
            nonce = nonce,
            responseEncryption = encryption,
            transactionData = transactionData,
            credentialOptions = credentialOptions.map { option ->
                val credentialDataJson = option.credentialData.toString()
                WalletDemoPresentationCredentialOption(
                    queryId = option.queryId,
                    credentialId = option.credentialId,
                    multiple = option.multiple,
                    label = resolveCardTitle(
                        format = option.format,
                        credentialDataJson = credentialDataJson,
                        displayName = option.label,
                        fallback = option.format,
                    ),
                    issuer = option.issuer,
                    subject = option.subject,
                    format = option.format,
                    credentialDataJson = credentialDataJson,
                    disclosures = option.disclosures.map { disclosure ->
                        WalletDemoPresentationDisclosure(
                            label = CredentialDisplayVocabulary.disclosureLabel(disclosure.name, disclosure.path),
                            path = disclosure.path,
                            valueJson = disclosure.value.toString(),
                            selectivelyDisclosable = disclosure.selectivelyDisclosable,
                            required = disclosure.required,
                            selectable = disclosure.selectable,
                        )
                    },
                )
            },
            credentialRequirements = credentialRequirements.map { requirement ->
                WalletDemoPresentationCredentialRequirement(options = requirement.options)
            },
        ),
    )
}

internal fun List<PreviewTransactionDataItemDto>.toDemoTransactionDataGroups(): List<ClaimGroup> =
    CredentialDisplayNormalizer.transactionDataGroups(
        map { item ->
            WalletDemoTransactionDataItem(
                type = item.type,
                displayName = item.type,
                credentialQueryIds = item.credentialQueryIds,
                supportedFields = emptyList(),
                rawJson = item.rawJson.toString(),
                detailsJson = item.details.toString(),
            )
        },
    )

internal fun PreviewResponseEncryptionDto?.toDemoEncryption(): WalletDemoResponseEncryption =
    if (this == null || !required) {
        WalletDemoResponseEncryption.NotRequired
    } else {
        WalletDemoResponseEncryption.Required(
            keyManagementAlgorithm = keyManagementAlgorithm.orEmpty(),
            contentEncryptionAlgorithm = contentEncryptionAlgorithm.orEmpty(),
            verifierKeyId = verifierKeyId,
            verifierKeyThumbprint = verifierKeyThumbprint.orEmpty(),
        )
    }

internal fun WalletPresentResultDto.toDemoOperationResult(
    successMessage: String,
    failureMessage: String,
): WalletDemoOperationResult {
    val continuation = when {
        !formPostHtml.isNullOrBlank() -> WalletDemoPresentationContinuation.FormPostHtml(formPostHtml)
        !getUrl.isNullOrBlank() -> WalletDemoPresentationContinuation.Url(getUrl)
        !redirectTo.isNullOrBlank() -> WalletDemoPresentationContinuation.Url(redirectTo)
        else -> null
    }
    return if (transmissionSuccess == false && continuation == null) {
        WalletDemoOperationResult.Failure(failureMessage)
    } else {
        WalletDemoOperationResult.Success(successMessage, continuation)
    }
}

internal fun JsonObject.toDemoCredential(
    fallback: StoredCredentialMetadataDto,
): WalletDemoCredential {
    val credential = this["credential"]?.jsonObject
    val format = credential?.get("format")?.jsonPrimitive?.content ?: fallback.format
    val issuer = credential?.get("issuer")?.jsonPrimitive?.content ?: fallback.issuer
    val subject = credential?.get("subject")?.jsonPrimitive?.content ?: fallback.subject
    val credentialData = credential?.get("credentialData")
    val metadata = this["metadata"]
    val id = this["id"]?.jsonPrimitive?.content ?: fallback.id
    val label = this["label"]?.jsonPrimitive?.content ?: fallback.label ?: format
    val addedAt = jsonElementAsString(this["addedAt"]) ?: fallback.addedAt
    return WalletDemoCredential(
        id = id,
        format = format,
        issuer = issuer,
        subject = subject,
        label = label,
        addedAt = addedAt,
        credentialDataJson = credentialData?.toString(),
        metadataJson = metadata?.toString(),
    )
}

internal fun StoredCredentialMetadataDto.toDemoCredential(): WalletDemoCredential =
    WalletDemoCredential(
        id = id,
        format = format,
        issuer = issuer,
        subject = subject,
        label = label ?: format,
        addedAt = addedAt,
    )

internal fun publicJwkFromDidDocument(document: JsonObject?): String {
    val methods = document?.get("verificationMethod") as? JsonArray ?: return "{}"
    return methods.firstOrNull()?.jsonObject?.get("publicKeyJwk")?.toString() ?: "{}"
}

internal data class VerifiedHolder(
    val keyId: String,
    val did: String,
    val publicJwk: String,
)

/** Pair each key with a DID whose verification method publishes that key. */
internal fun verifiedHolderPairs(keys: List<WalletKeyInfo>, dids: List<WalletDidEntry>): List<VerifiedHolder> {
    val remaining = dids.toMutableList()
    return buildList {
        for (key in keys) {
            val publicJwk = key.publicJwk ?: continue
            val index = remaining.indexOfFirst { matchingVerificationKey(it.document, publicJwk) != null }
            if (index < 0) continue
            val did = remaining.removeAt(index)
            val matched = matchingVerificationKey(did.document, publicJwk) ?: continue
            add(VerifiedHolder(key.keyId, did.did, matched.toString()))
        }
    }
}

internal fun matchingVerificationKey(document: JsonObject?, publicJwk: JsonObject): JsonObject? {
    val fields = publicKeyFields(publicJwk)
    if (fields.isEmpty()) return null
    val methods = document?.get("verificationMethod") as? JsonArray ?: return null
    return methods.firstNotNullOfOrNull { entry ->
        val key = (entry as? JsonObject)?.get("publicKeyJwk") as? JsonObject ?: return@firstNotNullOfOrNull null
        if (fields.all { name -> key[name] != null && key[name] == publicJwk[name] }) key else null
    }
}

private fun publicKeyFields(publicJwk: JsonObject): List<String> = when (publicJwk["kty"]?.jsonPrimitive?.contentOrNull) {
    "EC" -> listOf("kty", "crv", "x", "y")
    "OKP" -> listOf("kty", "crv", "x")
    "RSA" -> listOf("kty", "n", "e")
    else -> emptyList()
}

private fun jsonElementAsString(value: JsonElement?): String? = when (value) {
    null -> null
    is JsonPrimitive -> value.content
    else -> value.toString()
}

internal fun DeferredCredentialHandleDto.toDemoDeferred() = WalletDemoDeferredCredential(
    id = id, credentialConfigurationId = credentialConfigurationId,
    intervalSeconds = intervalSeconds, credentialIdentifier = credentialIdentifier,
    status = when (status) {
        "AWAITING_ISSUER" -> WalletDemoContinuationStatus.AwaitingIssuer
        "AWAITING_LOCAL_SAVE" -> WalletDemoContinuationStatus.AwaitingLocalSave
        "REMOTE_OUTCOME_UNCERTAIN" -> WalletDemoContinuationStatus.RemoteOutcomeUncertain
        "STORAGE_OUTCOME_UNCERTAIN" -> WalletDemoContinuationStatus.StorageOutcomeUncertain
        else -> WalletDemoContinuationStatus.Unresolved
    },
    displayMetadataJson = displayMetadataJson,
)

internal fun ReceiveCredentialResultDto.toOutcome(): WalletDemoIssuanceOutcome {
    val pending = deferredCredentials.map {
        WalletDemoDeferredCredential(it.deferredCredentialId, it.credentialConfigurationId,
            it.intervalSeconds, it.credentialIdentifier, status = WalletDemoContinuationStatus.AwaitingIssuer)
    } + storageOutcome?.deferredCredentials.orEmpty().map { it.toDemoDeferred() }
    return when {
        failure != null || storageOutcome != null -> WalletDemoIssuanceOutcome.Failed(
            message = storageOutcome?.error?.message ?: "Credential issuance stopped during ${requireNotNull(failure).stage.lowercase()}",
            storedCredentialIds = credentialIds, deferredCredentials = pending, offerConsumed = true,
            failedTargetCount = if (failure == null) 0 else 1, notAttemptedTargetCount = failure?.notAttempted?.size ?: 0,
            kind = storageOutcome?.error?.code.toFailureKind(),
        )
        pending.isNotEmpty() -> WalletDemoIssuanceOutcome.Deferred(credentialIds, pending)
        else -> WalletDemoIssuanceOutcome.Stored(credentialIds)
    }
}

internal fun DeferredIssuanceOutcomeDto.toOutcome(): WalletDemoIssuanceOutcome = when (this) {
    is DeferredIssuanceOutcomeDto.Stored -> WalletDemoIssuanceOutcome.Stored(credentialIds)
    is DeferredIssuanceOutcomeDto.Deferred -> WalletDemoIssuanceOutcome.Deferred(
        storedCredentialIds, credentials.map { it.toDemoDeferred() })
    is DeferredIssuanceOutcomeDto.Failed -> WalletDemoIssuanceOutcome.Failed(
        message = error.message, storedCredentialIds = storedCredentialIds,
        failedTargetCount = if (failure == null) 0 else 1,
        notAttemptedTargetCount = failure?.notAttempted?.size ?: 0,
        deferredCredentials = deferredCredentials.map { it.toDemoDeferred() }, offerConsumed = true,
        kind = error.code.toFailureKind(),
    )
    DeferredIssuanceOutcomeDto.Cancelled -> WalletDemoIssuanceOutcome.Cancelled
}

private fun String?.toFailureKind() = when (this) {
    "REMOTE_OUTCOME_UNCERTAIN" -> WalletDemoIssuanceFailureKind.RemoteOutcomeUncertain
    "STORAGE_OUTCOME_UNCERTAIN" -> WalletDemoIssuanceFailureKind.StorageOutcomeUncertain
    else -> WalletDemoIssuanceFailureKind.General
}
