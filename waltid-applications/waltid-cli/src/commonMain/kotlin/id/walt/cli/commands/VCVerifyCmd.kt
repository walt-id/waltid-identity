package id.walt.cli.commands

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.VCUtil
import id.walt.cli.util.WaltIdCmdHelpOptionMessage
import id.walt.credentials.verification.ExpirationDatePolicyException
import id.walt.credentials.verification.JsonSchemaVerificationException
import id.walt.credentials.verification.NotBeforePolicyException
import id.walt.credentials.verification.models.PolicyResult
import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File

class VCVerifyCmd : CliktCommand(
    name = "verify",
    help = """Apply a wide range of verification policies on a W3C Verifiable Credential (VC).
        
        Example usage:
        ----------------
        waltid vc verify ./myVC.signed.json
        waltid vc verify -p signature ./myVC.signed.json
        waltid vc verify -p schema --arg=schema=mySchema.json ./myVC.signed.json
        waltid vc verify -p signature -p schema --arg=schema=mySchema.json ./myVC.signed.json
    """,
    printHelpOnEmptyArgs = true,
) {

    init {
        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    val print: PrettyPrinter = PrettyPrinter(this)

    private val vc: File by argument(help = "the verifiable credential file (in JWS format) to be verified (required)").file()

    val policies: List<String> by option(
        "-p",
        "--policy",
        help = "Specify one, or more policies to be applied during the verification process of the VC (signature policy is always applied)."
    ).choice(
        *listOf(
            "signature",
            "expired",
            "not-before",
            "revoked_status_list",
            "schema",
            "allowed-issuer",
            "webhook",
        ).toTypedArray()
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
            |revoked_status_list| - |
            |schema|schema=/path/to/schema.json|
            |allowed-issuer|issuer=did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV|
            |webhook|url=https://example.com|
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
        args.putAll(getAllowedIssuerPolicyArguments())
        args.putAll(getWebhookPolicyArguments())
        args.putAll(getRevocationPolicyArguments())
        for (noArgPolicyName in listOf("signature", "expired", "not-before", "revoked_status_list")) {
            if (noArgPolicyName in policies) {
                args[noArgPolicyName] = "".toJsonElement()
            }
        }
        return args
    }

    private fun getSchemaPolicyArguments(): Map<out String, JsonElement> {
        val args = emptyMap<String, JsonElement>().toMutableMap()

        if ("schema" in policies) {

            // Argument provided?
            if ("schema" !in policyArguments || policyArguments["schema"]!!.isEmpty()) {
                throw MissingOption(this.option("--arg for the 'schema' policy (--arg=schema=/file/path/to/schema.json)"))
            }

            val schemaFilePath = policyArguments["schema"]!!
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

    private fun getAllowedIssuerPolicyArguments(): Map<out String, JsonElement> {
        val args = mutableMapOf<String, JsonElement>()
        if ("allowed-issuer" in policies) {
            if ("issuer" !in policyArguments || policyArguments["issuer"]!!.isEmpty()) {
                throw MissingOption(this.option("--arg for the 'allowed-issuer' policy (--arg=issuer=did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV"))
            }
            args["allowed-issuer"] = policyArguments["issuer"]!!.toJsonElement()
        }

        return args
    }

    private fun getWebhookPolicyArguments(): Map<out String, JsonElement> {
        val args = mutableMapOf<String, JsonElement>()
        if ("webhook" in policies) {
            if ("url" !in policyArguments || policyArguments["url"]!!.isEmpty()) {
                throw MissingOption(this.option("--arg for the 'webhook' policy (--arg=url=https://example.com"))
            }
            args["webhook"] = policyArguments["url"]!!.toJsonElement()
            args["vc"] = vc.readText().toJsonElement()
        }

        return args
    }

    private fun getRevocationPolicyArguments(): Map<out String, JsonElement> {
        val args = mutableMapOf<String, JsonElement>()
        if ("revoked_status_list" in policies) {
            args["vc"] = vc.readText().toJsonElement()
        }

        return args
    }
}
