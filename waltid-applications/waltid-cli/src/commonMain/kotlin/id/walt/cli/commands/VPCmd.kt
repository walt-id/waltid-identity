package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class VPCmd : CliktCommand(
    name = "vp",
    help = "Creating and verifying Verifiable Presentations",
    printHelpOnEmptyArgs = true
) {

    init {
        subcommands(VPCreateCmd(), VPVerifyCmd())
    }

    override fun run(): Unit {}
}

fun main(args: Array<String>) = VPCmd().main(args)