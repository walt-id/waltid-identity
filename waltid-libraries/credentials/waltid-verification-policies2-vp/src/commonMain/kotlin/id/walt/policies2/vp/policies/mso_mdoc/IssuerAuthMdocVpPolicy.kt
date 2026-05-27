@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.cose.toCoseVerifier
import id.walt.crypto.keys.jwk.JWKKey.Companion.convertDerCertificateToPemCertificate
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val policyId = "mso_mdoc/issuer_auth"

@Serializable
@SerialName(policyId)
class IssuerAuthMdocVpPolicy : MdocVPPolicy() {

    override val id = policyId
    override val description = "Verify issuer authentication"

    companion object {
        private val log = KotlinLogging.logger { }
    }

    override suspend fun VPPolicyRunContext.verifyMdocPolicy(
        document: Document,
        mso: MobileSecurityObject,
        verificationContext: VerificationSessionContext?
    ): Result<Unit> = coroutineScope {
        log.trace { "--- Verifying issuer authentication ---" }

        // Use getParsedIssuerAuth() which checks unprotected header first, then falls back to
        // the protected header per ISO 18013-5 §9.1.2.4 ("readers SHOULD support protected header").
        val parsedIssuerAuth = document.issuerSigned.getParsedIssuerAuth()

        addResult("certificate_chain", parsedIssuerAuth.x5c)

        val issuerKey = parsedIssuerAuth.signerKey
        log.trace { "Signer key to be used: $issuerKey" }
        addOptionalJsonResult("signer_jwk") { issuerKey.exportJWKObject() }

        val firstCertDer = parsedIssuerAuth.x5c.first().decodeFromBase64()
        addOptionalResult("signer_pem") { convertDerCertificateToPemCertificate(firstCertDer) }

        log.trace { "Verifying issuer auth signature with signer key..." }
        val issuerAuth = document.issuerSigned.issuerAuth
        if (!issuerAuth.verify(issuerKey.toCoseVerifier())) {
            throw IllegalArgumentException("IssuerAuth COSE_Sign1 signature is invalid.")
        }

        // Verify the certificate chain when more than one cert is present.
        if (parsedIssuerAuth.x5c.size > 1) {
            val chainBytes = parsedIssuerAuth.x5c.map { it.decodeFromBase64() }
            X5CChainValidator.verifyChain(chainBytes)
            addResult("chain_length", parsedIssuerAuth.x5c.size.toString())
            addResult("chain_verified", true.toString())
        }

        success()
    }
}
