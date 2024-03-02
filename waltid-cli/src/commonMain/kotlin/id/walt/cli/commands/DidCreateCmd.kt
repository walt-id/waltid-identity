package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import id.walt.cli.util.DidMethod

class DidCreateCmd : CliktCommand(
    name = "create",
    help = "Create a brand new Decentralized Identity"
) {

    val input by option("-m", "--method")
        .help("The DID method to be used.")
        // .enum<DidMethod>(ignoreCase = true)
        .choice(DidMethod.KEY.name, DidMethod.JWK.name)
        .default(DidMethod.KEY.name)

    override fun run() {
        echo("DID created: did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
    }
}