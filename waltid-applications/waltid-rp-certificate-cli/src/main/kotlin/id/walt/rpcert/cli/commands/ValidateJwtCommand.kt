package id.walt.rpcert.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import id.walt.rpcert.cli.util.AuthorizationRequestFetcher
import id.walt.rpcert.cli.util.NoRegistrationCertificateFoundException
import id.walt.rpcert.cli.util.ValidationRunner
import id.walt.rpcert.cli.util.resolveInlineOrFile
import id.walt.x509.CertificateDer
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

class ValidateJwtCommand : CliktCommand(name = "validate-jwt") {
    override fun help(context: Context) =
        "Validate an OpenID4VP Authorization Request against the Wallet-Relying Party Registration " +
            "Certificate found in its verifier_info; prints true/false and exits 0/1 accordingly."

    private val request by option(
        "--request",
        help = "openid4vp://authorize?... Authorization Request URL. Prefix with @ to read it from a file.",
    ).required()

    private val trustAnchorFiles by option(
        "--trust-anchor",
        help = "DER-encoded trust anchor certificate the registration certificate's x5c chain must chain up to (repeatable)",
    ).file(mustExist = true, canBeDir = false).multiple()

    private val allowSelfSigned by option(
        "--allow-self-signed",
        help = "Accept a self-signed root certificate within the x5c chain as trust anchor (insecure, local testing only)",
    ).flag(default = false)

    override fun run() = runBlocking {
        val requestUrl = resolveInlineOrFile(request)
        val trustAnchors = trustAnchorFiles.map { CertificateDer(it.readBytes()) }

        val authorizationRequest = try {
            AuthorizationRequestFetcher.resolve(requestUrl)
        } catch (e: Exception) {
            throw CliktError("Could not resolve Authorization Request: ${e.message}")
        }

        val outcome = try {
            ValidationRunner.run(authorizationRequest, trustAnchors, allowSelfSigned)
        } catch (e: NoRegistrationCertificateFoundException) {
            throw CliktError(e.message ?: "No registration certificate found")
        }

        echo(outcome.reasoning)
        echo(outcome.allowed.toString())

        if (!outcome.allowed) exitProcess(1)
    }
}
