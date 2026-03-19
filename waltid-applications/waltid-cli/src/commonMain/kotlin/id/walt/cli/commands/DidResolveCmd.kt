package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.installMordantMarkdown
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import id.walt.cli.util.DidUtil
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.WaltIdCmdHelpOptionMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val prettyJson = Json { prettyPrint = true }

class DidResolveCmd : CliktCommand(
    name = "resolve"
) {
    override fun help(context: Context) = """Resolve the document associated with the input Decentralized Identifier (DID).
        
        Example usage:
        --------------
        waltid did resolve -d did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV
    """.replace("\n", "  \n")

    init {
        installMordantMarkdown()
    }

    override val printHelpOnEmptyArgs = true

    init {
        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    val print: PrettyPrinter = PrettyPrinter(this)
    private val did by option("-d", "-did")
        .help("The DID to be resolved.")
        .required()

    override fun run() {
        runBlocking {
            val result = DidUtil.resolveDid(did)
            val jsonObject = Json.decodeFromString<JsonObject>(result.toString())
            val prettyJsonString = prettyJson.encodeToString(jsonObject)

            print.green("Did resolved: ")
            print.box(prettyJsonString)
        }
    }
}
