package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.VCUtil
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    )
        .choice(
            "schema",
            "holder-binding",
            "expired",
            "webhook",
            "maximum-credentials",
            "minimum-credentials",
            "signature",
            "allowed-issuer",
            "not-before"
        )
        // .default("signature")
        .multiple()

    // associate()

    override fun run() {

        // val key = runBlocking { KeyUtil().getKey(keyFile) }

        val jws = vc.readText()

        val results = runBlocking { VCUtil.verify(jws, policies) }

        print.box("Verification Result")
        results.forEach {
            if (it.isSuccess()) { // Not enough to be a successful verification. Sometimes, the verification succeeds because the policy is not even applied.

                var policyAvailable = false
                var details = ""
                val innerException = it.result.exceptionOrNull()

                if (innerException != null) {
                    details = innerException.message!!
                    // if (innerException is ExpirationDatePolicyException && (innerException as ExpirationDatePolicyException).policyAvailable) {
                    //     reason = "??"
                    // } else if (innerException is IllegalStateException) {
                    //     reason = innerException.message!!
                    // }
                } else if (!(it.result.getOrThrow() as JsonObject).get("policy_available")!!
                        .equals(JsonPrimitive(true))
                ) { // If policy_available == false
                    details =
                        " Pero no mucho. Neither 'exp', 'validUntil' nor 'expirationDate' found ¯\\_(ツ)_/¯ Is it a bug?"

                }
                print.dim("${it.request.policy.name}: ", false)
                print.green("Success! ", false)
                print.plain(details)
            } else {
                print.dim("${it.request.policy.name}: ", false)
                print.red("Fail! ", false)
                it.result.exceptionOrNull()?.message?.let { msg -> print.italic(msg) }
            }
        }
    }
}
