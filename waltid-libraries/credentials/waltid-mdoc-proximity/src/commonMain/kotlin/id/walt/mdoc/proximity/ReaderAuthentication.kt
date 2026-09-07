@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.cose.verifyDetached
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.ReaderAuthenticationPayloads
import kotlinx.io.bytestring.ByteString
import kotlinx.coroutines.CancellationException

/** Scope is part of the statement identity; whole-request statements have no document index. */
sealed interface ReaderAuthenticationScope {
    data class Document(val index: Int) : ReaderAuthenticationScope {
        init { require(index >= 0) { "Document request index must not be negative" } }
    }
    data object WholeRequest : ReaderAuthenticationScope
}

class ReaderAuthenticationEvidence(
    val scope: ReaderAuthenticationScope,
    /** Zero-based statement index within the authentication scope. */
    val authenticationIndex: Int = 0,
    certificateChainDer: List<ImmutableBytes> = emptyList(),
) {
    private val certificateChain = certificateChainDer.toList()
    val certificateChainDer: List<ImmutableBytes> get() = certificateChain.toList()

    init { require(authenticationIndex >= 0) }
}

enum class ReaderTrustState { NOT_EVALUATED, VALID_BUT_UNTRUSTED, REVOKED, TRUSTED }

data class ReaderTrustDecision(
    val state: ReaderTrustState,
    val reason: String? = null,
    val displayName: String? = null,
)

fun interface ReaderTrustEvaluator {
    suspend fun evaluate(evidence: ReaderAuthenticationEvidence): ReaderTrustDecision
}

/** Only cryptographically valid statements can carry evidence and an application trust decision. */
sealed interface ReaderAuthenticationResult {
    data object Absent : ReaderAuthenticationResult
    data class Malformed(val reason: String) : ReaderAuthenticationResult
    data class Invalid(val reason: String) : ReaderAuthenticationResult
    data class Valid(
        val evidence: ReaderAuthenticationEvidence,
        val trust: ReaderTrustDecision,
    ) : ReaderAuthenticationResult
}

class DeviceRequestReaderAuthentication(
    documents: List<ReaderAuthenticationResult>,
    wholeRequest: List<ReaderAuthenticationResult>,
) {
    private val documentResults = documents.toList()
    private val wholeRequestResults = wholeRequest.toList()
    val documents: List<ReaderAuthenticationResult> get() = documentResults.toList()
    val wholeRequest: List<ReaderAuthenticationResult> get() = wholeRequestResults.toList()
}

enum class ReaderAuthenticationDisplayValidity { ABSENT, MALFORMED, INVALID, VALID }

/** Display-safe projection; it intentionally contains no signatures, certificates, or raw evidence. */
data class ReaderAuthenticationDisplayEntry(
    val scope: ReaderAuthenticationScope,
    val authenticationIndex: Int,
    val validity: ReaderAuthenticationDisplayValidity,
    val trust: ReaderTrustState,
    val displayName: String? = null,
    val reason: String? = null,
) {
    init {
        require(authenticationIndex >= 0)
        require(validity == ReaderAuthenticationDisplayValidity.VALID || trust == ReaderTrustState.NOT_EVALUATED)
        require(displayName == null || displayName.isNotBlank())
        require(reason == null || reason.isNotBlank())
    }
}

class DeviceRequestReaderAuthenticationDisplay(
    documents: List<ReaderAuthenticationDisplayEntry>,
    wholeRequest: List<ReaderAuthenticationDisplayEntry>,
) {
    private val documentEntries = documents.toList()
    private val wholeRequestEntries = wholeRequest.toList()
    val documents: List<ReaderAuthenticationDisplayEntry> get() = documentEntries.toList()
    val wholeRequest: List<ReaderAuthenticationDisplayEntry> get() = wholeRequestEntries.toList()
}

fun DeviceRequestReaderAuthentication.toDisplaySafe(): DeviceRequestReaderAuthenticationDisplay =
    DeviceRequestReaderAuthenticationDisplay(
        documents = documents.mapIndexed { index, result ->
            result.toDisplayEntry(ReaderAuthenticationScope.Document(index), 0)
        },
        wholeRequest = wholeRequest.mapIndexed { index, result ->
            result.toDisplayEntry(ReaderAuthenticationScope.WholeRequest, index)
        },
    )

private fun ReaderAuthenticationResult.toDisplayEntry(
    scope: ReaderAuthenticationScope,
    authenticationIndex: Int,
): ReaderAuthenticationDisplayEntry = ReaderAuthenticationDisplayEntry(
    scope = scope,
    authenticationIndex = authenticationIndex,
    validity = when (this) {
        ReaderAuthenticationResult.Absent -> ReaderAuthenticationDisplayValidity.ABSENT
        is ReaderAuthenticationResult.Malformed -> ReaderAuthenticationDisplayValidity.MALFORMED
        is ReaderAuthenticationResult.Invalid -> ReaderAuthenticationDisplayValidity.INVALID
        is ReaderAuthenticationResult.Valid -> ReaderAuthenticationDisplayValidity.VALID
    },
    trust = (this as? ReaderAuthenticationResult.Valid)?.trust?.state ?: ReaderTrustState.NOT_EVALUATED,
    displayName = (this as? ReaderAuthenticationResult.Valid)?.trust?.displayName,
    reason = when (this) {
        is ReaderAuthenticationResult.Malformed -> reason
        is ReaderAuthenticationResult.Invalid -> reason
        is ReaderAuthenticationResult.Valid -> trust.reason
        ReaderAuthenticationResult.Absent -> null
    },
)

class ReaderAuthenticationVerifier(
    private val trustEvaluator: ReaderTrustEvaluator,
    allowedAlgorithms: Set<Int>,
    private val limits: MdocProximityLimits = MdocProximityLimits(),
) {
    private val allowedAlgorithms = allowedAlgorithms.toSet()
    private val cryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders())

    init {
        require(this.allowedAlgorithms.isNotEmpty()) { "At least one reader-authentication algorithm is required" }
    }

    suspend fun verify(
        request: DeviceRequest,
        transcript: SessionTranscript,
    ): DeviceRequestReaderAuthentication {
        val documents = request.docRequests.mapIndexed { index, docRequest ->
            docRequest.readerAuth?.let { signature ->
                verifyOne(
                    signature,
                    ReaderAuthenticationPayloads.forDocument(transcript, docRequest.itemsRequest),
                    ReaderAuthenticationEvidence(
                        scope = ReaderAuthenticationScope.Document(index),
                    ),
                )
            } ?: ReaderAuthenticationResult.Absent
        }
        val whole = request.readerAuthAll.orEmpty().mapIndexed { index, signature ->
            verifyOne(
                signature,
                ReaderAuthenticationPayloads.forAllDocuments(
                    transcript,
                    request.docRequests.map { it.itemsRequest },
                    request.deviceRequestInfo,
                ),
                ReaderAuthenticationEvidence(
                    scope = ReaderAuthenticationScope.WholeRequest,
                    authenticationIndex = index,
                ),
            )
        }
        return DeviceRequestReaderAuthentication(documents, whole)
    }

    private suspend fun verifyOne(
        signature: CoseSign1,
        detachedPayload: ByteArray,
        evidence: ReaderAuthenticationEvidence,
    ): ReaderAuthenticationResult {
        val chain = try {
            certificateChain(signature)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (cause: Exception) {
            return ReaderAuthenticationResult.Malformed(cause.message ?: "Malformed reader authentication")
        }
        val chainBytes = chain.sumOf { it.size.toLong() }
        if (
            chain.size > limits.maximumReaderCertificateChainLength ||
            chainBytes > limits.maximumReaderCertificateBytes.toLong()
        ) {
            return ReaderAuthenticationResult.Malformed("Reader certificate chain exceeds configured limits")
        }
        val valid = try {
            val certificates = chain.map { X509CertificateUtil.parseCertificateDerEncoded(ByteString(it)) }
            // Authentication validity proves the request signature with the leaf key. Certification-path,
            // revocation, ecosystem roots, and authorization remain exclusively trust-evaluator concerns.
            signature.verifyDetached(
                key = certificates.first().restoreSubjectPublicKey(cryptoRuntime),
                detachedPayload = detachedPayload,
                allowedAlgorithms = allowedAlgorithms,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        if (!valid) return ReaderAuthenticationResult.Invalid("Reader authentication signature is invalid")
        val verifiedEvidence = ReaderAuthenticationEvidence(evidence.scope, evidence.authenticationIndex, chain.map { ImmutableBytes.of(it) })
        val trust = try {
            trustEvaluator.evaluate(verifiedEvidence)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ReaderTrustDecision(ReaderTrustState.VALID_BUT_UNTRUSTED, "Reader trust evaluation is unavailable")
        }
        return ReaderAuthenticationResult.Valid(verifiedEvidence, trust)
    }

    private fun certificateChain(signature: CoseSign1): List<ByteArray> {
        require(signature.payload == null) { "Reader authentication must use a detached payload" }
        val protected = if (signature.protected.isEmpty()) CoseHeaders()
            else coseCompliantCbor.decodeFromByteArray(CoseHeaders.serializer(), signature.protected)
        val protectedChain = protected.x5chain
        val unprotectedChain = signature.unprotected.x5chain
        require(protectedChain == null || unprotectedChain == null) {
            "Reader authentication x5chain cannot appear in both protected and unprotected headers"
        }
        val chain = unprotectedChain ?: protectedChain
            ?: throw IllegalArgumentException("Reader authentication has no x5chain")
        require(chain.isNotEmpty()) { "Reader authentication x5chain is empty" }
        return chain.map { it.rawBytes.copyOf() }
    }
}
