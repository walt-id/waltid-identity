package id.walt.rpcert.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.rpcert.cli.util.DemoCertificateAuthority
import id.walt.rpcert.issuance.RelyingPartyRegistrationCertificateIssuer
import id.walt.rpcert.models.RelyingPartyRegistrationCertificate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.Base64

class IssueJwtCommand : CliktCommand(name = "issue-jwt") {
    override fun help(context: Context) =
        "Issue and sign a Wallet-Relying Party Registration Certificate (rc-wrp+jwt), printed to stdout."

    private val payloadFile by option(
        "--payload",
        help = "Path to a JSON file with the RelyingPartyRegistrationCertificate payload",
    ).file(mustExist = true, canBeDir = false).required()

    private val keyFile by option(
        "--key",
        help = "Path to a JWK signing key file (use together with --x5c)",
    ).file(mustExist = true, canBeDir = false)

    private val x5cFiles by option(
        "--x5c",
        help = "Path to a DER certificate file for the x5c chain, leaf first (repeatable, use together with --key)",
    ).file(mustExist = true, canBeDir = false).multiple()

    private val generateDemoCa by option(
        "--generate-demo-ca",
        help = "Generate an ephemeral self-signed CA and signing key instead of --key/--x5c",
    ).flag(default = false)

    private val json = Json { ignoreUnknownKeys = true }

    override fun run() = runBlocking {
        val payload = json.decodeFromString<RelyingPartyRegistrationCertificate>(payloadFile.readText())

        val useRealKeyMaterial = keyFile != null || x5cFiles.isNotEmpty()
        if (useRealKeyMaterial == generateDemoCa) {
            throw CliktError("Specify either --key together with --x5c, or --generate-demo-ca (not both)")
        }

        val (signingKey, x5c) = if (generateDemoCa) {
            DemoCertificateAuthority.generate().let { chain -> chain.signingKey to chain.x5c }
        } else {
            val key = keyFile ?: throw CliktError("--x5c requires --key")
            if (x5cFiles.isEmpty()) throw CliktError("--key requires at least one --x5c")
            val signingKey: Key = JWKKey.importJWK(key.readText()).getOrElse {
                throw CliktError("Could not import --key as a JWK: ${it.message}")
            }
            val x5c = x5cFiles.map { Base64.getEncoder().encodeToString(it.readBytes()) }
            signingKey to x5c
        }

        val jwt = RelyingPartyRegistrationCertificateIssuer.issue(signingKey, x5c, payload)
        echo(jwt)
    }
}
