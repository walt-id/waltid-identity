package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import id.walt.cli.util.DidMethod
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

    private val method by option("-m", "--method")
        .help("The DID method to be used.")
        .enum<DidMethod>(ignoreCase = true)
        // .choice(DidMethod.KEY.name, DidMethod.JWK.name)
        .default(DidMethod.KEY)

    private val keyFile by option("-k", "--key")
        .help("The Subject's key to be used. If none is provided, a new one will be generated.")
        .file()

    override fun run() = Unit
}