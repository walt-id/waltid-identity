package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import id.walt.cli.util.WaltIdCmdHelpOptionMessage

class VPCmd : CliktCommand(
    name = "vp",
    help = "Create and apply a wide range of verification policies on W3C Verifiable Presentations (VPs).",
    printHelpOnEmptyArgs = true
) {

    init {
        subcommands(VPCreateCmd(), VPVerifyCmd())

        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    override fun run(): Unit {}
}

fun main(args: Array<String>) = VPCmd().main(args)