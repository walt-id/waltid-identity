package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import id.walt.cli.util.DidMethod
import id.walt.cli.util.DidUtil
import id.walt.cli.util.KeyUtil
import id.walt.cli.util.PrettyPrinter
import kotlinx.coroutines.runBlocking

class DidCreateCmd : CliktCommand(
    name = "create",
    help = "Create a brand new Decentralized Identity"
) {

    val print: PrettyPrinter = PrettyPrinter(this)

    private val method by option("-m", "--method")
        .help("The DID method to be used.")
        .enum<DidMethod>(ignoreCase = true)
        .default(DidMethod.KEY)

    private val keyFile by option("-k", "--key")
        .help("The Subject's key to be used. If none is provided, a new one will be generated.")
        .file()

    override fun run() {
        runBlocking {
            val key = KeyUtil(this@DidCreateCmd).getKey(keyFile)

            val jwk = key.exportJWKPretty()

            print.green("DID Subject key to be used:")
            print.box(jwk)

            val result = DidUtil.createDid(method, key)

            print.green("DID created:")
            // print.box(result) // Can't be used because truncates long DIDs
            print.plain(result)
        }
    }
}