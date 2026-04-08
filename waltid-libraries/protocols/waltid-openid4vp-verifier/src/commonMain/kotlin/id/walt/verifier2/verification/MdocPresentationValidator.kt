package id.walt.verifier2.verification

import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.presentations.formats.MsoMdocPresentation
import id.walt.credentials.representations.X5CCertificateString
import id.walt.credentials.representations.X5CList
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.mdoc.parser.MdocParser
import id.walt.mdoc.verification.MdocVerificationContext
import id.walt.mdoc.verification.MdocVerifier
import id.walt.verifier.openid.TransactionDataUtils
import id.walt.verifier2.verification.Verifier2PresentationValidator.PresentationValidationResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi

@Deprecated(
    message = "Use PresentationVerificationEngine and VP policies for verifier flows. This validator remains as a compatibility shim.",
)
object MdocPresentationValidator {

    private val log = KotlinLogging.logger("MdocPresentationValidator")

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun validateMsoMdocPresentation(
        mdocBase64UrlString: String,
        expectedNonce: String,
        expectedAudience: String?,
        responseUri: String?,
        isDcApi: Boolean = false,
        isEncrypted: Boolean = false,
        jwkThumbprint: String? = null,
        expectedTransactionData: List<String>? = null,
    ): Result<PresentationValidationResult> = runCatching {
        if (!isDcApi) {
            requireNotNull(responseUri) { "Response uri is required for redirect-based mdoc validation" }
        }

        val verificationContext = MdocVerificationContext(
            expectedNonce = expectedNonce,
            expectedAudience = expectedAudience,
            responseUri = responseUri,
            isDcApi = isDcApi,
            isEncrypted = isEncrypted,
            jwkThumbprint = jwkThumbprint,
        )
        log.trace { "Validating Mdoc presentation, with verification context: $verificationContext" }

        val document = MdocParser.parseToDocument(mdocBase64UrlString)
        val sessionTranscript = MdocVerifier.buildSessionTranscriptForContext(verificationContext)
        val verificationResult = MdocVerifier.verify(document, sessionTranscript)

        require(verificationResult.valid) { "Mdoc verification failed: ${verificationResult.errors}" }
        validateTransactionData(document, expectedTransactionData)

        val signerKey = verificationResult.signerKey?.key ?: throw IllegalArgumentException("Missing signer key")
        val x5CList = X5CList(
            (verificationResult.x5c ?: throw IllegalArgumentException("Missing x5c"))
                .map(::X5CCertificateString),
        )

        val mdocsCredential = MdocsCredential(
            credentialData = verificationResult.credentialData,
            signed = mdocBase64UrlString,
            docType = verificationResult.docType,
            issuer = null,
            signature = CoseCredentialSignature(
                signerKey = DirectSerializedKey(signerKey),
                x5cList = x5CList,
            ),
        )

        PresentationValidationResult(
            presentation = MsoMdocPresentation(mdocsCredential),
            credentials = listOf(mdocsCredential),
        )
    }

    private fun validateTransactionData(
        document: id.walt.mdoc.objects.document.Document,
        expectedTransactionData: List<String>?,
    ) {
        val embeddedTransactionData = TransactionDataUtils.extractMdocEmbeddedTransactionData(
            deviceSignedItems = document.deviceSigned
                ?.namespaces
                ?.value
                ?.entries
                ?.get(TransactionDataUtils.MDOC_DEVICE_SIGNED_NAMESPACE)
                ?.entries
                ?.associate { it.key to it.value }
                .orEmpty(),
        )

        val expectedItems = expectedTransactionData.orEmpty()
        if (expectedItems.isEmpty()) {
            require(embeddedTransactionData.isEmpty()) {
                "mdoc transaction_data entries must be omitted when transaction_data is not requested"
            }
            return
        }

        val algorithm = TransactionDataUtils.resolveHashAlgorithm(
            TransactionDataUtils.decodeTransactionDataList(expectedItems),
        ) ?: TransactionDataUtils.DEFAULT_HASH_ALGORITHM
        val expectedHashes = TransactionDataUtils.calculateTransactionDataHashes(expectedItems, algorithm)
        val embeddedHashes = TransactionDataUtils.calculateTransactionDataHashes(embeddedTransactionData, algorithm)

        require(embeddedHashes == expectedHashes) {
            "mdoc transaction_data does not match the requested transaction_data"
        }
    }
}
