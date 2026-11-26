@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile.digitalcredentials

import kotlinx.serialization.ExperimentalSerializationApi
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.Cose
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toEncodedJwk
import id.walt.cose.verifyDetached
import id.walt.credentials.formats.MdocsCredential
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto2.hpke.Hpke
import id.walt.crypto2.keys.KeyUsage
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.dcapi.DCAPIEncryptionInfo
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.deviceretrieval.ReaderAuthenticationPayloads
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.handover.AnnexCDcapiHandoverInfo
import id.walt.mdoc.objects.sha256
import id.walt.mdoc.readertrust.ReaderAuthenticationEvidence
import id.walt.mdoc.readertrust.ReaderAuthenticationScope
import id.walt.mdoc.readertrust.ReaderTrustDecision
import id.walt.mdoc.readertrust.ReaderTrustEvaluator
import id.walt.mdoc.readertrust.ReaderTrustState
import id.walt.wallet2.data.HolderKeyBindingException
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.resolveHolderKey
import id.walt.wallet2.handlers.PreviewSessionStore
import id.walt.wallet2.mobile.presentation.PresentationCredentialOption
import id.waltid.openid4vp.wallet.DcApiWallet
import id.waltid.openid4vp.wallet.presentation.MdocPresenter
import id.waltid.openid4vp.wallet.presentation.PresentationReviewGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.toList
import kotlinx.io.bytestring.ByteString as IoByteString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The whole ISO 18013-7 Annex C presentation flow.
 *
 * [preview] validates the request, matches available wallet documents, evaluates reader trust and
 * retains the consent state. [submit] verifies that the post-consent request is the one consented to,
 * builds the selected documents, and HPKE-encrypts the `DeviceResponse`.
 */
internal class MobileWalletAnnexCEngine(
    private val wallet: Wallet,
    private val readerTrustEvaluator: ReaderTrustEvaluator,
    private val registryRecords: suspend () -> List<RegistryEntry>,
) {
    fun parseDeviceRequest(base64Url: String): AnnexCParsedRequest =
        decodeAndValidateDeviceRequest(base64Url).toParsedRequest()

    fun annexCRequest(request: DigitalCredentialRequest): AnnexCRequest {
        require(request.protocol == DigitalCredentialProtocols.ISO_MDOC_ANNEX_C) {
            "Not an ISO 18013-7 Annex C request: ${request.protocol}"
        }
        val data = Json.parseToJsonElement(request.dataJson).jsonObject
        val deviceRequest = requireNotNull(data["deviceRequest"]?.jsonPrimitive?.contentOrNull) {
            "Annex C request data has no deviceRequest"
        }
        val encryptionInfo = requireNotNull(data["encryptionInfo"]?.jsonPrimitive?.contentOrNull) {
            "Annex C request data has no encryptionInfo"
        }
        return AnnexCRequest(
            parsedRequest = parseDeviceRequest(deviceRequest),
            verifiedOrigin = request.verifiedOrigin,
            selectedRegistryEntryIds = request.selectedRegistryEntryIds,
            deviceRequestBase64Url = deviceRequest,
            encryptionInfoBase64Url = encryptionInfo,
        )
    }

    suspend fun preview(request: AnnexCRequest): AnnexCPreview {
        val validated = validate(request)
        val options = matchWalletDocuments(validated)
        val reader = authenticateReader(validated)
        val requestId = retainedRequests.create(
            walletId = wallet.id,
            value = RetainedRequest(
                parsedRequest = validated.parsedRequest,
                verifiedOrigin = validated.origin,
                rawRequest = validated.rawRequest,
                allowedCredentialIds = options.mapTo(mutableSetOf()) { it.credential.id },
            ),
        )
        return AnnexCPreview(
            requestId = requestId,
            verifiedOrigin = validated.origin,
            parsedRequest = validated.parsedRequest,
            credentialOptions = options,
            readerAuthentication = reader.authentication,
            readerTrust = reader.trust,
        )
    }

    suspend fun submit(submission: AnnexCSubmission): DigitalCredentialResponse =
        retainedRequests.useRetainingOnFailure(wallet.id, submission.requestId) { retained ->
            val confirmed = confirmRequestUnchangedAfterConsent(submission, retained)
            currentCoroutineContext()[PresentationReviewGuard]?.check()
            val documents = buildSelectedDocuments(submission, retained, confirmed.transcript)
            currentCoroutineContext()[PresentationReviewGuard]?.check()
            encryptAnnexCDeviceResponse(documents, confirmed.encryptionInfo, confirmed.transcript)
        }

    /** A request that passed validation, with everything later steps need already decoded. */
    private data class ValidatedRequest(
        val parsedRequest: AnnexCParsedRequest,
        val origin: String,
        val deviceRequest: DeviceRequest?,
        val encryptionInfoBase64Url: String?,
        val rawRequest: RawRequest?,
        val selectedRegistryEntryIds: List<String>,
    )

    /**
     * The raw request consent was given for, in the form each part has to be compared in.
     *
     * [deviceRequestDigest] digests the *decoded* bytes, so a re-spelled Base64URL encoding of the
     * same request is still accepted. [encryptionInfoBase64Url] is kept verbatim, because the session
     * transcript binds that string as written.
     */
    private class RawRequest(
        val deviceRequestDigest: ByteArray,
        val encryptionInfoBase64Url: String,
    )

    private class RetainedRequest(
        val parsedRequest: AnnexCParsedRequest,
        val verifiedOrigin: String,
        val rawRequest: RawRequest?,
        val allowedCredentialIds: Set<String>,
    )

    /** The post-consent request, re-derived from bytes that were proven identical to the preview's. */
    private data class ConfirmedRequest(
        val transcript: SessionTranscript,
        val encryptionInfo: DCAPIEncryptionInfo,
    )

    private val retainedRequests =
        PreviewSessionStore<RetainedRequest>(sessionName = "Annex C presentation")

    /**
     * Checks everything that can be decided from the request alone, before any wallet data is read.
     *
     * The raw `deviceRequest`/`encryptionInfo` pair is optional because Apple withholds it until
     * consent, but it is all-or-nothing: a caller must not be able to supply a raw request whose
     * encryption metadata is never validated, or vice versa.
     */
    private suspend fun validate(request: AnnexCRequest): ValidatedRequest {
        val origin = DcApiWallet.canonicalizePlatformOrigin(request.verifiedOrigin)
        require((request.deviceRequestBase64Url == null) == (request.encryptionInfoBase64Url == null)) {
            "Raw Annex C deviceRequest and encryptionInfo must be supplied together"
        }
        val parsedRequest = request.parsedRequest.normalized()
        val deviceRequest = request.deviceRequestBase64Url?.let(::decodeAndValidateDeviceRequest)
        require(deviceRequest == null || deviceRequest.toParsedRequest() == parsedRequest) {
            "Parsed and raw Annex C requests are inconsistent"
        }
        request.encryptionInfoBase64Url?.let { decodeAndValidateEncryptionInfo(it) }
        return ValidatedRequest(
            parsedRequest = parsedRequest,
            origin = origin,
            deviceRequest = deviceRequest,
            encryptionInfoBase64Url = request.encryptionInfoBase64Url,
            rawRequest = request.deviceRequestBase64Url?.let { deviceRequestBase64Url ->
                RawRequest(
                    deviceRequestDigest = deviceRequestBase64Url.decodeFromBase64Url().sha256(),
                    encryptionInfoBase64Url = requireNotNull(request.encryptionInfoBase64Url),
                )
            },
            selectedRegistryEntryIds = request.selectedRegistryEntryIds,
        )
    }

    /**
     * Finds the wallet documents that can satisfy each requested document, and rejects the request if
     * any goes unsatisfied - offering partial consent for a request the wallet cannot answer would
     * disclose data for nothing.
     */
    private suspend fun matchWalletDocuments(
        request: ValidatedRequest,
    ): List<PresentationCredentialOption> {
        val credentialIdsByRegistryId =
            registryRecords().associate { it.registryEntryId to it.credentialId }
        val selectedCredentialIds = request.selectedRegistryEntryIds.map { registryId ->
            credentialIdsByRegistryId[registryId] ?: throw StaleRegistryEntryException(
                registryId
            )
        }.toSet()
        val storedCredentials = wallet.streamAllCredentials().toList()
        val options = request.parsedRequest.documents.flatMapIndexed { index, documentRequest ->
            val bindingFailures = mutableListOf<HolderKeyBindingException>()
            val documentOptions = storedCredentials.mapNotNull { stored ->
                val option = stored.toAnnexCOption(index, documentRequest, selectedCredentialIds)
                    ?: return@mapNotNull null
                try {
                    wallet.resolveHolderKey(stored, setOf(KeyUsage.SIGN))
                    option
                } catch (failure: HolderKeyBindingException) {
                    bindingFailures += failure
                    null
                }
            }
            if (documentOptions.isEmpty() && bindingFailures.isNotEmpty()) throw bindingFailures.first()
            documentOptions
        }
        request.parsedRequest.documents.forEachIndexed { index, documentRequest ->
            require(options.any { it.queryId == annexCQueryId(index, documentRequest.docType) }) {
                "No current mdoc credential satisfies requested document type ${documentRequest.docType}"
            }
        }
        return options
    }

    /** Reader authentication state, with the trust decision present only for a verified reader. */
    private data class ReaderAuthenticationOutcome(
        val authentication: DigitalCredentialReaderAuthentication,
        val trust: ReaderTrustDecision? = null,
    )

    private suspend fun authenticateReader(request: ValidatedRequest): ReaderAuthenticationOutcome =
        if (request.deviceRequest == null) ReaderAuthenticationOutcome(DigitalCredentialReaderAuthentication.PENDING_RAW_REQUEST)
        else verifyReaderAuthentication(
            deviceRequest = request.deviceRequest,
            transcript = AnnexCDcapiHandoverInfo.sessionTranscript(
                base64EncryptionInfo = requireNotNull(request.encryptionInfoBase64Url),
                origin = request.origin,
            ),
        )

    /**
     * Proves the post-consent request is the one the user consented to, then re-derives the session
     * transcript and encryption metadata from its bytes.
     *
     * When the raw request was already available at preview, it must be exactly the one consent was
     * given for. The parsed request alone is too weak a binding: it carries no reader authentication,
     * no `deviceRequestInfo` and no version, so a request that dropped or replaced its reader auth
     * after consent would compare equal and remain answerable.
     *
     * On Apple's deferred path the raw request first exists here, so byte equality cannot be required
     * and these consistency checks are the available binding.
     */
    private suspend fun confirmRequestUnchangedAfterConsent(
        submission: AnnexCSubmission,
        retained: RetainedRequest,
    ): ConfirmedRequest {
        val origin = DcApiWallet.canonicalizePlatformOrigin(submission.verifiedOrigin)
        require(origin == retained.verifiedOrigin) { "Annex C origin changed after consent" }
        retained.rawRequest?.let { consented ->
            require(
                submission.deviceRequestBase64Url.decodeFromBase64Url().sha256()
                    .contentEquals(consented.deviceRequestDigest)
            ) {
                "Annex C deviceRequest changed after consent"
            }
            require(submission.encryptionInfoBase64Url == consented.encryptionInfoBase64Url) {
                "Annex C encryptionInfo changed after consent"
            }
        }
        val deviceRequest = decodeAndValidateDeviceRequest(submission.deviceRequestBase64Url)
        require(deviceRequest.toParsedRequest() == retained.parsedRequest) {
            "Parsed and raw Annex C requests are inconsistent"
        }
        val encryptionInfo = decodeAndValidateEncryptionInfo(submission.encryptionInfoBase64Url)
        val transcript = AnnexCDcapiHandoverInfo.sessionTranscript(
            base64EncryptionInfo = submission.encryptionInfoBase64Url,
            origin = origin,
        )
        verifyReaderAuthentication(deviceRequest, transcript)
        return ConfirmedRequest(transcript = transcript, encryptionInfo = encryptionInfo)
    }

    /** Builds one signed mdoc per requested document from the credentials the user selected. */
    private suspend fun buildSelectedDocuments(
        submission: AnnexCSubmission,
        retained: RetainedRequest,
        transcript: SessionTranscript,
    ): List<Document> {
        val selectionsByQuery = submission.selectedCredentialOptions.associateBy { it.queryId }
        require(selectionsByQuery.size == retained.parsedRequest.documents.size) {
            "Exactly one credential must be selected for every Annex C document request"
        }
        require(selectionsByQuery.values.all { it.credentialId in retained.allowedCredentialIds }) {
            "An Annex C credential selection was not offered by the consent preview"
        }
        return retained.parsedRequest.documents.mapIndexed { index, requested ->
            val queryId = annexCQueryId(index, requested.docType)
            val selection = selectionsByQuery[queryId]
                ?: throw IllegalArgumentException("Missing credential selection for $queryId")
            // Identified by query rather than by credential: StaleRegistryEntryException
            // carries an opaque platform registry id, and this id is wallet-local.
            val stored = wallet.findCredential(selection.credentialId)
                ?: throw IllegalArgumentException("Selected credential for $queryId no longer exists")
            require((stored.credential as? MdocsCredential)?.docType == requested.docType) {
                "Selected credential no longer matches the Annex C document request"
            }
            // Resolve the exact credential-bound key. A wallet default is never an mdoc fallback.
            val holderKey = wallet.resolveHolderKey(
                stored,
                setOf(KeyUsage.SIGN)
            ).keyMaterial.requireCrypto2Key()
            MdocPresenter.buildAnnexCDocument(
                digitalCredential = stored.credential,
                requestedElements = requested.namespaces,
                sessionTranscript = transcript,
                holderKey = holderKey,
            )
        }
    }

    private fun decodeAndValidateDeviceRequest(base64Url: String): DeviceRequest =
        DeviceRequest.decodeFromBase64Url(base64Url).also { request ->
            require(
                request.version in setOf(
                    DeviceRequest.VERSION,
                    DeviceRequest.VERSION_WITH_SIGNING
                )
            ) {
                "Unsupported Annex C DeviceRequest version"
            }
            require(request.docRequests.isNotEmpty()) { "Annex C DeviceRequest has no document requests" }
        }

    /**
     * Decodes `encryptionInfo` and rejects a recipient key the response could never be sealed to.
     *
     * The key-shape rules are crypto2's, so the wallet cannot drift from what [Hpke.sealBase] will
     * accept; the check runs here rather than at submission because a reader that sent unusable
     * encryption metadata must not reach the consent dialog.
     */
    private suspend fun decodeAndValidateEncryptionInfo(base64Url: String): DCAPIEncryptionInfo =
        coseCompliantCbor.decodeFromByteArray(
            DCAPIEncryptionInfo.serializer(),
            base64Url.decodeFromBase64Url(),
        ).also { info ->
            Hpke.validateRecipientPublicKey(info.encryptionParameters.recipientPublicKey.toEncodedJwk())
        }

    /**
     * Verifies reader authentication if the request carries any, then asks the application's trust
     * policy whether the verified reader is one it recognises.
     *
     * Reader authentication is optional in Annex C, so its absence is reported as
     * [DigitalCredentialReaderAuthentication.NOT_AUTHENTICATED] and the request stays processable - an anonymous
     * reader is a reader the user can still decline. A signature that is *present but does not
     * verify* is different in kind: it means the request was tampered with or replayed, so every
     * such case throws and the request never reaches consent or a response.
     *
     * Every verified statement is evaluated separately by the shared [ReaderTrustEvaluator], so one
     * holder-configured policy decides proximity and Annex C readers alike. The reader counts as
     * trusted only if every statement is; a revoked reader certificate rejects the request.
     */
    private suspend fun verifyReaderAuthentication(
        deviceRequest: DeviceRequest,
        transcript: SessionTranscript,
    ): ReaderAuthenticationOutcome {
        val authenticatedRequests = deviceRequest.docRequests.filter { it.readerAuth != null }
        val readerAuthAll = deviceRequest.readerAuthAll.orEmpty()
        if (authenticatedRequests.isEmpty() && readerAuthAll.isEmpty()) {
            return ReaderAuthenticationOutcome(DigitalCredentialReaderAuthentication.NOT_AUTHENTICATED)
        }
        require(readerAuthAll.isNotEmpty() || authenticatedRequests.size == deviceRequest.docRequests.size) {
            "Mixed authenticated and unauthenticated Annex C document requests are rejected"
        }
        var certificateChain: List<ByteArray>? = null
        val evidence = mutableListOf<ReaderAuthenticationEvidence>()

        suspend fun verifyAuthentication(
            signature: CoseSign1,
            detachedPayload: ByteArray,
            scope: ReaderAuthenticationScope,
            authenticationIndex: Int,
        ) {
            val protectedHeaders = if (signature.protected.isEmpty()) CoseHeaders()
            else coseCompliantCbor.decodeFromByteArray(
                CoseHeaders.serializer(),
                signature.protected
            )
            val chain = (protectedHeaders.x5chain ?: signature.unprotected.x5chain)
                ?.map { it.rawBytes }
                ?: throw IllegalArgumentException("Reader authentication has no x5chain")
            val certificates = chain.map { X509CertificateUtil.parseCertificateDerEncoded(IoByteString(it)) }
            if (certificateChain == null) certificateChain = chain
            else require(
                certificateChain.size == chain.size &&
                        certificateChain.zip(chain)
                            .all { (left, right) -> left.contentEquals(right) },
            ) {
                "Annex C reader authentication signatures use different certificate chains"
            }
            require(
                signature.verifyDetached(
                    key = certificates.first().restoreSubjectPublicKey(X509CertificateUtil.services.cryptoRuntime),
                    detachedPayload = detachedPayload,
                    allowedAlgorithms = READER_AUTHENTICATION_COSE_ALGORITHMS,
                )
            ) {
                "Reader authentication signature is invalid"
            }
            evidence += ReaderAuthenticationEvidence(
                scope = scope,
                authenticationIndex = authenticationIndex,
                certificateChainDer = chain.map(::IoByteString),
            )
        }

        if (readerAuthAll.isNotEmpty()) {
            val detachedPayload = ReaderAuthenticationPayloads.forAllDocuments(
                sessionTranscript = transcript,
                itemsRequests = deviceRequest.docRequests.map { it.itemsRequest },
                deviceRequestInfo = deviceRequest.deviceRequestInfo,
            )
            readerAuthAll.forEachIndexed { index, signature ->
                try {
                    verifyAuthentication(signature, detachedPayload, ReaderAuthenticationScope.WholeRequest, index)
                } catch (exception: Exception) {
                    throw IllegalArgumentException(
                        "ReaderAuthAll signature at index $index is invalid",
                        exception,
                    )
                }
            }
        }
        deviceRequest.docRequests.forEachIndexed { index, docRequest ->
            val readerAuth = docRequest.readerAuth ?: return@forEachIndexed
            try {
                verifyAuthentication(
                    readerAuth,
                    ReaderAuthenticationPayloads.forDocument(transcript, docRequest.itemsRequest),
                    ReaderAuthenticationScope.Document(index),
                    0,
                )
            } catch (exception: Exception) {
                throw IllegalArgumentException(
                    "ReaderAuth signature for document request at index $index is invalid",
                    exception,
                )
            }
        }
        val decisions = evidence.map { evaluateReaderTrust(it) }
        require(decisions.none { it.state == ReaderTrustState.REVOKED }) {
            "Annex C reader certificate is revoked"
        }
        val untrusted = decisions.firstOrNull { it.state != ReaderTrustState.TRUSTED }
        val trust = if (untrusted != null) {
            // The signatures verified, so an evaluator answering NOT_EVALUATED still leaves a valid but untrusted reader.
            ReaderTrustDecision(
                state = ReaderTrustState.VALID_BUT_UNTRUSTED,
                reason = untrusted.reason ?: "Reader trust policy did not accept the reader",
                displayName = untrusted.displayName,
            )
        } else {
            ReaderTrustDecision(
                state = ReaderTrustState.TRUSTED,
                displayName = decisions.firstNotNullOfOrNull { it.displayName }
                    ?: X509CertificateUtil.parseCertificateDerEncoded(IoByteString(requireNotNull(certificateChain).first()))
                        .data.subjectDn,
            )
        }
        return ReaderAuthenticationOutcome(DigitalCredentialReaderAuthentication.VERIFIED, trust)
    }

    /** Per the [ReaderTrustEvaluator] contract, a failing policy leaves the reader untrusted rather than failing the request. */
    private suspend fun evaluateReaderTrust(evidence: ReaderAuthenticationEvidence): ReaderTrustDecision =
        try {
            readerTrustEvaluator.evaluate(evidence)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            ReaderTrustDecision(
                state = ReaderTrustState.VALID_BUT_UNTRUSTED,
                reason = "Reader trust evaluation failed: '${exception.message}'",
            )
        }

    private companion object {
        /**
         * COSE algorithms accepted for ISO 18013-5 reader authentication.
         *
         * The active profile restricts this path to its ECDSA and EdDSA reader-authentication
         * algorithms, so an unexpected algorithm in an untrusted request is rejected before
         * verification.
         */
        private val READER_AUTHENTICATION_COSE_ALGORITHMS = setOf(
            Cose.Algorithm.ES256,
            Cose.Algorithm.ES384,
            Cose.Algorithm.ES512,
            Cose.Algorithm.EdDSA,
        )


    }
}
