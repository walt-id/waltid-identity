@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.cose.toCoseVerifier
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.jwk.JWKKey.Companion.convertDerCertificateToPemCertificate
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
        verificationContext: VerificationSessionContext
    ): Result<Unit> = coroutineScope {
        log.trace { "--- Verifying issuer authentication ---" }
        val issuerAuth = document.issuerSigned.issuerAuth
        val x5c = issuerAuth.unprotected.x5chain
        addResult("certificate_chain", x5c?.map { it.rawBytes.toHexString() } ?: emptyList<String>())
        requireNotNull(x5c) { "Missing certificate chain in mdocs credential" }

        val signerCertificateBytes = x5c.firstOrNull()?.rawBytes
            ?: throw IllegalArgumentException("Missing signer certificate in x5chain.")

        addOptionalResult("signer_pem") { convertDerCertificateToPemCertificate(signerCertificateBytes) }

        val issuerKey = JWKKey.importFromDerCertificate(signerCertificateBytes).getOrThrow()
        log.trace { "Signer key to be used: $issuerKey" }
        addOptionalJsonResult("signer_jwk") { issuerKey.exportJWKObject() }

        log.trace { "Verifying issuer auth signature with signer key..." }
        if (!issuerAuth.verify(issuerKey.toCoseVerifier())) {
            throw IllegalArgumentException("IssuerAuth COSE_Sign1 signature is invalid.")
        }

        success()
    }
}
