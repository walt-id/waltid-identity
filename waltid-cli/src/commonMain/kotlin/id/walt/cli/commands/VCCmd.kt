package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class VCCmd : CliktCommand(
    name = "vc",
    help = "Issuing, presenting and verifying Verifiable Credentials",
    printHelpOnEmptyArgs = true
) {

    init {
        subcommands(VCSignCmd(), VCVerifyCmd())
    }

    override fun run(): Unit {}
}

fun main(args: Array<String>) = VCCmd().main(args)
