package id.walt.policies2.vc.policies.status.validator

import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.vc.policies.status.CredentialFetcher
import id.walt.policies2.vc.policies.status.StatusListContent
import id.walt.policies2.vc.policies.status.model.StatusContent
import id.walt.policies2.vc.policies.status.model.StatusPolicyAttribute
import id.walt.policies2.vc.policies.status.model.StatusRetrievalError
import id.walt.policies2.vc.policies.status.model.StatusVerificationError
import id.walt.policies2.vc.policies.status.model.IETFEntry
import id.walt.policies2.vc.policies.status.reader.StatusValueReader
import id.walt.policies2.vc.policies.status.signature.StatusListSignatureVerifier
import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizationRequest
import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizer
import id.walt.policies2.vc.policies.status.signature.VerifiedStatusList
import id.walt.policies2.vc.policies.status.signature.authorizeStatusListSigner
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

abstract class StatusValidatorBase<K : StatusContent, M : id.walt.policies2.vc.policies.status.model.StatusEntry, T : StatusPolicyAttribute>(
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
    ): Result<Unit> = runCatching {
        logger.debug { "Credential URL: ${entry.uri}" }
        val statusListContent = fetcher.fetch(entry.uri)
            .getOrElse { throw StatusRetrievalError(it.message ?: "Status credential download error") }
        
        signatureVerifier?.let { verifier ->
            requireNotNull(referencedCredential) { "Referenced credential is required for status-list signer authorization" }
            val verified = verifySignature(verifier, statusListContent)
            authorizeStatusListSigner(
                StatusListSignerAuthorizationRequest(referencedCredential, entry.uri, verified.signer),
                signerAuthorizer,
            )
            if (entry is IETFEntry && verified.payload is JsonObject) {
                require(verified.payload["sub"]?.jsonPrimitive?.contentOrNull == entry.uri) {
                    "IETF status-list token subject does not match referenced status-list URI"
                }
            }
        }
        
        val matchingReader = reader.firstOrNull { it.canHandle(statusListContent) }
        requireNotNull(matchingReader) { "No available reader to handle the status list content." }
        val statusList = matchingReader.read(statusListContent)
            .getOrElse { throw StatusRetrievalError(it.message ?: "Status credential parsing error") }
        val bitValue = getBitValue(statusList, entry)
        logger.debug { "EncodedList[${entry.index}] = $bitValue" }
        customValidations(statusList, attribute)
        statusValidations(bitValue, attribute)
    }
    
    private suspend fun verifySignature(
        verifier: StatusListSignatureVerifier,
        content: StatusListContent,
    ): VerifiedStatusList<*> {
        logger.debug { "Verifying status list signature" }
        
        val verified = when (content) {
            is StatusListContent.Text -> {
                if (isJwt(content.content)) {
                    verifier.verifyJwtWithSigner(content.content).getOrElse {
                        throw StatusVerificationError("Status list JWT signature verification failed: ${it.message}")
                    }
                } else {
                    throw StatusVerificationError("Unsupported unsigned status-list text format")
                }
            }
            is StatusListContent.Binary -> {
                verifier.verifyCwtWithSigner(content.content).getOrElse {
                    throw StatusVerificationError("Status list CWT signature verification failed: ${it.message}")
                }
            }
        }
        
        logger.debug { "Status list signature verified successfully" }
        return verified
    }
    
    private fun isJwt(content: String): Boolean {
        return content.startsWith("ey") && content.count { it == '.' } == 2
    }

    protected abstract suspend fun getBitValue(statusList: K, entry: M): List<Char>

    protected abstract fun customValidations(statusList: K, attribute: T)

    private fun statusValidations(bitValue: List<Char>, attribute: T) {
        if (bitValue.isEmpty()) {
            throw StatusVerificationError("Null or empty bit value")
        }
        if (!isBinaryValue(bitValue)) {
            throw StatusVerificationError("Invalid bit value: $bitValue")
        }
        val binaryString = bitValue.joinToString("")
        val intValue = binToInt(binaryString)
        val allowedValues = attribute.getAllowedValues()
        if (intValue.toUInt() !in allowedValues) {
            throw StatusVerificationError("Status validation failed: expected one of $allowedValues, but got $intValue")
        }
    }

    private fun isBinaryValue(value: List<Char>) = setOf('0', '1').let { valid ->
        value.all { it in valid }
    }

    private fun binToInt(bin: String) = bin.toInt(2)
}
