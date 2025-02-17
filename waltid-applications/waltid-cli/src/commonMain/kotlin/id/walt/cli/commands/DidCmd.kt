package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import id.walt.cli.util.WaltIdCmdHelpOptionMessage

class DidCmd : CliktCommand(
    name = "did"
) {

    override fun help(context: Context) = "DID management features."
    override val printHelpOnEmptyArgs = true

    init {
        subcommands(DidCreateCmd(), DidResolveCmd())

        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    override fun run(): Unit {}
}
