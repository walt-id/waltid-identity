package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand

class VPCmd : CliktCommand(
    name = "vp",
    help = "Creating and verifying Verifiable Presentations",
    printHelpOnEmptyArgs = true
) {

    init {
    }

    override fun run(): Unit {}
}

fun main(args: Array<String>) = VPCmd().main(args)