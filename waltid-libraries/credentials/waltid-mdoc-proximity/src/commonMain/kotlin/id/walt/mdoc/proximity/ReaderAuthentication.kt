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

enum class ReaderAuthenticationScope { DOCUMENT, WHOLE_REQUEST }

data class ReaderAuthenticationEvidence(
    val scope: ReaderAuthenticationScope,
    val documentRequestIndex: Int? = null,
    /** Zero-based statement index within the authentication scope. */
    val authenticationIndex: Int = 0,
    val certificateChainDer: List<ImmutableBytes> = emptyList(),
) {
    init {
        require((scope == ReaderAuthenticationScope.DOCUMENT) == (documentRequestIndex != null))
        require(documentRequestIndex == null || documentRequestIndex >= 0)
        require(authenticationIndex >= 0)
    }
}

sealed interface ReaderAuthenticationValidity {
    data object Absent : ReaderAuthenticationValidity
    data class Malformed(val reason: String) : ReaderAuthenticationValidity
    data class Invalid(val reason: String) : ReaderAuthenticationValidity
    data class Valid(val evidence: ReaderAuthenticationEvidence) : ReaderAuthenticationValidity
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

data class ReaderAuthenticationResult(
    val validity: ReaderAuthenticationValidity,
    val trust: ReaderTrustDecision = ReaderTrustDecision(ReaderTrustState.NOT_EVALUATED),
) {
    init {
        require(validity is ReaderAuthenticationValidity.Valid || trust.state == ReaderTrustState.NOT_EVALUATED) {
            "Trust cannot be evaluated before reader authentication is cryptographically valid"
        }
    }
}

data class DeviceRequestReaderAuthentication(
    val documents: List<ReaderAuthenticationResult>,
    val wholeRequest: List<ReaderAuthenticationResult>,
)

enum class ReaderAuthenticationDisplayValidity { ABSENT, MALFORMED, INVALID, VALID }

/** Display-safe projection; it intentionally contains no signatures, certificates, or raw evidence. */
data class ReaderAuthenticationDisplayEntry(
    val scope: ReaderAuthenticationScope,
    val documentRequestIndex: Int?,
    val authenticationIndex: Int,
    val validity: ReaderAuthenticationDisplayValidity,
    val trust: ReaderTrustState,
    val displayName: String? = null,
    val reason: String? = null,
) {
    init {
        require((scope == ReaderAuthenticationScope.DOCUMENT) == (documentRequestIndex != null))
        require(documentRequestIndex == null || documentRequestIndex >= 0)
        require(authenticationIndex >= 0)
        require(displayName == null || displayName.isNotBlank())
        require(reason == null || reason.isNotBlank())
    }
}

data class DeviceRequestReaderAuthenticationDisplay(
    val documents: List<ReaderAuthenticationDisplayEntry>,
    val wholeRequest: List<ReaderAuthenticationDisplayEntry>,
)

fun DeviceRequestReaderAuthentication.toDisplaySafe(): DeviceRequestReaderAuthenticationDisplay =
    DeviceRequestReaderAuthenticationDisplay(
        documents = documents.mapIndexed { index, result ->
            result.toDisplayEntry(ReaderAuthenticationScope.DOCUMENT, index, 0)
        },
        wholeRequest = wholeRequest.mapIndexed { index, result ->
            result.toDisplayEntry(ReaderAuthenticationScope.WHOLE_REQUEST, null, index)
        },
    )

private fun ReaderAuthenticationResult.toDisplayEntry(
    scope: ReaderAuthenticationScope,
    documentRequestIndex: Int?,
    authenticationIndex: Int,
): ReaderAuthenticationDisplayEntry = ReaderAuthenticationDisplayEntry(
    scope = scope,
    documentRequestIndex = documentRequestIndex,
    authenticationIndex = authenticationIndex,
    validity = when (validity) {
        ReaderAuthenticationValidity.Absent -> ReaderAuthenticationDisplayValidity.ABSENT
        is ReaderAuthenticationValidity.Malformed -> ReaderAuthenticationDisplayValidity.MALFORMED
        is ReaderAuthenticationValidity.Invalid -> ReaderAuthenticationDisplayValidity.INVALID
        is ReaderAuthenticationValidity.Valid -> ReaderAuthenticationDisplayValidity.VALID
    },
    trust = trust.state,
    displayName = trust.displayName,
    reason = when (val value = validity) {
        is ReaderAuthenticationValidity.Malformed -> value.reason
        is ReaderAuthenticationValidity.Invalid -> value.reason
        ReaderAuthenticationValidity.Absent, is ReaderAuthenticationValidity.Valid -> trust.reason
    },
)

class ReaderAuthenticationVerifier(
    private val trustEvaluator: ReaderTrustEvaluator,
    private val allowedAlgorithms: Set<Int>,
    private val limits: MdocProximityLimits = MdocProximityLimits(),
) {
    private val cryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders())

    init {
        require(allowedAlgorithms.isNotEmpty()) { "At least one reader-authentication algorithm is required" }
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
                        scope = ReaderAuthenticationScope.DOCUMENT,
                        documentRequestIndex = index,
                    ),
                )
            } ?: ReaderAuthenticationResult(ReaderAuthenticationValidity.Absent)
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
                    scope = ReaderAuthenticationScope.WHOLE_REQUEST,
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
            return ReaderAuthenticationResult(ReaderAuthenticationValidity.Malformed(cause.message ?: "Malformed reader authentication"))
        }
        val chainBytes = chain.sumOf { it.size.toLong() }
        if (
            chain.size > limits.maximumReaderCertificateChainLength ||
            chainBytes > limits.maximumReaderCertificateBytes.toLong()
        ) {
            return ReaderAuthenticationResult(ReaderAuthenticationValidity.Malformed("Reader certificate chain exceeds configured limits"))
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
        if (!valid) return ReaderAuthenticationResult(ReaderAuthenticationValidity.Invalid("Reader authentication signature is invalid"))
        val verifiedEvidence = evidence.copy(certificateChainDer = chain.map { ImmutableBytes.of(it) })
        val trust = try {
            trustEvaluator.evaluate(verifiedEvidence)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ReaderTrustDecision(ReaderTrustState.VALID_BUT_UNTRUSTED, "Reader trust evaluation is unavailable")
        }
        return ReaderAuthenticationResult(ReaderAuthenticationValidity.Valid(verifiedEvidence), trust)
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
