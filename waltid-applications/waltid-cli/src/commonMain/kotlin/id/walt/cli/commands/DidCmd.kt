package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class DidCmd : CliktCommand(
    name = "did",
    help = "DID management features",
    printHelpOnEmptyArgs = true
) {

    init {
        subcommands(DidCreateCmd(), DidResolveCmd())
    }

    override fun run(): Unit {}
}

fun main(args: Array<String>) = DidCmd().main(args)
