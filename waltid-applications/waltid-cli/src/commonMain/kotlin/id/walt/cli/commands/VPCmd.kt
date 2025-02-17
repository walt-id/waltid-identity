package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import id.walt.cli.util.WaltIdCmdHelpOptionMessage

class VPCmd : CliktCommand(
    name = "vp"
) {

    override fun help(context: Context) = "Create and apply a wide range of verification policies on W3C Verifiable Presentations (VPs)."
    override val printHelpOnEmptyArgs = true

    init {
        subcommands(VPCreateCmd(), VPVerifyCmd())

        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    override fun run(): Unit {}
}
