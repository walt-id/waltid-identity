package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.FileNotFound
import com.github.ajalt.clikt.core.MissingOption
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.VCUtil
import id.walt.credentials.verification.ExpirationDatePolicyException
import id.walt.credentials.verification.JsonSchemaVerificationException
import id.walt.credentials.verification.NotBeforePolicyException
import id.walt.credentials.verification.PolicyManager
import id.walt.credentials.verification.models.PolicyResult
import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File

class VCVerifyCmd : CliktCommand(
    name = "verify",
    printHelpOnEmptyArgs = true,
) {

    override fun commandHelp(context: Context): String {
        var help = """
        VC verification command.
            
        Verifies the specified VC under a set of specified policies.

        The available policies are:
        
        """.trimIndent()
        help += "\u0085\u0085"

        PolicyManager.listPolicyDescriptions().entries.forEach {
            help += "- ${it.key}: ${it.value}\u0085"
        }

        help += "\u0085"

        help += """
        Multiple policies are accepted. e.g.

            waltid vc verify --policy=signature --policy=expired vc.json

        If no policy is specified, only the Signature Policy will be applied. i.e.

            waltid vc verify vc.json

        Some policies require parameters. To specify it, use --arg or -a options.

            --arg=param1=value1 --a param2=value2

        e.g.

            waltid vc verify --policy=schema -a schema=mySchema.json vc.json
        """.trimIndent()

        return help
    }

    val print: PrettyPrinter = PrettyPrinter(this)

    private val vc: File by argument(help = "the verifiable credential file (in JWS format) to be verified (required)").file()

    val policies: List<String> by option(
        "-p",
        "--policy",
        help = """Specify a policy to be applied in the verification process."""
    ).choice(
        *PolicyManager.listPolicyDescriptions().keys.toTypedArray()
    ).multiple()

    val policyArguments: Map<String, String> by option(
        "-a",
        "--arg",
    ).associate().help {
        """Argument required by some policies, namely:
            
            |Policy|Expected Argument|
            |------|--------|
            |signature| - |
            |expired| - |
            |not-before| - |
            |schema|schema=/path/to/schema.json|
        """.trimMargin()
    }

    override fun run() {

        val jws = vc.readText()
        val args = checkAndLoadArguments()

        val results = runBlocking { VCUtil.verify(jws, policies, args) }

        print.box("Verification Result")
        results.forEach {
            if (it.isSuccess()) { // Not enough to be a successful verification. Sometimes, the verification succeeds because the policy is not even applied.
                handleSuccess(it)
            } else {
                handleFailure(it)
            }
        }
    }

    private fun handleFailure(it: PolicyResult) {
        when (val exception = it.result.exceptionOrNull()) {

            is JsonSchemaVerificationException -> {
                exception.validationErrors.forEach { err ->
                    print.dim("${it.request.policy.name}: ", false)
                    print.red("Fail! ", false)
                    if (err.objectPath.isEmpty()) {
                        print.italic("""-> ${err.message}""")
                    } else {
                        print.italic(""""${err.objectPath}" (in ${err.schemaPath}) -> ${err.message}""")
                    }
                }
            }

            is ExpirationDatePolicyException -> {
                print.dim("${it.request.policy.name}: ", false)
                print.red("Fail! ", false)
                print.italic("VC expired since ${exception.date}")
            }

            is NotBeforePolicyException -> {
                print.dim("${it.request.policy.name}: ", false)
                print.red("Fail! ", false)
                print.italic("VC not valid until ${exception.date}")
            }

            else -> {
                print.dim("${it.request.policy.name}: ", false)
                print.red("Fail! ", false)
                exception?.message?.let { print.italic(it) }
            }
        }
    }

    private fun handleSuccess(it: PolicyResult) {
        print.dim("${it.request.policy.name}: ", false)
        print.green("Success! ")
    }

    private fun checkAndLoadArguments(): MutableMap<String, JsonElement> {
        val args = emptyMap<String, JsonElement>().toMutableMap()
        args.putAll(getSchemaPolicyArguments())
        return args
    }

    private fun getSchemaPolicyArguments(): Map<out String, JsonElement> {
        val args = emptyMap<String, JsonElement>().toMutableMap()

        if ("schema" in policies) {

            // Argument provided?
            if ("schema" !in policyArguments || policyArguments["schema"]!!.isEmpty()) {
                throw MissingOption(this.option("--arg for the 'schema' policy (--arg=schema=/file/path/to/schema.json)"))
            }

            val schemaFilePath = policyArguments.get("schema")!!
            val schemaFile = File(schemaFilePath)

            // Schema exists?
            if (!schemaFile.exists()) {
                throw FileNotFound(schemaFilePath)
            }

            val schema = File(schemaFilePath).readText()
            args["schema"] = Json.parseToJsonElement(schema).toJsonElement()
        }

        return args
    }
}
