package id.walt.policies2.vc.policies.status.validator

import id.walt.credentials.status.EvaluatedCredentialStatus
import id.walt.credentials.status.certificateDeadline
import id.walt.credentials.status.CredentialStatusException
import id.walt.credentials.status.authenticateStatusList
import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.vc.policies.status.CredentialFetcher
import id.walt.policies2.vc.policies.status.StatusListContent
import id.walt.policies2.vc.policies.status.runStatusCheck
import id.walt.policies2.vc.policies.status.model.StatusContent
import id.walt.policies2.vc.policies.status.model.StatusPolicyAttribute
import id.walt.policies2.vc.policies.status.model.StatusRetrievalError
import id.walt.policies2.vc.policies.status.model.StatusVerificationError
import id.walt.policies2.vc.policies.status.model.IETFEntry
import id.walt.policies2.vc.policies.status.reader.StatusValueReader
import id.walt.policies2.vc.policies.status.signature.StatusListSignatureVerifier
import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Clock
import id.walt.credentials.status.StatusProtocol
import id.walt.policies2.vc.policies.status.model.StatusEntry
import kotlin.time.Instant

abstract class StatusValidatorBase<K : StatusContent, M : StatusEntry, T : StatusPolicyAttribute>(
    private val fetcher: CredentialFetcher,
    private vararg val reader: StatusValueReader<K>,
    private val signatureVerifier: StatusListSignatureVerifier? = null,
) : StatusValidator<M, T> {
    protected val logger = KotlinLogging.logger {}

    override suspend fun validate(entry: M, attribute: T): Result<Unit> =
        validate(entry, attribute, null, null)

    suspend fun validate(
        entry: M,
        attribute: T,
        referencedCredential: DigitalCredential?,
        signerAuthorizer: StatusListSignerAuthorizer?,
    ): Result<Unit> = evaluate(entry, attribute, referencedCredential, signerAuthorizer).fold(
        onFailure = { Result.failure(if (it is CredentialStatusException) it.cause ?: it else it) },
        onSuccess = { evaluated -> runStatusCheck {
        val value = evaluated.value
        val allowedValues = attribute.getAllowedValues()
        if (value !in allowedValues) {
            throw StatusVerificationError("Status validation failed: expected one of $allowedValues, but got $value")
        }
    } })

    /** Authenticated interpretation, independent of the verifier's allowed-value policy. */
    suspend fun evaluate(
        entry: M,
        attribute: T,
        referencedCredential: DigitalCredential?,
        signerAuthorizer: StatusListSignerAuthorizer?,
        suppliedContent: StatusListContent? = null,
        nowEpochSeconds: Long = Clock.System.now().epochSeconds,
        profile: StatusProtocol? = null,
    ): Result<EvaluatedCredentialStatus> = runStatusCheck {
        val statusListContent = suppliedContent ?: fetcher.fetch(entry.uri)
            .getOrElse { throw StatusRetrievalError(it.message ?: "Status credential download error") }

        val verified = signatureVerifier?.let { verifier ->
            requireNotNull(referencedCredential) { "Referenced credential is required for status-list signer authorization" }
            authenticateStatusList(verifier, statusListContent, referencedCredential, entry, signerAuthorizer, nowEpochSeconds, profile)
        }

        val matchingReader = reader.firstOrNull { it.canHandle(statusListContent) }
        requireNotNull(matchingReader) { "No available reader to handle the status list content." }
        val statusList = matchingReader.read(statusListContent)
            .getOrElse {
                if (it is CredentialStatusException) throw it
                throw StatusRetrievalError(it.message ?: "Status credential parsing error")
            }
        val bitValue = getBitValue(statusList, entry)
        customValidations(statusList, attribute)
        if (bitValue.isEmpty()) throw StatusVerificationError("Null or empty bit value")
        if (bitValue.any { it != '0' && it != '1' }) throw StatusVerificationError("Invalid bit value: $bitValue")
        EvaluatedCredentialStatus(bitValue.joinToString("").toUInt(2),
            verified?.let { certificateDeadline(it.signer.certificateChain, Instant.fromEpochSeconds(nowEpochSeconds)) })
    }

    protected abstract suspend fun getBitValue(statusList: K, entry: M): List<Char>

    protected abstract fun customValidations(statusList: K, attribute: T)

}
