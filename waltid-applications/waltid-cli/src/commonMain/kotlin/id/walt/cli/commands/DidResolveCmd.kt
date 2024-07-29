package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import id.walt.cli.util.DidUtil
import id.walt.cli.util.PrettyPrinter
import id.walt.cli.util.WaltIdCmdHelpOptionMessage
import kotlinx.coroutines.runBlocking

class DidResolveCmd : CliktCommand(
    name = "resolve",
    help = """Resolve the document associated with the input Decentralized Identifier (DID).
        
        Example usage:
        --------------
        waltid did resolve -d did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV
    """,
    printHelpOnEmptyArgs = true
) {

    init {
        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    val print : PrettyPrinter = PrettyPrinter(this)
    private val did by option("-d", "-did")
        .help("The DID to be resolved.")
        .required()

    override fun run() {
        runBlocking {
            val result = DidUtil.resolveDid(did)
            val jsonObject: JsonObject = Gson().fromJson(result.toString(), JsonObject::class.java)
            val gson: Gson = GsonBuilder().setPrettyPrinting().create()
            val prettyJsonString = gson.toJson(jsonObject)

            print.green("Did resolved: ")
            print.box(prettyJsonString)
        }
    }
}