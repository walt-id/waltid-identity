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
import id.walt.credentials.verification.policies.ExpirationDatePolicy
import id.walt.credentials.verification.policies.NotBeforeDatePolicy
import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File


class VCVerifyCmd : CliktCommand(
    name = "verify",
    printHelpOnEmptyArgs = true
) {

    override fun commandHelp(context: Context): String {
        // val style = context.theme.info
        return """VC verification command.
            
         Verifies the specified VC under a set of specified policies.

        The available policies are:
        
        - schema: Verifies a credentials data against a JSON Schema (Draft 7 - see https://json-schema.org/specification-links#draft-7).
        - holder-binding: Verifies that issuer of the Verifiable Presentation (presenter) is also the subject of all Verifiable Credentials contained within.
        - presentation-definition: Verifies that with an Verifiable Presentation at minimum the list of credentials `request_credentials` has been presented.
        - expired: Verifies that the credentials expiration date (`exp` for JWTs) has not been exceeded.
        - webhook: Sends the credential data to an webhook URL as HTTP POST, and returns the verified status based on the webhooks set status code (success = 200 - 299).
        - maximum-credentials: Verifies that a maximum number of credentials in the Verifiable Presentation is not exceeded.
        - minimum-credentials: Verifies that a minimum number of credentials are included in the Verifiable Presentation.
        - signature: Checks a JWT credential by verifying its cryptographic signature using the key referenced by the DID in `iss`.
        - allowed-issuer: Checks that the issuer of the credential is present in the supplied list.
        - not-before: Verifies that the credentials not-before date (for JWT: `nbf`, if unavailable: `iat` - 1 min) is correctly exceeded.
        
        Multiple policies are accepted. e.g.
     
            waltid vc verify --policy=signature --policy=expired vc.json
   
        If no policy is specified, only the Signature Policy will be applied. i.e.
        
            waltid vc verify vc.json
        
        Some policies require parameters. To specify it, use --arg or -a options. e.g.
        
            --arg=param1=value1 --a param2=value2
            
            e.g.

            waltid vc verify --policy=schema -a schema=mySchema.json vc.json
        """.trimIndent()
    }


    val print: PrettyPrinter = PrettyPrinter(this)

    private val vc: File by argument(help = "the verifiable credential file (in JWS format) to be verified (required)").file()

    val policies: List<String> by option(
        "-p",
        "--policy",
        help = """Specify a policy to be applied in the verification process."""
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
    ).associate().help {
        """Argument required by some policies, namely:
            
            |Policy|Expected Argument|
            |------|--------|
            |signature| - |
            |expired| - |
            |schema|schema=/path/to/schema.json|
        """.trimMargin()
    }

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
            } // isFailure
        } // results.forEach
    } // run()
} // class
