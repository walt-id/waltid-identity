package id.walt.openid4vp.verifier.verification

import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.presentations.formats.MsoMdocPresentation
import id.walt.credentials.representations.X5CCertificateString
import id.walt.credentials.representations.X5CList
import id.walt.credentials.signatures.CoseCredentialSignature
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.mdoc.verification.MdocVerifier
import id.walt.mdoc.verification.VerificationContext
import id.walt.openid4vp.verifier.verification.Verifier2PresentationValidator.PresentationValidationResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.time.ExperimentalTime

object MdocPresentationValidator {

    private val log = KotlinLogging.logger("MdocPresentationValidator")

    @OptIn(ExperimentalTime::class, ExperimentalSerializationApi::class)
    suspend fun validateMsoMdocPresentation(
        mdocBase64UrlString: String,
        // These three (expectedNonce, expectedAudience, responseUri) are required to reconstruct the SessionTranscript
        expectedNonce: String,
        expectedAudience: String, // This is the client_id
        responseUri: String?
    ): Result<PresentationValidationResult> = runCatching {
        requireNotNull(responseUri) { "Response uri is required for mdoc presentation validation" }

        val verificationContext = VerificationContext(expectedNonce, expectedAudience, responseUri)
        log.trace { "Validating Mdoc presentation, with verification context: $verificationContext" }

        val verificationResult = MdocVerifier.verify(mdocBase64UrlString, verificationContext)

        require(verificationResult.valid) { "Mdoc verification failed: ${verificationResult.errors}" }

        val docType = verificationResult.docType

        // TODO: can reuse some functionality from Mdoc parser?
        val signerKey = verificationResult.signerKey?.key ?: throw IllegalArgumentException("Missing signer key")
        val x5CList = X5CList(
            (verificationResult.x5c ?: throw IllegalArgumentException("Missing x5c"))
                .map { X5CCertificateString(it) })

        //val issuerVirtualDid = LocalRegistrar().createByKey(signerKey, DidJwkCreateOptions()).did

        val mdocsCredential = MdocsCredential(
            credentialData = verificationResult.credentialData,
            signed = mdocBase64UrlString,
            docType = docType,
            issuer = null, //issuerVirtualDid,
            signature = CoseCredentialSignature(
                signerKey = DirectSerializedKey(signerKey),
                x5cList = x5CList
            )
        )

        PresentationValidationResult(
            presentation = MsoMdocPresentation(mdocsCredential),
            credentials = listOf(mdocsCredential)
        )
    }

}
