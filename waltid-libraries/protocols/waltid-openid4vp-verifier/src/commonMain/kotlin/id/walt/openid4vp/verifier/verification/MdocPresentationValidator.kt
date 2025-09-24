package id.walt.openid4vp.verifier.verification

import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.presentations.formats.MsoMdocPresentation
import id.walt.did.dids.registrar.LocalRegistrar
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
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

        val verificationResult = MdocVerifier.verify(mdocBase64UrlString, verificationContext)

        require(verificationResult.valid) { "Mdoc verification failed: ${verificationResult.errors}" }

        val docType = verificationResult.docType
        val issuerVirtualDid = LocalRegistrar().createByKey(verificationResult.issuerKey.key, DidJwkCreateOptions()).did

        val mdocsCredential = MdocsCredential(
            credentialData = verificationResult.credentialData,
            signed = mdocBase64UrlString,
            docType = docType,
            issuer = issuerVirtualDid
        )

        PresentationValidationResult(
            presentation = MsoMdocPresentation(mdocsCredential),
            credentials = listOf(mdocsCredential)
        )
    }

}
