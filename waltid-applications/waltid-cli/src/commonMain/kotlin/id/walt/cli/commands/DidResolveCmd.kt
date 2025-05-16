package id.walt.cli.commands

//import com.google.gson.Gson
//import com.google.gson.GsonBuilder
//import com.google.gson.JsonObject

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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

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

    @OptIn(ExperimentalSerializationApi::class)
    override fun run() {
        runBlocking {
            val result = DidUtil.resolveDid(did)
//            val jsonObject: JsonObject = Gson().fromJson(result.toString(), JsonObject::class.java)
//            val gson: Gson = GsonBuilder().setPrettyPrinting().create()
//            val prettyJsonString = gson.toJson(jsonObject)
            val jsonObject: JsonObject = Json.parseToJsonElement(result.toString()).jsonObject

            val jsonPrettyPrinter = Json {
                prettyPrint = true
                prettyPrintIndent = "  "
            }
            val prettyJsonString = jsonPrettyPrinter.encodeToString(JsonObject.serializer(), jsonObject)

            print.green("Did resolved: ")
            print.box(prettyJsonString)
        }
    }
}
