package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import id.walt.cli.util.*
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import kotlinx.coroutines.runBlocking

class DidCreateCmd : CliktCommand(
    name = "create",
    help = """Create a Decentralized Identifier (DID).
        
        Example usage:
        --------------
        waltid did create 
        waltid did create -k myKey.json
        waltid did create -m jwk
    """
) {

    init {
        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    val print: PrettyPrinter = PrettyPrinter(this)

    private val method by option("-m", "--method")
        .help("The DID method to be used.")
        .enum<DidMethod>(ignoreCase = true)
        .default(DidMethod.KEY)

    private val keyFile by option("-k", "--key")
        .help("The subject's key to be used. If none is provided, a new one will be generated.")
        .file(canBeDir = false)
    private val useJwkJcsPub by option("-j", "--useJwkJcsPub")
        .help("Flag to enable JWK_JCS-Pub encoding (default=off). Applies only to the did:key method and is relevant in the context of EBSI.")
        .flag(default = false)

    override fun run() {
        runBlocking {
            val key = KeyUtil(this@DidCreateCmd).getKey(keyFile)

            val jwk = key.exportJWKPretty()

            print.green("DID Subject key to be used:")
            print.box(jwk)

            val result = if (useJwkJcsPub && method == DidMethod.KEY) {
                DidUtil.createDid(method, key, DidKeyCreateOptions(key.keyType, useJwkJcsPub)) }
            else
                DidUtil.createDid(method, key)

            print.green("DID created:")
            // print.box(result) // Can't be used because truncates long DIDs
            print.plain(result)
        }
    }
}
