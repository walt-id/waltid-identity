@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.cose.Cose
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toEncodedJwk
import id.walt.cose.verifyDetached
import id.walt.crypto2.hpke.Hpke
import id.walt.crypto2.keys.HpkeCiphertext
import id.walt.credentials.formats.MdocsCredential
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.keys.KeyUsage
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.dcapi.DCAPIEncryptionInfo
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.deviceretrieval.ReaderAuthenticationPayloads
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.handover.AnnexCDcapiHandoverInfo
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.handlers.PreviewSessionStore
import id.waltid.openid4vp.wallet.DcApiWallet
import id.waltid.openid4vp.wallet.presentation.MdocPresenter
import id.walt.x509.CertificateDer
import id.walt.x509.crypto2VerificationKey
import id.walt.x509.verifyOrderedCertificateChainSignatures
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborObjectAsArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

@Serializable
@CborObjectAsArray
private data class AnnexCEncryptedResponse(
    val type: String,
    val response: AnnexCEncryptedResponseData,
)

@Serializable
private data class AnnexCEncryptedResponseData(
    @ByteString val enc: ByteArray,
    @ByteString val cipherText: ByteArray,
)

/**
 * HPKE-seals an Annex C device response to the reader's recipient key.
 *
 * Annex C fixes the suite at DHKEM(P-256, HKDF-SHA256) / HKDF-SHA256 / AES-128-GCM, which is the
 * only suite [Hpke] implements, and `aad` is empty because the session transcript is already bound
 * through `info`.
 */
internal suspend fun encryptAnnexCHpke(
    recipientPublicKey: CoseKey,
    plaintext: ByteArray,
    info: ByteArray,
): HpkeCiphertext = Hpke.sealBase(
    recipientPublicKey = recipientPublicKey.toEncodedJwk(),
    plaintext = plaintext,
    info = info,
)

/**
 * The whole ISO 18013-7 Annex C presentation flow, in one place.
 *
 * The two entry points read as the sequence they perform. [preview] validates the request, matches
 * available wallet documents, evaluates reader trust and retains the consent state. [submit] verifies
 * that the post-consent request is immutably the one consented to, builds the selected documents, and
 * HPKE-encrypts the `DeviceResponse`. Each step is a private method rather than a class: the steps
 * are not independently useful, they share the wallet and the session transcript, and splitting them
 * across types would hide the ordering that makes the flow safe.
 */
internal class MobileWalletAnnexCEngine(
    private val wallet: Wallet,
    private val readerTrustEvaluator: MobileWalletReaderTrustEvaluator,
    private val registryRecords: suspend () -> List<MobileWalletCredentialRegistryRecord>,
) {
    /** A request that passed validation, with everything later steps need already decoded. */
    private data class ValidatedRequest(
        val parsedRequest: MobileWalletAnnexCParsedRequest,
        val origin: String,
        val deviceRequest: DeviceRequest?,
        val encryptionInfoBase64Url: String?,
        val selectedRegistryEntryIds: List<String>,
    )

    private data class RetainedRequest(
        val parsedRequest: MobileWalletAnnexCParsedRequest,
        val verifiedOrigin: String,
        val encryptionInfoBase64Url: String?,
        val allowedCredentialIds: Set<String>,
    )

    /** The post-consent request, re-derived from bytes that were proven identical to the preview's. */
    private data class ConfirmedRequest(
        val transcript: SessionTranscript,
        val encryptionInfo: DCAPIEncryptionInfo,
    )

    private val retainedRequests = PreviewSessionStore<RetainedRequest>(sessionName = "Annex C presentation")

    fun parseDeviceRequest(base64Url: String): MobileWalletAnnexCParsedRequest =
        decodeAndValidateDeviceRequest(base64Url).toParsedRequest()

    suspend fun preview(request: MobileWalletAnnexCRequest): MobileWalletAnnexCPreview {
        val validated = validate(request)
        val options = matchWalletDocuments(validated)
        val readerTrust = evaluateReaderTrust(validated)
        val requestId = retainedRequests.create(
            walletId = wallet.id,
            value = RetainedRequest(
                parsedRequest = validated.parsedRequest,
                verifiedOrigin = validated.origin,
                encryptionInfoBase64Url = validated.encryptionInfoBase64Url,
                allowedCredentialIds = options.mapTo(mutableSetOf()) { it.credentialId },
            ),
        )
        return MobileWalletAnnexCPreview(
            requestId = requestId,
            verifiedOrigin = validated.origin,
            parsedRequest = validated.parsedRequest,
            credentialOptions = options,
            readerTrust = readerTrust,
        )
    }

    suspend fun submit(submission: MobileWalletAnnexCSubmission): MobileWalletDigitalCredentialResponse =
        retainedRequests.useRetainingOnFailure(wallet.id, submission.requestId) { retained ->
            val confirmed = confirmRequestUnchangedAfterConsent(submission, retained)
            val documents = buildSelectedDocuments(submission, retained, confirmed.transcript)
            encryptDeviceResponse(documents, confirmed)
        }

    /**
     * Checks everything that can be decided from the request alone, before any wallet data is read.
     *
     * The raw `deviceRequest`/`encryptionInfo` pair is optional because Apple withholds it until
     * consent, but it is all-or-nothing: a caller must not be able to supply a raw request whose
     * encryption metadata is never validated, or vice versa.
     */
    private suspend fun validate(request: MobileWalletAnnexCRequest): ValidatedRequest {
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
    ): List<MobileWalletPresentationCredentialOption> {
        val credentialIdsByRegistryId = registryRecords().associate { it.registryEntryId to it.credentialId }
        val selectedCredentialIds = request.selectedRegistryEntryIds.map { registryId ->
            credentialIdsByRegistryId[registryId] ?: throw MobileWalletStaleRegistryEntryException(registryId)
        }.toSet()
        val storedCredentials = wallet.streamAllCredentials().toList()
        val options = request.parsedRequest.documents.flatMapIndexed { index, documentRequest ->
            storedCredentials.mapNotNull { stored ->
                stored.toAnnexCOption(index, documentRequest, selectedCredentialIds)
            }
        }
        request.parsedRequest.documents.forEachIndexed { index, documentRequest ->
            require(options.any { it.queryId == annexCQueryId(index, documentRequest.docType) }) {
                "No current mdoc credential satisfies requested document type ${documentRequest.docType}"
            }
        }
        return options
    }

    private suspend fun evaluateReaderTrust(request: ValidatedRequest): MobileWalletReaderTrust =
        if (request.deviceRequest == null) MobileWalletReaderTrust.PendingRawRequest
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
     * Reader authentication is verified again here for its *rejection*, not its verdict: the trust
     * state already informed the consent that was given, but a signature that does not verify must
     * stop the response. On Apple's deferred path this is the only point where the raw request exists
     * at all, so it is the only point where that signature can be checked.
     */
    private suspend fun confirmRequestUnchangedAfterConsent(
        submission: MobileWalletAnnexCSubmission,
        retained: RetainedRequest,
    ): ConfirmedRequest {
        val origin = DcApiWallet.canonicalizePlatformOrigin(submission.verifiedOrigin)
        require(origin == retained.verifiedOrigin) { "Annex C origin changed after consent" }
        if (retained.encryptionInfoBase64Url != null) {
            require(submission.encryptionInfoBase64Url == retained.encryptionInfoBase64Url) {
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
        submission: MobileWalletAnnexCSubmission,
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
        // Mobile keys are always platform-managed, so this path is crypto2-only and carries no
        // fallback for exportable JWK material.
        val holderKey = wallet.resolveCrypto2Key(usages = setOf(KeyUsage.SIGN))
            ?: throw IllegalStateException("No crypto2 wallet signing key is available")
        return retained.parsedRequest.documents.mapIndexed { index, requested ->
            val queryId = annexCQueryId(index, requested.docType)
            val selection = selectionsByQuery[queryId]
                ?: throw IllegalArgumentException("Missing credential selection for $queryId")
            // Identified by query rather than by credential: MobileWalletStaleRegistryEntryException
            // carries an opaque platform registry id, and this id is wallet-local.
            val stored = wallet.findCredential(selection.credentialId)
                ?: throw IllegalArgumentException("Selected credential for $queryId no longer exists")
            require((stored.credential as? MdocsCredential)?.docType == requested.docType) {
                "Selected credential no longer matches the Annex C document request"
            }
            MdocPresenter.buildAnnexCDocument(
                digitalCredential = stored.credential,
                requestedElements = requested.namespaces,
                sessionTranscript = transcript,
                holderKey = holderKey,
            )
        }
    }

    /** HPKE-seals the `DeviceResponse` to the reader's recipient key and wraps it for the platform. */
    private suspend fun encryptDeviceResponse(
        documents: List<Document>,
        confirmed: ConfirmedRequest,
    ): MobileWalletDigitalCredentialResponse {
        val plaintext = coseCompliantCbor.encodeToByteArray(
            DeviceResponse.serializer(),
            DeviceResponse(version = "1.0", documents = documents.toTypedArray(), status = 0u),
        )
        val ciphertext = encryptAnnexCHpke(
            recipientPublicKey = confirmed.encryptionInfo.encryptionParameters.recipientPublicKey,
            plaintext = plaintext,
            info = coseCompliantCbor.encodeToByteArray(SessionTranscript.serializer(), confirmed.transcript),
        )
        val responseBase64Url = coseCompliantCbor.encodeToByteArray(
            AnnexCEncryptedResponse.serializer(),
            AnnexCEncryptedResponse(
                type = "dcapi",
                response = AnnexCEncryptedResponseData(
                    enc = ciphertext.encapsulatedKey.toByteArray(),
                    cipherText = ciphertext.ciphertext.toByteArray(),
                ),
            ),
        ).encodeToBase64Url()
        return MobileWalletDigitalCredentialResponse(
            protocol = MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C,
            dataJson = buildJsonObject { put("response", JsonPrimitive(responseBase64Url)) }.toString(),
        )
    }

    private fun decodeAndValidateDeviceRequest(base64Url: String): DeviceRequest =
        DeviceRequest.decodeFromBase64Url(base64Url).also { request ->
            require(request.version in setOf(DeviceRequest.VERSION, DeviceRequest.VERSION_WITH_SIGNING)) {
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
     * [MobileWalletReaderTrust.NotAuthenticated] and the request stays processable - an anonymous
     * reader is a reader the user can still decline. A signature that is *present but does not
     * verify* is different in kind: it means the request was tampered with or replayed, so every
     * such case throws and the request never reaches consent or a response.
     */
    private suspend fun verifyReaderAuthentication(
        deviceRequest: DeviceRequest,
        transcript: SessionTranscript,
    ): MobileWalletReaderTrust {
        val authenticatedRequests = deviceRequest.docRequests.filter { it.readerAuth != null }
        val readerAuthAll = deviceRequest.readerAuthAll.orEmpty()
        if (authenticatedRequests.isEmpty() && readerAuthAll.isEmpty()) {
            return MobileWalletReaderTrust.NotAuthenticated
        }
        require(readerAuthAll.isNotEmpty() || authenticatedRequests.size == deviceRequest.docRequests.size) {
            "Mixed authenticated and unauthenticated Annex C document requests are rejected"
        }
        var certificateChain: List<ByteArray>? = null

        suspend fun verifyAuthentication(signature: CoseSign1, detachedPayload: ByteArray) {
            val protectedHeaders = if (signature.protected.isEmpty()) CoseHeaders()
                else coseCompliantCbor.decodeFromByteArray(CoseHeaders.serializer(), signature.protected)
            val chain = (protectedHeaders.x5chain ?: signature.unprotected.x5chain)
                ?.map { it.rawBytes }
                ?: throw IllegalArgumentException("Reader authentication has no x5chain")
            verifyOrderedCertificateChainSignatures(chain.map(::CertificateDer))
            if (certificateChain == null) certificateChain = chain
            else require(
                certificateChain.size == chain.size &&
                    certificateChain.zip(chain).all { (left, right) -> left.contentEquals(right) },
            ) {
                "Annex C reader authentication signatures use different certificate chains"
            }
            require(
                signature.verifyDetached(
                    key = CertificateDer(chain.first()).crypto2VerificationKey(),
                    detachedPayload = detachedPayload,
                    allowedAlgorithms = READER_AUTHENTICATION_COSE_ALGORITHMS,
                )
            ) {
                "Reader authentication signature is invalid"
            }
        }

        if (readerAuthAll.isNotEmpty()) {
            val detachedPayload = ReaderAuthenticationPayloads.forAllDocuments(
                sessionTranscript = transcript,
                itemsRequests = deviceRequest.docRequests.map { it.itemsRequest },
                deviceRequestInfo = deviceRequest.deviceRequestInfo,
            )
            readerAuthAll.forEachIndexed { index, signature ->
                try {
                    verifyAuthentication(signature, detachedPayload)
                } catch (exception: Exception) {
                    throw IllegalArgumentException(
                        "ReaderAuthAll signature at index $index is invalid",
                        exception,
                    )
                }
            }
        }
        authenticatedRequests.forEachIndexed { index, docRequest ->
            val readerAuth = requireNotNull(docRequest.readerAuth)
            try {
                verifyAuthentication(
                    readerAuth,
                    ReaderAuthenticationPayloads.forDocument(transcript, docRequest.itemsRequest),
                )
            } catch (exception: Exception) {
                throw IllegalArgumentException(
                    "ReaderAuth signature for document request at index $index is invalid",
                    exception,
                )
            }
        }
        return readerTrustEvaluator.evaluate(requireNotNull(certificateChain))
    }

    private fun DeviceRequest.toParsedRequest(): MobileWalletAnnexCParsedRequest =
        MobileWalletAnnexCParsedRequest(
            documents = docRequests.map { docRequest ->
                val items = docRequest.itemsRequest.value
                MobileWalletAnnexCDocumentRequest(
                    docType = items.docType,
                    namespaces = items.namespaces.entries
                        .sortedBy { it.key }
                        .associate { (namespace, requests) ->
                            namespace to requests.entries.map { it.key }.distinct().sorted()
                        },
                )
            },
        ).normalized()

    private fun MobileWalletAnnexCParsedRequest.normalized(): MobileWalletAnnexCParsedRequest =
        copy(
            documents = documents.map { document ->
                document.copy(
                    namespaces = document.namespaces.entries
                        .sortedBy { it.key }
                        .associate { (namespace, elements) ->
                            namespace to elements.distinct().sorted()
                        },
                )
            },
        )

    private fun StoredCredential.toAnnexCOption(
        index: Int,
        request: MobileWalletAnnexCDocumentRequest,
        selectedCredentialIds: Set<String>,
    ): MobileWalletPresentationCredentialOption? {
        val mdoc = credential as? MdocsCredential ?: return null
        if (mdoc.docType != request.docType) return null
        if (selectedCredentialIds.isNotEmpty() && id !in selectedCredentialIds) return null
        val disclosures = request.namespaces.flatMap { (namespace, elementNames) ->
            val namespaceData = mdoc.credentialData[namespace] as? JsonObject ?: return null
            elementNames.map { elementName ->
                val value = namespaceData[elementName] ?: return null
                MobileWalletPresentationDisclosure(
                    path = "$namespace.$elementName",
                    name = elementName,
                    valueJson = Json.encodeToString(JsonElement.serializer(), value),
                    displayValue = value.displayValue(),
                    selectivelyDisclosable = true,
                    required = true,
                    selectable = false,
                )
            }
        }
        val metadata = toMetadata()
        return MobileWalletPresentationCredentialOption(
            queryId = annexCQueryId(index, request.docType),
            credentialId = id,
            multiple = false,
            format = metadata.format,
            issuer = metadata.issuer,
            subject = metadata.subject,
            label = metadata.label,
            credentialDataJson = Json.encodeToString(JsonObject.serializer(), mdoc.credentialData),
            disclosures = disclosures,
        )
    }

    private fun JsonElement.displayValue(): String? =
        (this as? JsonPrimitive)?.contentOrNull ?: toString()

    private companion object {
        /**
         * COSE algorithms accepted for ISO 18013-5 reader authentication.
         *
         * The set is restricted to the ECDSA and EdDSA algorithms §9.1.3.4 permits for reader
         * authentication, so an unexpected algorithm in an untrusted request is rejected rather
         * than verified.
         */
        private val READER_AUTHENTICATION_COSE_ALGORITHMS = setOf(
            Cose.Algorithm.ES256,
            Cose.Algorithm.ES384,
            Cose.Algorithm.ES512,
            Cose.Algorithm.EdDSA,
        )

        private fun annexCQueryId(index: Int, docType: String): String = "$index:$docType"
    }
}
