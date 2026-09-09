@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.cose.selectCoseSignatureAlgorithm
import id.walt.cose.toCoseSigner
import id.walt.credentials.formats.MdocsCredential
import id.walt.crypto2.keys.KeyUsage
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.deviceretrieval.ElementReference
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.elements.DeviceSignedItem
import id.walt.mdoc.objects.elements.DeviceSignedItemList
import id.walt.mdoc.proximity.DeviceRequestReaderAuthentication
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.MdocApplicationAuthorization
import id.walt.mdoc.proximity.MdocApplicationAuthorizationDetail
import id.walt.mdoc.proximity.MdocAuthenticationMethod
import id.walt.mdoc.proximity.MdocConsentPrompt
import id.walt.mdoc.proximity.MdocCredentialCandidate
import id.walt.mdoc.proximity.MdocDocumentPresentation
import id.walt.mdoc.proximity.MdocHolderRequestContext
import id.walt.mdoc.proximity.MdocHolderRequestProcessor
import id.walt.mdoc.proximity.MdocRequestMatchResult
import id.walt.mdoc.proximity.MdocRequestMatcher
import id.walt.mdoc.proximity.MdocRequestPreparation
import id.walt.mdoc.proximity.MdocRequestPreview
import id.walt.mdoc.proximity.MdocRequestSelection
import id.walt.mdoc.proximity.MdocResponseBuilder
import id.walt.mdoc.proximity.MdocResponseResolution
import id.walt.mdoc.proximity.MdocSessionContinuation
import id.walt.mdoc.proximity.PreviewDocument
import id.walt.mdoc.proximity.PreviewElement
import id.walt.mdoc.proximity.ProximityError as EngineProximityError
import id.walt.mdoc.proximity.ProximityException
import id.walt.mdoc.proximity.ReaderAuthenticationDisplayEntry
import id.walt.mdoc.proximity.ReaderAuthenticationDisplayValidity
import id.walt.mdoc.proximity.ReaderAuthenticationEvidence
import id.walt.mdoc.proximity.ReaderAuthenticationResult
import id.walt.mdoc.proximity.ReaderAuthenticationScope
import id.walt.mdoc.proximity.ReaderAuthenticationVerifier
import id.walt.mdoc.proximity.ReaderTrustDecision
import id.walt.mdoc.proximity.ReaderTrustEvaluator
import id.walt.mdoc.proximity.ReaderTrustState
import id.walt.mdoc.proximity.SelectedDocument
import id.walt.mdoc.proximity.supportsMdocDeviceMac
import id.walt.mdoc.proximity.toDisplaySafe
import id.walt.mdoc.verification.verifyIssuerAuthentication
import id.walt.mdoc.verification.verifyIssuerSignedItemDigests
import id.walt.wallet2.data.HolderKeyBindingErrorCode
import id.walt.wallet2.data.HolderKeyBindingException
import id.walt.wallet2.data.ResolvedHolderKey
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.resolveHolderKey
import id.walt.x509.authorityKeyIdentifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.cbor.CborBoolean
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonObject

internal class ProximityRequestProcessor(
    private val wallet: Wallet,
    configuration: ProximityConfiguration,
    readerAuthenticationAlgorithms: Set<Int>,
) : MdocHolderRequestProcessor {
    private data class InventoryDocument(
        val stored: StoredCredential,
        val credential: MdocsCredential,
        val document: Document,
        val holderKey: ResolvedHolderKey,
        val deviceAuthentication: ProximityDeviceAuthenticationMethod,
        val issuerAuthorityKeyIdentifiers: List<ImmutableBytes>,
    )

    private data class ApplicationProfileSnapshot(
        val public: ProximityApplicationAuthorization,
        val lower: MdocApplicationAuthorization,
        val decodedDeviceElements: List<Pair<ProximityDeviceSignedElement, CborElement>>,
    )

    private data class Snapshot(
        val exchange: Int,
        val review: ProximityReview,
        val selection: MdocRequestSelection,
        val inventory: Map<String, InventoryDocument>,
        val applicationProfiles: List<ApplicationProfileSnapshot>,
        val bindingDigest: ImmutableBytes,
        val lowerPreview: MdocRequestPreview,
    )

    private data class Approved(
        val exchange: Int,
        val reviewId: ProximityReviewId,
        val submission: ProximitySubmission,
        val applicationProfiles: List<ApplicationProfileSnapshot>,
        val choiceDigest: ImmutableBytes,
    )

    private data class ReaderTrustKey(
        val scope: ProximityReaderAuthenticationScope,
        val authenticationIndex: Int,
    )

    private data class ResolvedAuthentication(
        val holderKey: ResolvedHolderKey,
        val method: ProximityDeviceAuthenticationMethod,
    )

    private val configuration = configuration.snapshot()
    private var closed = false
    private val readerVerifier = ReaderAuthenticationVerifier(
        trustEvaluator = ReaderTrustEvaluator(::evaluateReaderTrust),
        allowedAlgorithms = readerAuthenticationAlgorithms,
    )
    private val matcher = MdocRequestMatcher()
    private val responseBuilder = MdocResponseBuilder()
    private val stateMutex = Mutex()
    private val evaluatedReaderTrust = mutableMapOf<ReaderTrustKey, ProximityReaderTrustDecision>()
    private var currentSnapshot: Snapshot? = null
    private var approved: Approved? = null

    override suspend fun prepare(context: MdocHolderRequestContext): MdocRequestPreparation = try {
        MdocRequestPreparation.Review(preview(context))
    } catch (failure: ProximityException) {
        when (failure.error.code) {
            "request_unsatisfied" -> MdocRequestPreparation.NoData
            "invalid_reader_authentication", "reader_revoked", "trusted_reader_required" ->
                MdocRequestPreparation.Rejected(failure.error)
            else -> throw failure
        }
    }

    override suspend fun preview(context: MdocHolderRequestContext): MdocRequestPreview {
        val snapshot = buildSnapshot(context)
        stateMutex.withLock {
            check(!closed) { "The proximity processor is closed" }
            check(currentSnapshot == null) { "A previous proximity review has not been consumed" }
            currentSnapshot = snapshot
        }
        return snapshot.lowerPreview
    }

    suspend fun review(prompt: MdocConsentPrompt): ProximityReview = stateMutex.withLock {
        val snapshot = requireNotNull(currentSnapshot) { "The proximity review snapshot is unavailable" }
        require(snapshot.exchange == prompt.exchange)
        require(snapshot.bindingDigest == prompt.preview.submissionBindingDigest)
        snapshot.review.snapshot()
    }

    suspend fun cancel() = stateMutex.withLock {
        closed = true
        approved = null
        currentSnapshot = null
    }

    /** Returns a stable error when invalid; otherwise retains the exact immutable submission. */
    suspend fun accept(
        prompt: MdocConsentPrompt,
        reviewId: ProximityReviewId,
        submission: ProximitySubmission,
    ): ProximityError? {
        val owned = try { submission.snapshot() } catch (_: IllegalArgumentException) {
            return staleError("The proximity submission is invalid")
        }
        return stateMutex.withLock {
            val snapshot = currentSnapshot
                ?: return@withLock staleError("The proximity review is no longer current")
            if (closed || snapshot.review.reviewId != reviewId || snapshot.exchange != prompt.exchange ||
                snapshot.bindingDigest != prompt.preview.submissionBindingDigest) {
                return@withLock staleError("The proximity action does not belong to the current review")
            }
            validateSubmission(snapshot.review, owned)?.let { return@withLock it }
            if (approved != null) return@withLock staleError("The proximity review was already submitted")
            approved = Approved(
                prompt.exchange, reviewId, owned, snapshot.applicationProfiles,
                choiceDigest(reviewId, owned, snapshot.applicationProfiles),
            )
            null
        }
    }

    suspend fun holderAuthorization(
        reviewId: ProximityReviewId,
    ): ProximityHolderAuthorization = stateMutex.withLock {
        val snapshot = requireNotNull(currentSnapshot) { "The proximity review snapshot is unavailable" }
        val retained = requireNotNull(approved) { "The submission has not been accepted" }
        require(!closed && retained.reviewId == reviewId && snapshot.review.reviewId == reviewId)
        ProximityHolderAuthorization(
            reviewId = reviewId,
            exchange = retained.exchange,
            requests = retained.submission.documents.map { selected ->
                val method = snapshot.review.documents
                    .single { it.requestIndex == selected.requestIndex }
                    .credentialOptions.single { it.credentialId == selected.credentialId }
                    .deviceAuthentication
                ProximityHolderAuthorizationRequest(
                    requestIndex = selected.requestIndex,
                    credentialId = selected.credentialId,
                    deviceAuthentication = method,
                )
            },
        )
    }

    override suspend fun resolve(
        context: MdocHolderRequestContext,
        preview: MdocRequestPreview,
    ): MdocResponseResolution {
        val retained = stateMutex.withLock {
            val value = requireNotNull(approved) { "No holder-approved proximity submission is available" }
            require(!closed && value.exchange == context.exchange)
            require(value.choiceDigest == choiceDigest(value.reviewId, value.submission, value.applicationProfiles))
            approved = null
            currentSnapshot = null
            value
        }
        val fresh = buildSnapshot(context)
        if (fresh.bindingDigest != preview.submissionBindingDigest) {
            throw ProximityException(
                EngineProximityError.Security(
                    "changed_submission",
                    "Credential, trust, status, or application-profile state changed after consent",
                )
            )
        }
        validateSubmission(fresh.review, retained.submission)?.let { error ->
            throw ProximityException(EngineProximityError.Security(error.code, error.message))
        }
        stateMutex.withLock { check(!closed) { "The proximity processor is closed" } }
        val response = buildResponse(context, fresh, retained.submission, retained.applicationProfiles)
        stateMutex.withLock { check(!closed) { "The proximity processor is closed" } }
        return MdocResponseResolution.Send(
            exactResponse = ImmutableBytes.of(
                coseCompliantCbor.encodeToByteArray(DeviceResponse.serializer(), response)
            ),
            continuation = if (retained.submission.continueAfterResponse) {
                MdocSessionContinuation.CONTINUE
            } else {
                MdocSessionContinuation.TERMINATE
            },
            submissionBindingDigest = fresh.bindingDigest,
        )
    }

    private suspend fun buildSnapshot(context: MdocHolderRequestContext): Snapshot {
        val request = context.request.value
        val requestedDocTypes = request.docRequests.map { it.itemsRequest.value.docType }.toSet()
        val credentials = try {
            wallet.streamAllCredentials().map { it.snapshotForProximity() }.toList()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: HolderKeyBindingException) {
            throw ProximityException(
                EngineProximityError.Policy(
                    "holder_key_unavailable",
                    "The credential-bound holder key is unavailable",
                ),
                failure,
            )
        }
        val relevant = credentials.mapNotNull { stored ->
            (stored.credential as? MdocsCredential)
                ?.takeIf { it.docType in requestedDocTypes }
                ?.let { stored to it }
        }
        val failures = mutableListOf<Throwable>()
        val inventory = relevant.mapNotNull { (stored, credential) ->
            try {
                validateInventoryDocument(stored, credential, context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                failures += failure
                null
            }
        }
        val candidates = inventory.map { it.toCandidate() }
        val selection = when (val result = matcher.match(request, candidates)) {
            is MdocRequestMatchResult.Matched -> result.selection
            is MdocRequestMatchResult.Unsatisfied -> {
                failures.firstOrNull()?.let { failure ->
                    if (failure is ProximityException) throw failure
                    throw ProximityException(
                        EngineProximityError.Policy(
                            "credential_unavailable",
                            "A required credential cannot be used for this proximity presentation",
                        ),
                        failure,
                    )
                }
                throw ProximityException(EngineProximityError.Policy("request_unsatisfied", result.reason))
            }
        }
        evaluatedReaderTrust.clear()
        val readerAuthentication = readerVerifier.verify(request, context.transcript.value)
        val selectedRequestIndices = selection.documents.map(SelectedDocument::requestIndex).toSet()
        enforceReaderPolicy(readerAuthentication, selectedRequestIndices)
        var eligible = selection.eligibleDocuments.filter { it.requestIndex in selectedRequestIndices }
        val readerDisplay = readerAuthentication.toPublicEntries()
        val eligibleCredentialIds = eligible.map(SelectedDocument::credentialId).toSet()
        val profileSnapshots = evaluateApplicationProfiles(
            context,
            inventory.filter { it.stored.id in eligibleCredentialIds },
            readerDisplay,
        )
        profileSnapshots.forEach { profile ->
            selectedRequestIndices.forEach { requestIndex ->
                eligible = eligible.filterNot { it.requestIndex == requestIndex } +
                    eligible.filter {
                        it.requestIndex == requestIndex &&
                            it.credentialId in profile.public.compatibleCredentialIds
                    }
            }
        }
        selectedRequestIndices.forEach { index ->
            if (eligible.none { it.requestIndex == index }) {
                throw ProximityException(
                    EngineProximityError.Policy(
                        "application_profile_unsatisfied",
                        "The application profile excludes every credential for a requested document",
                    )
                )
            }
        }
        val filteredSelection = MdocRequestSelection(
            documents = selectedRequestIndices.sorted().map { requestIndex ->
                selection.documents.firstOrNull { selected ->
                    selected.requestIndex == requestIndex && selected in eligible
                } ?: eligible.filter { it.requestIndex == requestIndex }
                    .minBy(SelectedDocument::credentialId)
            },
            useCases = selection.useCases,
            eligibleDocuments = eligible,
        )
        val inventoryById = inventory.associateBy { it.stored.id }
        val documentReviews = selectedRequestIndices.sorted().map { requestIndex ->
            val docType = request.docRequests[requestIndex].itemsRequest.value.docType
            ProximityDocumentReview(
                requestIndex = requestIndex,
                docType = docType,
                credentialOptions = eligible.filter { it.requestIndex == requestIndex }
                    .sortedBy(SelectedDocument::credentialId)
                    .map { selected -> selected.toPublicOption(inventoryById.getValue(selected.credentialId)) },
            )
        }
        val useCases = selection.useCases.map { selected ->
            ProximityUseCase(
                index = selected.index,
                mandatory = selected.mandatory,
                documentRequestIndices = selected.documentSet,
                purposeHints = selected.purposeHints.map { (type, code) ->
                    ProximityPurposeHint(type, code)
                },
            )
        }
        val review = ProximityReview(
            reviewId = ProximityReviewId(Uuid.random().toString()),
            exchange = context.exchange,
            documents = documentReviews,
            readerAuthentication = readerDisplay,
            useCases = useCases,
            applicationAuthorizations = profileSnapshots.map(ApplicationProfileSnapshot::public),
        )
        val bindingDigest = snapshotDigest(context, inventory, eligible, review, profileSnapshots)
        val lowerPreview = MdocRequestPreview(
            documents = documentReviews.map { document ->
                PreviewDocument(
                    docType = document.docType,
                    credentialIds = document.credentialOptions.map { it.credentialId },
                    elements = document.credentialOptions.flatMap { it.requestedElements }
                        .distinctBy { it.namespace to it.elementIdentifier }
                        .map { PreviewElement(it.namespace, it.elementIdentifier, it.intentToRetain) },
                )
            },
            purposeHints = useCases.flatMap { it.purposeHints }.associate { it.type to it.code },
            readerAuthentication = readerAuthentication.toDisplaySafe(),
            submissionBindingDigest = bindingDigest,
            applicationAuthorizations = profileSnapshots.map(ApplicationProfileSnapshot::lower),
        )
        return Snapshot(
            exchange = context.exchange,
            review = review,
            selection = filteredSelection,
            inventory = inventoryById,
            applicationProfiles = profileSnapshots,
            bindingDigest = bindingDigest,
            lowerPreview = lowerPreview,
        )
    }

    private suspend fun validateInventoryDocument(
        stored: StoredCredential,
        credential: MdocsCredential,
        context: MdocHolderRequestContext,
    ): InventoryDocument {
        val document = credential.document
        require(document.docType == credential.docType) { "Stored mdoc document type is inconsistent" }
        val mso = credential.documentMso
        require(mso.docType == credential.docType) { "Stored mdoc MSO document type is inconsistent" }
        mso.validityInfo.precheck()
        mso.validityInfo.validate()
        val issuerAuthentication = verifyIssuerAuthentication(document)
        verifyIssuerSignedItemDigests(document, mso)
        val status = configuration.credentialStatusEvaluator.evaluate(
            ProximityCredentialStatusInput(
                credentialId = stored.id,
                docType = credential.docType,
                issuer = credential.issuer,
                validFrom = mso.validityInfo.validFrom,
                validUntil = mso.validityInfo.validUntil,
            )
        )
        require(status == ProximityCredentialStatus.Valid) {
            when (status) {
                ProximityCredentialStatus.Revoked -> "Stored mdoc credential is revoked"
                ProximityCredentialStatus.Indeterminate -> "Stored mdoc credential status is indeterminate"
                ProximityCredentialStatus.Valid -> error("unreachable")
            }
        }
        val authentication = try {
            resolveAuthentication(stored, context.readerEphemeralKey.value)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            throw ProximityException(
                EngineProximityError.Policy(
                    "holder_key_unavailable",
                    "The credential-bound holder key is unavailable",
                ),
                failure,
            )
        }
        if (authentication == null) throw ProximityException(
            EngineProximityError.Policy(
                "holder_key_unavailable",
                "The credential-bound holder key cannot use an allowed authentication method",
            )
        )
        return InventoryDocument(
            stored = stored,
            credential = credential,
            document = document,
            holderKey = authentication.holderKey,
            deviceAuthentication = authentication.method,
            issuerAuthorityKeyIdentifiers = issuerAuthentication.certificateChain.mapNotNull {
                it.authorityKeyIdentifier?.let(ImmutableBytes::of)
            }.distinct(),
        )
    }

    private suspend fun resolveAuthentication(
        stored: StoredCredential,
        readerEphemeralKey: CoseKey,
    ): ResolvedAuthentication? {
        return configuration.deviceAuthenticationPolicy.preferenceOrder.firstNotNullOfOrNull { method ->
            val requiredUsage = when (method) {
                ProximityDeviceAuthenticationMethod.Signature -> KeyUsage.SIGN
                ProximityDeviceAuthenticationMethod.Mac -> KeyUsage.KEY_AGREEMENT
            }
            val holderKey = try {
                wallet.resolveHolderKey(stored, setOf(requiredUsage))
            } catch (failure: HolderKeyBindingException) {
                if (failure.code == HolderKeyBindingErrorCode.KEY_USAGE_UNSUPPORTED) null else throw failure
            } ?: return@firstNotNullOfOrNull null
            val liveKey = holderKey.keyMaterial.requireCrypto2Key()
            val supported = when (method) {
                ProximityDeviceAuthenticationMethod.Signature -> runCatching {
                    liveKey.toCoseSigner(
                        liveKey.selectCoseSignatureAlgorithm(acceptedAlgorithms = null),
                    )
                }.isSuccess
                ProximityDeviceAuthenticationMethod.Mac ->
                    liveKey.supportsMdocDeviceMac(readerEphemeralKey)
            }
            if (supported) ResolvedAuthentication(holderKey, method) else null
        }
    }

    private fun InventoryDocument.toCandidate(): MdocCredentialCandidate = MdocCredentialCandidate(
        id = stored.id,
        docType = credential.docType,
        issuerAuthorityKeyIdentifiers = issuerAuthorityKeyIdentifiers,
        availableElements = document.issuerSigned.namespaces.orEmpty().flatMap { (namespace, values) ->
            values.entries.map { ElementReference(namespace, it.value.elementIdentifier) }
        },
        booleanElements = document.issuerSigned.namespaces.orEmpty().flatMap { (namespace, values) ->
            values.entries.mapNotNull { item ->
                (item.value.elementValue as? CborBoolean)?.value?.let { value ->
                    ElementReference(namespace, item.value.elementIdentifier) to value
                }
            }
        }.toMap(),
    )

    private suspend fun evaluateApplicationProfiles(
        context: MdocHolderRequestContext,
        inventory: List<InventoryDocument>,
        readerAuthentication: List<ProximityReaderAuthentication>,
    ): List<ApplicationProfileSnapshot> {
        if (configuration.applicationProfiles.profiles.isEmpty()) return emptyList()
        val input = ProximityApplicationProfileInput(
            deviceRequestBase64Url = context.request.encodedCopy().toBase64Url(),
            credentials = inventory.map {
                ProximityApplicationCredential(
                    credentialId = it.stored.id,
                    docType = it.credential.docType,
                    label = it.stored.label,
                )
            },
            requestedDocuments = context.request.value.docRequests.mapIndexed { index, request ->
                val items = request.itemsRequest.value
                ProximityApplicationDocumentRequest(
                    requestIndex = index,
                    docType = items.docType,
                    requestedElements = items.namespaces.flatMap { (namespace, elements) ->
                        elements.entries.map { element ->
                            ProximityRequestedElement(
                                namespace = namespace,
                                elementIdentifier = element.key,
                                intentToRetain = element.value,
                            )
                        }
                    },
                )
            },
            readerAuthentication = readerAuthentication,
        )
        val recognized = configuration.applicationProfiles.profiles.mapNotNull { profile ->
            val result = try {
                profile.evaluate(input.snapshot())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                throw ProximityException(
                    EngineProximityError.Policy(
                        "application_profile_failed",
                        "The application profile could not evaluate this request",
                    ),
                    failure,
                )
            }
            when (result) {
                ProximityApplicationProfileResult.NotRecognized -> null
                is ProximityApplicationProfileResult.Rejected -> throw ProximityException(
                    EngineProximityError.Policy("application_profile_rejected", result.reason)
                )
                is ProximityApplicationProfileResult.Recognized -> {
                    if (result.authorization.profileId != profile.id) throw ProximityException(
                        EngineProximityError.Policy(
                            "application_profile_invalid",
                            "The application profile returned an inconsistent identifier",
                        )
                    )
                    result.authorization.snapshot()
                }
            }
        }
        if (recognized.size > 1) throw ProximityException(
            EngineProximityError.Policy(
                "application_profile_ambiguous",
                "More than one application profile recognized the same request",
            )
        )
        val inventoryById = inventory.associateBy { it.stored.id }
        return try {
            recognized.map { authorization ->
                require(authorization.compatibleCredentialIds.all(inventoryById::containsKey)) {
                    "Application profile selected a credential outside the eligible inventory"
                }
                val digest = authorization.resultBindingDigestBase64Url.fromBase64Url()
                val decoded = authorization.deviceSignedElements.map { element ->
                    val inventoryDocument = inventoryById.getValue(element.credentialId)
                    requireDeviceElementAuthorized(inventoryDocument, element)
                    val bytes = element.valueCborBase64Url.fromBase64Url()
                    require(bytes.isNotEmpty() && bytes.size <= configuration.maximumMessageBytes)
                    element to coseCompliantCbor.decodeFromByteArray(CborElement.serializer(), bytes)
                }
                ApplicationProfileSnapshot(
                    public = authorization,
                    lower = MdocApplicationAuthorization(
                        profileId = authorization.profileId,
                        displayTitle = authorization.displayTitle,
                        details = authorization.details.map {
                            MdocApplicationAuthorizationDetail(it.id, it.label, it.value)
                        },
                        resultBindingDigest = ImmutableBytes.of(digest),
                    ),
                    decodedDeviceElements = decoded,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            throw ProximityException(
                EngineProximityError.Policy(
                    "application_profile_invalid",
                    "The application profile returned invalid authorization data",
                ),
                failure,
            )
        }
    }

    private fun requireDeviceElementAuthorized(
        inventory: InventoryDocument,
        element: ProximityDeviceSignedElement,
    ) {
        val authorization = inventory.credential.documentMso.deviceKeyInfo.keyAuthorizations
            ?: throw IllegalArgumentException("Application profile requires device-signed data without MSO KeyAuthorizations")
        val namespaceAuthorized = element.namespace in authorization.namespaces.orEmpty()
        val elementAuthorized = element.elementIdentifier in authorization.dataElements
            ?.get(element.namespace).orEmpty()
        require(namespaceAuthorized || elementAuthorized) {
            "Application profile device-signed element is not authorized by the credential MSO"
        }
    }

    private suspend fun evaluateReaderTrust(evidence: ReaderAuthenticationEvidence): ReaderTrustDecision {
        val publicEvidence = ProximityReaderEvidence(
            scope = evidence.scope.toPublic(),
            authenticationIndex = evidence.authenticationIndex,
            certificateChainDerBase64Url = evidence.certificateChainDer.map { it.copy().toBase64Url() },
        )
        val decision = configuration.readerTrustEvaluator.evaluate(publicEvidence)
        evaluatedReaderTrust[
            ReaderTrustKey(
                publicEvidence.scope,
                publicEvidence.authenticationIndex,
            )
        ] = decision
        return ReaderTrustDecision(
            state = decision.state.toEngine(),
            reason = decision.reason,
            displayName = decision.displayName,
        )
    }

    private fun enforceReaderPolicy(
        authentication: DeviceRequestReaderAuthentication,
        selectedRequestIndices: Set<Int>,
    ) {
        val all = authentication.documents + authentication.wholeRequest
        val display = authentication.toDisplaySafe()
        val displayAll = display.documents + display.wholeRequest
        if (displayAll.any {
                it.validity == ReaderAuthenticationDisplayValidity.MALFORMED ||
                    it.validity == ReaderAuthenticationDisplayValidity.INVALID
            }
        ) {
            throw ProximityException(
                EngineProximityError.Security(
                    "invalid_reader_authentication",
                    "Reader authentication is malformed or cryptographically invalid",
                )
            )
        }
        if (all.any { (it as? ReaderAuthenticationResult.Valid)?.trust?.state == ReaderTrustState.REVOKED }) {
            throw ProximityException(
                EngineProximityError.Policy("reader_revoked", "The authenticated reader is revoked")
            )
        }
        if (configuration.readerPolicy == ProximityReaderPolicy.RequireTrusted) {
            val trustedWholeRequest = authentication.wholeRequest.any { (it as? ReaderAuthenticationResult.Valid)?.trust?.state == ReaderTrustState.TRUSTED }
            val selectedDocuments = selectedRequestIndices.map(authentication.documents::get)
            val everyDocumentTrusted = selectedDocuments.isNotEmpty() &&
                selectedDocuments.all { (it as? ReaderAuthenticationResult.Valid)?.trust?.state == ReaderTrustState.TRUSTED }
            if (!trustedWholeRequest && !everyDocumentTrusted) {
                throw ProximityException(
                    EngineProximityError.Policy(
                        "trusted_reader_required",
                        "The selected profile requires an authenticated and trusted reader",
                    )
                )
            }
        }
    }

    private suspend fun buildResponse(
        context: MdocHolderRequestContext,
        snapshot: Snapshot,
        submission: ProximitySubmission,
        applicationProfiles: List<ApplicationProfileSnapshot>,
    ): DeviceResponse {
        val presentations = mutableListOf<Pair<Int, MdocDocumentPresentation>>()
        submission.documents.forEach { submitted ->
            val choice = snapshot.selection.eligibleDocuments.single {
                it.requestIndex == submitted.requestIndex && it.credentialId == submitted.credentialId
            }
            val inventory = snapshot.inventory.getValue(submitted.credentialId)
            val offered = choice.elements.map { it.reference }.toSet()
            val disclosed = submitted.disclosedElements.map {
                ElementReference(it.namespace, it.elementIdentifier)
            }.toSet()
            require(disclosed.all { it in offered }) { "Submission selected an element outside the current review" }
            val effectiveDisclosures = if (MDL_PORTRAIT in offered && MDL_PORTRAIT !in disclosed) {
                emptySet()
            } else {
                disclosed
            }
            val deviceNamespaces = applicationProfiles
                .flatMap(ApplicationProfileSnapshot::decodedDeviceElements)
                .filter { (element, _) -> element.credentialId == submitted.credentialId }
                .groupBy { (element, _) -> element.namespace }
                .mapValues { (_, elements) ->
                    DeviceSignedItemList(
                        elements.map { (element, value) -> DeviceSignedItem(element.elementIdentifier, value) }
                    )
                }
                .let(::DeviceNameSpaces)
            presentations += submitted.requestIndex to MdocDocumentPresentation(
                source = inventory.document,
                holderKey = inventory.holderKey.keyMaterial.requireCrypto2Key(),
                selectedIssuerElements = effectiveDisclosures,
                deviceNameSpaces = deviceNamespaces,
                authentication = when (inventory.deviceAuthentication) {
                    ProximityDeviceAuthenticationMethod.Signature ->
                        MdocAuthenticationMethod.Signature()
                    ProximityDeviceAuthenticationMethod.Mac ->
                        MdocAuthenticationMethod.Mac(context.readerEphemeralKey.value)
                },
            )
        }
        val ordinary = mutableListOf<MdocDocumentPresentation>()
        val encrypted = mutableListOf<id.walt.mdoc.objects.deviceretrieval.EncryptedDocuments>()
        presentations.forEach { (requestIndex, presentation) ->
            val encryption = context.request.value.docRequests[requestIndex].itemsRequest.value
                .requestInfo?.docResponseEncryption
            if (encryption == null) ordinary += presentation
            else encrypted += responseBuilder.buildEncryptedDocuments(
                docRequestId = requestIndex.toUInt(),
                presentations = listOf(presentation),
                transcript = context.transcript.value,
                encryptionParameters = encryption,
            )
        }
        return responseBuilder.buildResponse(
            presentations = ordinary,
            transcript = context.transcript.value,
            encryptedDocuments = encrypted,
        )
    }

    private fun validateSubmission(
        review: ProximityReview,
        submission: ProximitySubmission,
    ): ProximityError? {
        if (submission.documents.map { it.requestIndex }.toSet() != review.documents.map { it.requestIndex }.toSet()) {
            return staleError("Exactly one current credential choice is required for every reviewed document")
        }
        submission.documents.forEach { selected ->
            val document = review.documents.singleOrNull { it.requestIndex == selected.requestIndex }
                ?: return staleError("A submitted document was not part of the current review")
            val credential = document.credentialOptions.singleOrNull { it.credentialId == selected.credentialId }
                ?: return staleError("A submitted credential was not offered by the current review")
            val offered = credential.requestedElements.map {
                ProximityElementReference(it.namespace, it.elementIdentifier)
            }.toSet()
            if (!selected.disclosedElements.all { it in offered }) {
                return staleError("A submitted disclosure was not offered by the current review")
            }
        }
        return null
    }

    private fun SelectedDocument.toPublicOption(
        inventory: InventoryDocument,
    ): ProximityCredentialOption = ProximityCredentialOption(
        credentialId = credentialId,
        label = inventory.stored.label,
        issuer = inventory.credential.issuer,
        validUntil = inventory.credential.documentMso.validityInfo.validUntil,
        deviceAuthentication = inventory.deviceAuthentication,
        requestedElements = elements.sortedWith(
            compareBy({ it.reference.namespace }, { it.reference.elementIdentifier })
        ).map { element ->
            ProximityRequestedElement(
                namespace = element.reference.namespace,
                elementIdentifier = element.reference.elementIdentifier,
                intentToRetain = element.intentToRetain,
                satisfiesRequestedElements = element.satisfiesAlternativesFor.map {
                    ProximityElementReference(it.namespace, it.elementIdentifier)
                },
            )
        },
    )

    private fun DeviceRequestReaderAuthentication.toPublicEntries():
        List<ProximityReaderAuthentication> {
        val display = toDisplaySafe()
        return (display.documents + display.wholeRequest).map { it.toPublic() }
    }

    private fun ReaderAuthenticationDisplayEntry.toPublic(): ProximityReaderAuthentication {
        val decision = evaluatedReaderTrust[ReaderTrustKey(scope.toPublic(), authenticationIndex)]
        return ProximityReaderAuthentication(
            scope = scope.toPublic(),
            authenticationIndex = authenticationIndex,
            outcome = when (validity) {
                ReaderAuthenticationDisplayValidity.ABSENT -> ProximityReaderAuthenticationOutcome.Absent
                ReaderAuthenticationDisplayValidity.MALFORMED ->
                    ProximityReaderAuthenticationOutcome.Malformed(reason ?: "Malformed reader authentication")
                ReaderAuthenticationDisplayValidity.INVALID ->
                    ProximityReaderAuthenticationOutcome.Invalid(reason ?: "Invalid reader authentication")
                ReaderAuthenticationDisplayValidity.VALID -> ProximityReaderAuthenticationOutcome.Valid(
                    requireNotNull(decision) { "Verified reader trust evaluation is missing" },
                )
            },
        )
    }

    private fun choiceDigest(
        reviewId: ProximityReviewId,
        submission: ProximitySubmission,
        profiles: List<ApplicationProfileSnapshot>,
    ): ImmutableBytes {
        val values = buildList {
            add("walt.id/mobile-wallet-proximity-choice/v1".encodeToByteArray())
            add(reviewId.value.encodeToByteArray())
            add(byteArrayOf(if (submission.continueAfterResponse) 1 else 0))
            add(intBytes(submission.documents.size))
            submission.documents.forEach { document ->
                add(intBytes(document.requestIndex))
                add(document.credentialId.encodeToByteArray())
                add(intBytes(document.disclosedElements.size))
                document.disclosedElements.sortedWith(compareBy({ it.namespace }, { it.elementIdentifier })).forEach {
                    add(it.namespace.encodeToByteArray())
                    add(it.elementIdentifier.encodeToByteArray())
                }
            }
            add(intBytes(profiles.size))
            profiles.forEach { profile ->
                val authorization = profile.public
                add(authorization.profileId.encodeToByteArray())
                add(authorization.displayTitle.encodeToByteArray())
                add(authorization.resultBindingDigestBase64Url.fromBase64Url())
                add(intBytes(authorization.details.size))
                authorization.details.forEach {
                    add(it.id.encodeToByteArray())
                    add(it.label.encodeToByteArray())
                    add(it.value.encodeToByteArray())
                }
                add(intBytes(authorization.compatibleCredentialIds.size))
                authorization.compatibleCredentialIds.sorted().forEach { add(it.encodeToByteArray()) }
                add(intBytes(authorization.deviceSignedElements.size))
                authorization.deviceSignedElements.forEach {
                    add(it.credentialId.encodeToByteArray())
                    add(it.namespace.encodeToByteArray())
                    add(it.elementIdentifier.encodeToByteArray())
                    add(it.valueCborBase64Url.fromBase64Url())
                }
            }
        }
        return ImmutableBytes.of(SHA256().digest(values.fold(intBytes(values.size)) { bytes, value -> bytes + lengthPrefixed(value) }))
    }

    private fun snapshotDigest(
        context: MdocHolderRequestContext,
        inventory: List<InventoryDocument>,
        eligible: List<SelectedDocument>,
        review: ProximityReview,
        profiles: List<ApplicationProfileSnapshot>,
    ): ImmutableBytes {
        val values = buildList {
            add("walt.id/mobile-wallet-proximity-snapshot/v1".encodeToByteArray())
            add(context.request.encodedCopy())
            add(context.transcript.encodedCopy())
            add(context.exchange.toString().encodeToByteArray())
            inventory.sortedBy { it.stored.id }.forEach { item ->
                add(item.stored.id.encodeToByteArray())
                add(item.credential.docType.encodeToByteArray())
                add(requireNotNull(item.credential.signed).encodeToByteArray())
                val binding = item.holderKey.binding
                add(binding.schemaVersion.toString().encodeToByteArray())
                add(binding.keyReference.encodeToByteArray())
                add(binding.publicKeyThumbprint.algorithm.encodeToByteArray())
                add(binding.publicKeyThumbprint.value.encodeToByteArray())
                add(item.deviceAuthentication.name.encodeToByteArray())
            }
            eligible.sortedWith(compareBy(SelectedDocument::requestIndex, SelectedDocument::credentialId)).forEach { option ->
                add(option.requestIndex.toString().encodeToByteArray())
                add(option.credentialId.encodeToByteArray())
                option.elements.sortedWith(compareBy({ it.reference.namespace }, { it.reference.elementIdentifier }))
                    .forEach { element ->
                        add(element.reference.namespace.encodeToByteArray())
                        add(element.reference.elementIdentifier.encodeToByteArray())
                        add(byteArrayOf(if (element.intentToRetain) 1 else 0))
                    }
            }
            review.readerAuthentication.forEach { entry ->
                add((if (entry.scope is ProximityReaderAuthenticationScope.Document) "Document" else "WholeRequest").encodeToByteArray())
                add((entry.scope.documentRequestIndex?.toString() ?: "-").encodeToByteArray())
                add(entry.authenticationIndex.toString().encodeToByteArray())
                add(entry.validity.name.encodeToByteArray())
                add(entry.trust.name.encodeToByteArray())
                add(entry.certificatePath.name.encodeToByteArray())
                add(entry.revocation.name.encodeToByteArray())
                add(entry.rical.name.encodeToByteArray())
                add(entry.displayName.orEmpty().encodeToByteArray())
                add(entry.reason.orEmpty().encodeToByteArray())
            }
            profiles.forEach { profile ->
                add(profile.public.profileId.encodeToByteArray())
                add(profile.public.displayTitle.encodeToByteArray())
                profile.public.details.forEach { detail ->
                    add(detail.id.encodeToByteArray())
                    add(detail.label.encodeToByteArray())
                    add(detail.value.encodeToByteArray())
                }
                add(profile.public.resultBindingDigestBase64Url.fromBase64Url())
                profile.public.compatibleCredentialIds.sorted().forEach { add(it.encodeToByteArray()) }
                profile.public.deviceSignedElements.forEach { element ->
                    add(element.credentialId.encodeToByteArray())
                    add(element.namespace.encodeToByteArray())
                    add(element.elementIdentifier.encodeToByteArray())
                    add(element.valueCborBase64Url.fromBase64Url())
                }
            }
        }
        val material = values.fold(intBytes(values.size)) { bytes, value -> bytes + lengthPrefixed(value) }
        return ImmutableBytes.of(SHA256().digest(material))
    }
}

private val MDL_PORTRAIT = ElementReference("org.iso.18013.5.1", "portrait")

private fun ReaderAuthenticationScope.toPublic(): ProximityReaderAuthenticationScope = when (this) {
    is ReaderAuthenticationScope.Document -> ProximityReaderAuthenticationScope.Document(index)
    ReaderAuthenticationScope.WholeRequest -> ProximityReaderAuthenticationScope.WholeRequest
}

private fun ProximityReaderTrustState.toEngine(): ReaderTrustState = when (this) {
    ProximityReaderTrustState.NotEvaluated ->
        throw IllegalArgumentException("A reader trust evaluator must return an evaluated decision")
    ProximityReaderTrustState.ValidButUntrusted -> ReaderTrustState.VALID_BUT_UNTRUSTED
    ProximityReaderTrustState.Revoked -> ReaderTrustState.REVOKED
    ProximityReaderTrustState.Trusted -> ReaderTrustState.TRUSTED
}

private fun ByteArray.toBase64Url(): String =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(this)

private fun String.fromBase64Url(): ByteArray =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(this)

private fun staleError(message: String): ProximityError = ProximityError(
    category = ProximityErrorCategory.StaleSubmission,
    code = "stale_submission",
    message = message,
    recovery = ProximityRecovery.None,
)

private fun intBytes(value: Int): ByteArray = byteArrayOf(
    (value ushr 24).toByte(),
    (value ushr 16).toByte(),
    (value ushr 8).toByte(),
    value.toByte(),
)

private fun lengthPrefixed(value: ByteArray): ByteArray = intBytes(value.size) + value

/** Reparse the authoritative signed string into a private mdoc graph; never re-encode signed DTOs. */
private fun StoredCredential.snapshotForProximity(): StoredCredential {
    val mdoc = credential as? MdocsCredential ?: return this
    return StoredCredential(
        id = id,
        credential = MdocsCredential(
            credentialData = JsonObject(emptyMap()),
            signed = mdoc.signed,
            docType = mdoc.docType,
            issuer = mdoc.issuer,
            subject = mdoc.subject,
        ),
        label = label,
        addedAt = addedAt,
        holderKeyBinding = holderKeyBinding,
    )
}
