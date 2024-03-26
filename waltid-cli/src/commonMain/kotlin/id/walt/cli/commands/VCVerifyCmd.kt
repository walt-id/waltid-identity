package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.VCUtil
import kotlinx.coroutines.runBlocking
import java.io.File

class VCVerifyCmd : CliktCommand(
    name = "verify",
    help = "Verifies the signature of a Verifiable Credential. Future plans to add new policies possibilities.",
    printHelpOnEmptyArgs = true
) {
    val print: PrettyPrinter = PrettyPrinter(this)

    // private val keyFile by option("-k", "--key")
    //     // .help("The Subject's key to be used. If none is provided, a new one will be generated.")
    //     .help("A core-crypto key representation to sign the credential (required)")
    //     .file()
    // .required()

    private val vc: File by argument(help = "the verifiable credential file (in the JWS format) to be verified (required)").file()

    // val policies: Map<String, String?> by option(
    val policies: List<String> by option(
        "-p",
        "--policy",
        help = """Specify a policy to be applied in the verification process.
                  Multiple policies are accepted. 
                  If no policy is specified, only the Signature Policy will be applied.
                  To define multiple policies, use --policy PolicyName1 --policy PolicyName2 (...) 
                  Some policies require parameters. To specify it, use: 
                  PolicyName='{"policyParam1"="policyVal1", "policyParam2"="policyVal2"}'
                """.trimIndent()
    ).multiple()
    // associate()

    //

    override fun run() {

        // val key = runBlocking { KeyUtil().getKey(keyFile) }

        val jws = vc.readText()

        val result = runBlocking { VCUtil.verify(jws, policies) }

        // if (result.isSuccess) {
        //     print.green("Success! ", false)
        //     print.plain("The VC signature is valid.")
        // } else {
        //     print.red("Fail! ", false)
        //     // print.plain("VC signature is not valid.")
        //     result.exceptionOrNull()?.let { it.message ?: "VC signature is not valid." }?.let { print.plain(it) }
        // }
    }
}
