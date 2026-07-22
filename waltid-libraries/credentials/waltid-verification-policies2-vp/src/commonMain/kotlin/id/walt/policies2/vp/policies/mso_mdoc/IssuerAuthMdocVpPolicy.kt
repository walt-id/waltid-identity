@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.crypto.keys.jwk.JWKKey.Companion.convertDerCertificateToPemCertificate
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.toPublicJwk
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.verification.verifyIssuerAuthentication
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.time.Clock

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

        val verification = verifyIssuerAuthentication(
            document,
            verificationContext?.verificationTime ?: Clock.System.now(),
        )
        val encodedChain = verification.certificateChain.map { Base64.Default.encode(it.bytes.toByteArray()) }
        addResult("certificate_chain", encodedChain)

        val issuerKey = verification.signerKey
        log.trace { "Signer key to be used: $issuerKey" }
        addOptionalJsonResult("signer_jwk") {
            val publicKey = requireNotNull(issuerKey.capabilities.publicKeyExporter).exportPublicKey()
            Jwk.parse(publicKey.toPublicJwk(issuerKey.spec))
        }

        val firstCertDer = verification.certificateChain.first().bytes.toByteArray()
        addOptionalResult("signer_pem") { convertDerCertificateToPemCertificate(firstCertDer) }
        addResult("cose_algorithm", verification.coseAlgorithm)
        // Trust anchors are enforced separately by VICAL or trusted-list policies.
        if (verification.certificateChain.size > 1) {
            addResult("chain_length", verification.certificateChain.size.toString())
        }
        addResult("chain_constraints_verified", true.toString())

        success()
    }
}
