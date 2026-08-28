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
import id.walt.mdoc.proximity.MdocRequestPreview
import id.walt.mdoc.proximity.MdocRequestSelection
import id.walt.mdoc.proximity.MdocResponseBuilder
import id.walt.mdoc.proximity.MdocResponseResolution
import id.walt.mdoc.proximity.MdocSessionContinuation
import id.walt.mdoc.proximity.PreviewDocument
import id.walt.mdoc.proximity.PreviewElement
import id.walt.mdoc.proximity.ProximityError
import id.walt.mdoc.proximity.ProximityException
import id.walt.mdoc.proximity.ReaderAuthenticationDisplayEntry
import id.walt.mdoc.proximity.ReaderAuthenticationDisplayValidity
import id.walt.mdoc.proximity.ReaderAuthenticationEvidence
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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.Base64

internal class MobileWalletProximityRequestProcessor(
    private val wallet: Wallet,
    private val configuration: MobileWalletProximityConfiguration,
    readerAuthenticationAlgorithms: Set<Int>,
) : MdocHolderRequestProcessor {
    private data class InventoryDocument(
        val stored: StoredCredential,
        val credential: MdocsCredential,
        val document: Document,
        val holderKey: ResolvedHolderKey,
        val deviceAuthentication: MobileWalletProximityDeviceAuthenticationMethod,
        val issuerAuthorityKeyIdentifiers: List<ImmutableBytes>,
    )

    private data class ApplicationProfileSnapshot(
        val public: MobileWalletProximityApplicationAuthorization,
        val lower: MdocApplicationAuthorization,
        val decodedDeviceElements: List<Pair<MobileWalletProximityDeviceSignedElement, CborElement>>,
    )

    private data class Snapshot(
        val exchange: Int,
        val review: MobileWalletProximityReview,
        val selection: MdocRequestSelection,
        val inventory: Map<String, InventoryDocument>,
        val applicationProfiles: List<ApplicationProfileSnapshot>,
        val bindingDigest: ImmutableBytes,
        val lowerPreview: MdocRequestPreview,
    )

    private data class Approved(
        val exchange: Int,
        val submission: MobileWalletProximitySubmission,
    )

    private data class ReaderTrustKey(
        val scope: MobileWalletProximityReaderAuthenticationScope,
        val documentRequestIndex: Int?,
        val authenticationIndex: Int,
    )

    private data class ResolvedAuthentication(
        val holderKey: ResolvedHolderKey,
        val method: MobileWalletProximityDeviceAuthenticationMethod,
    )

    private val readerVerifier = ReaderAuthenticationVerifier(
        trustEvaluator = ReaderTrustEvaluator(::evaluateReaderTrust),
        allowedAlgorithms = readerAuthenticationAlgorithms,
    )
    private val matcher = MdocRequestMatcher()
    private val responseBuilder = MdocResponseBuilder()
    private val stateMutex = Mutex()
    private val evaluatedReaderTrust = mutableMapOf<ReaderTrustKey, MobileWalletProximityReaderTrustDecision>()
    private var currentSnapshot: Snapshot? = null
    private var approved: Approved? = null

    override suspend fun preview(context: MdocHolderRequestContext): MdocRequestPreview {
        val snapshot = buildSnapshot(context)
        stateMutex.withLock {
            check(currentSnapshot == null) { "A previous proximity review has not been consumed" }
            currentSnapshot = snapshot
        }
        return snapshot.lowerPreview
    }

    suspend fun review(prompt: MdocConsentPrompt): MobileWalletProximityReview = stateMutex.withLock {
        val snapshot = requireNotNull(currentSnapshot) { "The proximity review snapshot is unavailable" }
        require(snapshot.exchange == prompt.exchange)
        require(snapshot.bindingDigest == prompt.preview.submissionBindingDigest)
        snapshot.review
    }

    /** Returns a stable error when invalid; otherwise retains the exact immutable submission. */
    suspend fun accept(
        prompt: MdocConsentPrompt,
        submission: MobileWalletProximitySubmission,
    ): MobileWalletProximityError? = stateMutex.withLock {
        val snapshot = currentSnapshot
            ?: return@withLock staleError("The proximity review is no longer current")
        if (snapshot.exchange != prompt.exchange || snapshot.bindingDigest != prompt.preview.submissionBindingDigest) {
            return@withLock staleError("The proximity action does not belong to the current review")
        }
        validateSubmission(snapshot.review, submission)?.let { return@withLock it }
        if (approved != null) return@withLock staleError("The proximity review was already submitted")
        approved = Approved(prompt.exchange, submission)
        null
    }

    suspend fun holderAuthorization(
        prompt: MdocConsentPrompt,
        submission: MobileWalletProximitySubmission,
    ): MobileWalletProximityHolderAuthorization = stateMutex.withLock {
        val snapshot = requireNotNull(currentSnapshot) { "The proximity review snapshot is unavailable" }
        require(snapshot.exchange == prompt.exchange)
        require(snapshot.bindingDigest == prompt.preview.submissionBindingDigest)
        require(approved?.submission == submission) { "The submission has not been accepted" }
        MobileWalletProximityHolderAuthorization(
            exchange = prompt.exchange,
            requests = submission.documents.sortedBy { it.requestIndex }.map { selected ->
                val method = snapshot.review.documents
                    .single { it.requestIndex == selected.requestIndex }
                    .credentialOptions.single { it.credentialId == selected.credentialId }
                    .deviceAuthentication
                MobileWalletProximityHolderAuthorizationRequest(
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
            require(value.exchange == context.exchange)
            approved = null
            currentSnapshot = null
            value
        }
        val fresh = buildSnapshot(context)
        if (fresh.bindingDigest != preview.submissionBindingDigest) {
            throw ProximityException(
                ProximityError.Security(
                    "changed_submission",
                    "Credential, trust, status, or application-profile state changed after consent",
                )
            )
        }
        validateSubmission(fresh.review, retained.submission)?.let { error ->
            throw ProximityException(ProximityError.Security(error.code, error.message))
        }
        val response = buildResponse(context, fresh, retained.submission)
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
            wallet.streamAllCredentials().toList()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: HolderKeyBindingException) {
            throw ProximityException(
                ProximityError.Policy(
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
                        ProximityError.Policy(
                            "credential_unavailable",
                            "A required credential cannot be used for this proximity presentation",
                        ),
                        failure,
                    )
                }
                throw ProximityException(ProximityError.Policy("request_unsatisfied", result.reason))
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
                    ProximityError.Policy(
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
            MobileWalletProximityDocumentReview(
                requestIndex = requestIndex,
                docType = docType,
                credentialOptions = eligible.filter { it.requestIndex == requestIndex }
                    .sortedBy(SelectedDocument::credentialId)
                    .map { selected -> selected.toPublicOption(inventoryById.getValue(selected.credentialId)) },
            )
        }
        val useCases = selection.useCases.map { selected ->
            MobileWalletProximityUseCase(
                index = selected.index,
                mandatory = selected.mandatory,
                documentRequestIndices = selected.documentSet,
                purposeHints = selected.purposeHints.map { (type, code) ->
                    MobileWalletProximityPurposeHint(type, code)
                },
            )
        }
        val review = MobileWalletProximityReview(
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
            MobileWalletProximityCredentialStatusInput(
                credentialId = stored.id,
                docType = credential.docType,
                issuer = credential.issuer,
                validFrom = mso.validityInfo.validFrom,
                validUntil = mso.validityInfo.validUntil,
            )
        )
        require(status == MobileWalletProximityCredentialStatus.Valid) {
            when (status) {
                MobileWalletProximityCredentialStatus.Revoked -> "Stored mdoc credential is revoked"
                MobileWalletProximityCredentialStatus.Indeterminate -> "Stored mdoc credential status is indeterminate"
                MobileWalletProximityCredentialStatus.Valid -> error("unreachable")
            }
        }
        val authentication = try {
            resolveAuthentication(stored, context.readerEphemeralKey.value)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            throw ProximityException(
                ProximityError.Policy(
                    "holder_key_unavailable",
                    "The credential-bound holder key is unavailable",
                ),
                failure,
            )
        }
        if (authentication == null) throw ProximityException(
            ProximityError.Policy(
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
            issuerAuthorityKeyIdentifiers = listOfNotNull(
                issuerAuthentication.certificateChain.firstOrNull()?.authorityKeyIdentifier
                    ?.let(ImmutableBytes::of)
            ),
        )
    }

    private suspend fun resolveAuthentication(
        stored: StoredCredential,
        readerEphemeralKey: CoseKey,
    ): ResolvedAuthentication? {
        return configuration.deviceAuthenticationPolicy.preferenceOrder.firstNotNullOfOrNull { method ->
            val requiredUsage = when (method) {
                MobileWalletProximityDeviceAuthenticationMethod.Signature -> KeyUsage.SIGN
                MobileWalletProximityDeviceAuthenticationMethod.Mac -> KeyUsage.KEY_AGREEMENT
            }
            val holderKey = try {
                wallet.resolveHolderKey(stored, setOf(requiredUsage))
            } catch (failure: HolderKeyBindingException) {
                if (failure.code == HolderKeyBindingErrorCode.KEY_USAGE_UNSUPPORTED) null else throw failure
            } ?: return@firstNotNullOfOrNull null
            val liveKey = holderKey.keyMaterial.requireCrypto2Key()
            val supported = when (method) {
                MobileWalletProximityDeviceAuthenticationMethod.Signature -> runCatching {
                    liveKey.toCoseSigner(
                        liveKey.selectCoseSignatureAlgorithm(acceptedAlgorithms = null),
                    )
                }.isSuccess
                MobileWalletProximityDeviceAuthenticationMethod.Mac ->
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
    )

    private suspend fun evaluateApplicationProfiles(
        context: MdocHolderRequestContext,
        inventory: List<InventoryDocument>,
        readerAuthentication: List<MobileWalletProximityReaderAuthentication>,
    ): List<ApplicationProfileSnapshot> {
        if (configuration.applicationProfiles.profiles.isEmpty()) return emptyList()
        val input = MobileWalletProximityApplicationProfileInput(
            deviceRequestBase64Url = context.request.encodedCopy().toBase64Url(),
            credentials = inventory.map {
                MobileWalletProximityApplicationCredential(
                    credentialId = it.stored.id,
                    docType = it.credential.docType,
                    label = it.stored.label,
                )
            },
            requestedDocuments = context.request.value.docRequests.mapIndexed { index, request ->
                val items = request.itemsRequest.value
                MobileWalletProximityApplicationDocumentRequest(
                    requestIndex = index,
                    docType = items.docType,
                    requestedElements = items.namespaces.flatMap { (namespace, elements) ->
                        elements.entries.map { element ->
                            MobileWalletProximityRequestedElement(
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
                profile.evaluate(input)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                throw ProximityException(
                    ProximityError.Policy(
                        "application_profile_failed",
                        "The application profile could not evaluate this request",
                    ),
                    failure,
                )
            }
            when (result) {
                MobileWalletProximityApplicationProfileResult.NotRecognized -> null
                is MobileWalletProximityApplicationProfileResult.Rejected -> throw ProximityException(
                    ProximityError.Policy("application_profile_rejected", result.reason)
                )
                is MobileWalletProximityApplicationProfileResult.Recognized -> {
                    if (result.authorization.profileId != profile.id) throw ProximityException(
                        ProximityError.Policy(
                            "application_profile_invalid",
                            "The application profile returned an inconsistent identifier",
                        )
                    )
                    result.authorization
                }
            }
        }
        if (recognized.size > 1) throw ProximityException(
            ProximityError.Policy(
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
                ProximityError.Policy(
                    "application_profile_invalid",
                    "The application profile returned invalid authorization data",
                ),
                failure,
            )
        }
    }

    private fun requireDeviceElementAuthorized(
        inventory: InventoryDocument,
        element: MobileWalletProximityDeviceSignedElement,
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
        val publicEvidence = MobileWalletProximityReaderEvidence(
            scope = evidence.scope.toPublic(),
            documentRequestIndex = evidence.documentRequestIndex,
            authenticationIndex = evidence.authenticationIndex,
            certificateChainDerBase64Url = evidence.certificateChainDer.map { it.copy().toBase64Url() },
        )
        val decision = configuration.readerTrustEvaluator.evaluate(publicEvidence)
        evaluatedReaderTrust[
            ReaderTrustKey(
                publicEvidence.scope,
                publicEvidence.documentRequestIndex,
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
                ProximityError.Security(
                    "invalid_reader_authentication",
                    "Reader authentication is malformed or cryptographically invalid",
                )
            )
        }
        if (all.any { it.trust.state == ReaderTrustState.REVOKED }) {
            throw ProximityException(
                ProximityError.Policy("reader_revoked", "The authenticated reader is revoked")
            )
        }
        if (configuration.readerPolicy == MobileWalletProximityReaderPolicy.RequireTrusted) {
            val trustedWholeRequest = authentication.wholeRequest.any { it.trust.state == ReaderTrustState.TRUSTED }
            val selectedDocuments = selectedRequestIndices.map(authentication.documents::get)
            val everyDocumentTrusted = selectedDocuments.isNotEmpty() &&
                selectedDocuments.all { it.trust.state == ReaderTrustState.TRUSTED }
            if (!trustedWholeRequest && !everyDocumentTrusted) {
                throw ProximityException(
                    ProximityError.Policy(
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
        submission: MobileWalletProximitySubmission,
    ): DeviceResponse {
        val presentations = mutableListOf<Pair<Int, MdocDocumentPresentation>>()
        submission.documents.sortedBy { it.requestIndex }.forEach { submitted ->
            val choice = snapshot.selection.eligibleDocuments.single {
                it.requestIndex == submitted.requestIndex && it.credentialId == submitted.credentialId
            }
            val inventory = snapshot.inventory.getValue(submitted.credentialId)
            val offered = choice.elements.map { it.reference }.toSet()
            val disclosed = submitted.disclosedElements.map {
                ElementReference(it.namespace, it.elementIdentifier)
            }.toSet()
            require(disclosed.all { it in offered }) { "Submission selected an element outside the current review" }
            val deviceNamespaces = snapshot.applicationProfiles
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
                selectedIssuerElements = disclosed,
                deviceNameSpaces = deviceNamespaces,
                authentication = when (inventory.deviceAuthentication) {
                    MobileWalletProximityDeviceAuthenticationMethod.Signature ->
                        MdocAuthenticationMethod.Signature()
                    MobileWalletProximityDeviceAuthenticationMethod.Mac ->
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
        review: MobileWalletProximityReview,
        submission: MobileWalletProximitySubmission,
    ): MobileWalletProximityError? {
        if (submission.documents.map { it.requestIndex }.toSet() != review.documents.map { it.requestIndex }.toSet()) {
            return staleError("Exactly one current credential choice is required for every reviewed document")
        }
        submission.documents.forEach { selected ->
            val document = review.documents.singleOrNull { it.requestIndex == selected.requestIndex }
                ?: return staleError("A submitted document was not part of the current review")
            val credential = document.credentialOptions.singleOrNull { it.credentialId == selected.credentialId }
                ?: return staleError("A submitted credential was not offered by the current review")
            val offered = credential.requestedElements.map {
                MobileWalletProximityElementReference(it.namespace, it.elementIdentifier)
            }.toSet()
            if (!selected.disclosedElements.all { it in offered }) {
                return staleError("A submitted disclosure was not offered by the current review")
            }
        }
        return null
    }

    private fun SelectedDocument.toPublicOption(
        inventory: InventoryDocument,
    ): MobileWalletProximityCredentialOption = MobileWalletProximityCredentialOption(
        credentialId = credentialId,
        label = inventory.stored.label,
        issuer = inventory.credential.issuer,
        validUntil = inventory.credential.documentMso.validityInfo.validUntil,
        deviceAuthentication = inventory.deviceAuthentication,
        requestedElements = elements.sortedWith(
            compareBy({ it.reference.namespace }, { it.reference.elementIdentifier })
        ).map { element ->
            MobileWalletProximityRequestedElement(
                namespace = element.reference.namespace,
                elementIdentifier = element.reference.elementIdentifier,
                intentToRetain = element.intentToRetain,
                satisfiesRequestedElements = element.satisfiesAlternativesFor.map {
                    MobileWalletProximityElementReference(it.namespace, it.elementIdentifier)
                },
            )
        },
    )

    private fun DeviceRequestReaderAuthentication.toPublicEntries():
        List<MobileWalletProximityReaderAuthentication> {
        val display = toDisplaySafe()
        return (display.documents + display.wholeRequest).map { it.toPublic() }
    }

    private fun ReaderAuthenticationDisplayEntry.toPublic(): MobileWalletProximityReaderAuthentication =
        evaluatedReaderTrust[
            ReaderTrustKey(scope.toPublic(), documentRequestIndex, authenticationIndex)
        ].let { decision ->
            MobileWalletProximityReaderAuthentication(
                scope = scope.toPublic(),
                documentRequestIndex = documentRequestIndex,
                authenticationIndex = authenticationIndex,
                validity = when (validity) {
                    ReaderAuthenticationDisplayValidity.ABSENT -> MobileWalletProximityReaderAuthenticationValidity.Absent
                    ReaderAuthenticationDisplayValidity.MALFORMED -> MobileWalletProximityReaderAuthenticationValidity.Malformed
                    ReaderAuthenticationDisplayValidity.INVALID -> MobileWalletProximityReaderAuthenticationValidity.Invalid
                    ReaderAuthenticationDisplayValidity.VALID -> MobileWalletProximityReaderAuthenticationValidity.Valid
                },
                trust = when (trust) {
                    ReaderTrustState.NOT_EVALUATED -> MobileWalletProximityReaderTrustState.NotEvaluated
                    ReaderTrustState.VALID_BUT_UNTRUSTED -> MobileWalletProximityReaderTrustState.ValidButUntrusted
                    ReaderTrustState.REVOKED -> MobileWalletProximityReaderTrustState.Revoked
                    ReaderTrustState.TRUSTED -> MobileWalletProximityReaderTrustState.Trusted
                },
                certificatePath = decision?.certificatePath
                    ?: MobileWalletProximityReaderCertificatePathState.NotEvaluated,
                revocation = decision?.revocation ?: MobileWalletProximityReaderRevocationState.NotChecked,
                rical = decision?.rical ?: MobileWalletProximityRicalState.NotEvaluated,
                displayName = displayName,
                reason = reason,
            )
        }

    private fun snapshotDigest(
        context: MdocHolderRequestContext,
        inventory: List<InventoryDocument>,
        eligible: List<SelectedDocument>,
        review: MobileWalletProximityReview,
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
                add(entry.scope.name.encodeToByteArray())
                add((entry.documentRequestIndex?.toString() ?: "-").encodeToByteArray())
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
                profile.public.details.sortedBy { it.id }.forEach { detail ->
                    add(detail.id.encodeToByteArray())
                    add(detail.label.encodeToByteArray())
                    add(detail.value.encodeToByteArray())
                }
                add(profile.public.resultBindingDigestBase64Url.fromBase64Url())
                profile.public.compatibleCredentialIds.sorted().forEach { add(it.encodeToByteArray()) }
                profile.public.deviceSignedElements.sortedWith(
                    compareBy(
                        MobileWalletProximityDeviceSignedElement::credentialId,
                        MobileWalletProximityDeviceSignedElement::namespace,
                        MobileWalletProximityDeviceSignedElement::elementIdentifier,
                    )
                ).forEach { element ->
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

private fun ReaderAuthenticationScope.toPublic(): MobileWalletProximityReaderAuthenticationScope = when (this) {
    ReaderAuthenticationScope.DOCUMENT -> MobileWalletProximityReaderAuthenticationScope.Document
    ReaderAuthenticationScope.WHOLE_REQUEST -> MobileWalletProximityReaderAuthenticationScope.WholeRequest
}

private fun MobileWalletProximityReaderTrustState.toEngine(): ReaderTrustState = when (this) {
    MobileWalletProximityReaderTrustState.NotEvaluated ->
        throw IllegalArgumentException("A reader trust evaluator must return an evaluated decision")
    MobileWalletProximityReaderTrustState.ValidButUntrusted -> ReaderTrustState.VALID_BUT_UNTRUSTED
    MobileWalletProximityReaderTrustState.Revoked -> ReaderTrustState.REVOKED
    MobileWalletProximityReaderTrustState.Trusted -> ReaderTrustState.TRUSTED
}

private fun ByteArray.toBase64Url(): String =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(this)

private fun String.fromBase64Url(): ByteArray =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(this)

private fun staleError(message: String): MobileWalletProximityError = MobileWalletProximityError(
    category = MobileWalletProximityErrorCategory.StaleSubmission,
    code = "stale_submission",
    message = message,
    recoverable = true,
)

private fun intBytes(value: Int): ByteArray = byteArrayOf(
    (value ushr 24).toByte(),
    (value ushr 16).toByte(),
    (value ushr 8).toByte(),
    value.toByte(),
)

private fun lengthPrefixed(value: ByteArray): ByteArray = intBytes(value.size) + value
