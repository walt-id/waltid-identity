package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import id.walt.cli.util.WaltIdCmdHelpOptionMessage

class VCCmd : CliktCommand(
    name = "vc",
    help = """Sign and apply a wide range verification policies on W3C Verifiable Credentials (VCs).""",
    printHelpOnEmptyArgs = true,
) {

    init {
        subcommands(VCSignCmd(), VCVerifyCmd())

        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    override fun run(): Unit {}
}

fun main(args: Array<String>) = VCCmd().main(args)
