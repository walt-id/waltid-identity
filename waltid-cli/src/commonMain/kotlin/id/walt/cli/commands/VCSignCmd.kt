package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import id.walt.cli.util.PrettyPrinter

class VCSignCmd : CliktCommand(
    name = "sign",
    help = "Signs a Verifiable Credential.",
    printHelpOnEmptyArgs = true
) {
    val print: PrettyPrinter = PrettyPrinter(this)

    // -k, —key
    // -i, —issuerDid<str>
    // -s, —subjectDid=<str>
    // -vc, —verifiableCredential=<filepath>


    private val keyFile by option("-k", "--key")
        // .help("The Subject's key to be used. If none is provided, a new one will be generated.")
        .help("A core-crypto key representation to sign the credential")
        .file()

    private val issuerDid by option("-i", "--issuer")
        .help("The verifiable credential's issuer DID")

    private val subjectDid by option("-i", "--subject")
        .help("The verifiable credential's subject DID")
        .required()

    override fun run() = Unit
}