package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand

class DidResolveCmd : CliktCommand(
    name = "resolve",
    help = "Resolve the decentralized identity passed as an argument, i.e. it retrieves the sovereign identity document addressed by the given DID.",
    printHelpOnEmptyArgs = true
) {
    override fun run() {

    }
}