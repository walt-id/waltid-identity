package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import id.walt.cli.util.WaltIdCmdHelpOptionMessage

class VCCmd : CliktCommand(
    name = "vc",
) {

    override fun help(context: Context) = """Sign and apply a wide range verification policies on W3C Verifiable Credentials (VCs)."""
    override val printHelpOnEmptyArgs = true

    init {
        subcommands(VCSignCmd(), VCVerifyCmd())

        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    override fun run(): Unit {}
}
