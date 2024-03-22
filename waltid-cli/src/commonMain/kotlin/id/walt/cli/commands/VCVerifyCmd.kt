package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.VCUtil
import kotlinx.coroutines.runBlocking
import java.io.File

class VCVerifyCmd : CliktCommand(
    name = "verify",
    help = "Verifies a Verifiable Credential under a set of policies.",
    printHelpOnEmptyArgs = true
) {
    val print: PrettyPrinter = PrettyPrinter(this)

    // private val keyFile by option("-k", "--key")
    //     // .help("The Subject's key to be used. If none is provided, a new one will be generated.")
    //     .help("A core-crypto key representation to sign the credential (required)")
    //     .file()
    // .required()

    private val vc: File by argument(help = "the verifiable credential file (in the JWS format) to be verified (required)").file()

    override fun run() {

        // val key = runBlocking { KeyUtil().getKey(keyFile) }

        val jws = vc.readText()

        val result = runBlocking { VCUtil.verify(jws) }

        if (result.isSuccess) {
            print.green("Success! ", false)
            print.plain("The VC signature is valid.")
        } else {
            print.red("Fail! ", false)
            print.plain("VC signature is not valid.")
        }
    }
}
