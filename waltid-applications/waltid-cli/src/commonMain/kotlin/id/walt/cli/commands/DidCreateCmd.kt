package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.installMordantMarkdown
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.terminal.YesNoPrompt
import id.walt.cli.util.DidMethod
import id.walt.cli.util.DidUtil
import id.walt.cli.util.KeyUtil
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.WaltIdCmdHelpOptionMessage
import id.walt.did.dids.registrar.dids.DidCreateOptions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.writeText

class DidCreateCmd : CliktCommand(
    name = "create"
) {
    override fun help(context: Context) = """Create a Decentralized Identifier (DID).
        
        Example usage:
        --------------
        waltid did create 
        waltid did create -k myKey.json
        waltid did create -m jwk
    """.replace("\n", "  \n")

    init {
        installMordantMarkdown()
    }

    init {
        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    val print: PrettyPrinter = PrettyPrinter(this)

    private val method by option("-m", "--method")
        .help("The native crypto2 DID method to use: key or jwk.")
        .enum<DidMethod>(ignoreCase = true)
        .default(DidMethod.KEY)

    private val keyFile by option("-k", "--key")
        .help("The subject's key to be used. If none is provided, a new one will be generated.")
        .file(canBeDir = false)
    private val useJwkJcsPub by option("-j", "--useJwkJcsPub")
        .help("Flag to enable JWK_JCS-Pub encoding (default=off). Applies only to the did:key method and is relevant in the context of EBSI.")
        .flag(default = false)

    private val output by option("-o", "--did-doc-output")
        .path()
        .help("File path to save the created DID Document (optional). If not specified, the did document will be saved at the <did>.json file.")

    override fun run() {
        runBlocking {
            val key = KeyUtil(this@DidCreateCmd).getKey(keyFile)

            val jwk = KeyUtil.exportJwk(key, private = false)

            print.green("DID subject public key (JWK):")
            print.box(jwk)

            val result = DidUtil.createDid(
                method,
                key,
                DidCreateOptions(
                    method = method.name.lowercase(),
                    config = if (method == DidMethod.KEY) mapOf("useJwkJcsPub" to JsonPrimitive(useJwkJcsPub))
                    else emptyMap(),
                )
            )

            val outputFile = output ?: Path("${URLEncoder.encode(result.did, "UTF-8")}.json")
            if (outputFile.exists()
                && YesNoPrompt(
                    "The file \"${outputFile.absolutePathString()}\" already exists, do you want to overwrite it?",
                    terminal
                ).ask() == false
            ) {
                print.plain("Will not overwrite output file.")
                return@runBlocking
            }

            val prettyJson = Json {
                prettyPrint = true
            }

            val prettyJsonString = prettyJson.encodeToString(result.didDocument)

            print.green("DID Document:")
            print.box(prettyJson.encodeToString(result.didDocument))
            outputFile.writeText(prettyJsonString)

            print.green("DID created:")
            print.plain(result.did)
        }
    }
}
