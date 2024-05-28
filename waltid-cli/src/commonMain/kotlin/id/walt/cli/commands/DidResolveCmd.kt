package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import id.walt.cli.util.DidUtil
import id.walt.cli.util.PrettyPrinter
import kotlinx.coroutines.runBlocking

class DidResolveCmd : CliktCommand(
    name = "resolve",
    help = "Resolve the decentralized identity passed as an argument, i.e. it retrieves the sovereign identity document addressed by the given DID.",
    printHelpOnEmptyArgs = true
) {
    val print : PrettyPrinter = PrettyPrinter(this)
    private val did by option("-d", "-did")
        .help("the did to be resolved")
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