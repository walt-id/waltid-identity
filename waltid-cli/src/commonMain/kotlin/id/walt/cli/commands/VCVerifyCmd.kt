package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.FileNotFound
import com.github.ajalt.clikt.core.MissingOption
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.VCUtil
import id.walt.credentials.verification.JsonSchemaVerificationException
import id.walt.credentials.verification.policies.ExpirationDatePolicy
import id.walt.credentials.verification.policies.NotBeforeDatePolicy
import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

// sealed class LoadConfig(name: String): OptionGroup(name)
// class SchemaPolicy : LoadConfig("Options for --policy=schema") {
//     val schema by option().file().required()
// }
//
// class HolderBindingPolicy : LoadConfig("Options for --policy=holder-binding") {
//     val xxx by option().file().required()
// }
class VCVerifyCmd : CliktCommand(
    name = "verify",
    help = "Verify the specified VC under a set of specified policies.",
    printHelpOnEmptyArgs = true,
    allowMultipleSubcommands = true
) {

    // init {
    //     subcommands(
    //         VCVerifySignatureCmd(),
    //         VCVerifySchemaCmd())
    // }

    // Shared context with subcommands
    // val config by findOrSetObject { mutableMapOf<String, File>() }

    val print: PrettyPrinter = PrettyPrinter(this)

    // private val keyFile by option("-k", "--key")
    //     // .help("The Subject's key to be used. If none is provided, a new one will be generated.")
    //     .help("A core-crypto key representation to sign the credential (required)")
    //     .file()
    // .required()

    private val vc: File by argument(help = "the verifiable credential file (in JWS format) to be verified (required)").file()

    // val policies: List<String> by option().groupChoice(
    val policies: List<String> by option(
        "-p",
        "--policy",
        help = """Specify a policy to be applied in the verification process.
                  Multiple policies are accepted.
                  If no policy is specified, only the Signature Policy will be applied.
                  To define multiple policies, use --policy PolicyName1 --policy PolicyName2 (...)
                  Some policies require parameters. To specify it, use --arg arg1=value1
                  '
                """.trimIndent()
    ).choice(
        "schema",
        "holder-binding",
        "expired",
        "webhook",
        "maximum-credentials",
        "minimum-credentials",
        "signature",
        "allowed-issuer",
        "not-before"
    ).multiple()

    val policyArguments: Map<String, String> by option(
        "-a",
        "--arg",
        help = "Argument required by some policies."
    ).associate()

    // .groupChoice(
    //         "schema" to SchemaPolicy(),
    //         "holder-binding" to HolderBinding()
    //         // "expired",
    //         // "webhook",
    //         // "maximum-credentials",
    //         // "minimum-credentials",
    //         // "signature",
    //         // "allowed-issuer",
    //         // "not-before"
    //     )
        // .default("signature")
    // .multiple()


    // associate()

    override fun run() {

        //     echo("Saving VC file into the context")
        //     config["vc"] = vc
        // }
        // {
        //
        // val key = runBlocking { KeyUtil().getKey(keyFile) }

        val jws = vc.readText()
        val args = emptyMap<String, JsonElement>().toMutableMap()
        if ("schema" in policies) {

            // Argument provided?
            if ("schema" !in policyArguments || policyArguments["schema"]!!.isEmpty()) {
                throw MissingOption(this.option("--arg for the 'schema' policy (--arg=schema=/file/path/to/schema.json)"))
                // throw Exception("missing schema policy argument: use --arg schema=schemaFilePath")
            }

            val schemaFilePath = policyArguments.get("schema")!!
            val schemaFile = File(schemaFilePath)

            // Schema exists?
            if (!schemaFile.exists()) {
                throw FileNotFound(schemaFilePath)
            }

            val schema = File(schemaFilePath).readText()
            // File("/Users/alegomes/coding/waltid-identity/waltid-cli/src/jvmTest/resources/schema/ob_v3p0_achievementcredential_schema.json").readText()
            args["schema"] = Json.parseToJsonElement(schema).toJsonElement()
        }

        val results = runBlocking { VCUtil.verify(jws, policies, args) }

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
                } else if (it.result.getOrNull() !is Unit &&
                    "policy_available" in it.result.getOrThrow() as JsonObject &&
                    !(it.result.getOrThrow() as JsonObject).get("policy_available")!!.equals(JsonPrimitive(true))
                ) { // If policy_available == false
                    when (it.request.policy) {
                        is ExpirationDatePolicy -> {
                            details =
                                " Pero no mucho. Neither 'exp', 'validUntil' nor 'expirationDate' found. Is it a bug? ¯\\_(ツ)_/¯"
                        }

                        is NotBeforeDatePolicy -> {
                            details = "Not that much. Neither 'nbf' not 'iat' found. Is it a bug? ¯\\_(ツ)_/¯"
                        }

                    }


                }
                print.dim("${it.request.policy.name}: ", false)
                print.green("Success! ", false)
                print.plain(details)
            } else { // isFailure
                val exception = it.result.exceptionOrNull()
                if (exception is JsonSchemaVerificationException) {
                    exception.validationErrors.forEach { err ->
                        print.dim("${it.request.policy.name}: ", false)
                        print.red("Fail! ", false)
                        if (err.objectPath.isEmpty()) {
                            print.italic("""-> ${err.message}""")
                        } else {
                            print.italic(""""${err.objectPath}" (in ${err.schemaPath}) -> ${err.message}""")
                        }
                    }
                } else {
                    print.dim("${it.request.policy.name}: ", false)
                    print.red("Fail! ", false)
                    exception?.message?.let { print.italic(it) }
                }
            }
        }
    }
}
