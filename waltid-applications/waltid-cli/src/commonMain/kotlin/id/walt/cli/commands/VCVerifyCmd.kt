package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.installMordantMarkdown
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.VCUtil
import id.walt.cli.util.WaltIdCmdHelpOptionMessage
import id.walt.policies2.vc.JsonSchemaVerificationException
import kotlinx.coroutines.runBlocking
import java.io.File

class VCVerifyCmd : CliktCommand(name = "verify") {
    override fun help(context: Context) = """Verify a W3C, SD-JWT VC, or mdoc credential with policies2.

        Example usage:
        ----------------
        waltid vc verify ./myVC.signed.json
        waltid vc verify -p signature -p schema --arg=schema=mySchema.json ./myVC.signed.json
    """.replace("\n", "  \n")

    init {
        installMordantMarkdown()
        context { localization = WaltIdCmdHelpOptionMessage }
    }

    override val printHelpOnEmptyArgs = true

    val print = PrettyPrinter(this)

    private val credential: File by argument(
        help = "The W3C JWT VC, SD-JWT VC, or mdoc credential file to verify.",
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true)

    private val policyNames by option("-p", "--policy")
        .choice(
            "signature",
            "expired",
            "expiration",
            "not-before",
            "revoked-status-list",
            "schema",
            "allowed-issuer",
            "webhook",
        )
        .multiple()
        .help("Additional policies to run. Signature is mandatory; expiration and not-before are added by default.")

    private val rawArguments by option("-a", "--arg")
        .convert { value ->
            value.substringBefore('=', missingDelimiterValue = "").takeIf(String::isNotBlank)
                ?.let { it to value.substringAfter('=') }
                ?: fail("must use name=value")
        }
        .multiple()

    override fun run() {
        val arguments = rawArguments.groupBy({ it.first }, { it.second }).toMutableMap()
        if ("schema" in policyNames) {
            val schemaPath = arguments["schema"]?.singleOrNull()
                ?: throw IllegalArgumentException("Policy schema requires exactly one --arg=schema=/path/to/schema.json")
            arguments["schema"] = listOf(File(schemaPath).also {
                require(it.isFile) { "Schema file does not exist: $schemaPath" }
            }.readText())
        }
        val policies = VCUtil.policies(policyNames, arguments)
        val results = runBlocking { VCUtil.verify(credential.readText(), policies) }

        print.box("Verification Result")
        results.forEach { run ->
            print.dim("${run.policy.id}: ", false)
            if (run.result.isSuccess) {
                print.green("Success!")
            } else {
                print.red("Fail! ", false)
                val cause = run.result.exceptionOrNull()
                if (cause is JsonSchemaVerificationException) {
                    print.italic(cause.validationErrors.joinToString { "${it.objectPath}: ${it.message}" })
                } else {
                    print.italic(cause?.message ?: cause?.let { it::class.simpleName } ?: "Unknown verification error")
                }
            }
        }
        if (results.any { it.result.isFailure }) throw ProgramResult(1)
    }
}
