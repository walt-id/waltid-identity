package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import id.walt.cli.util.WaltIdCmdHelpOptionMessage

class KeyCmd : CliktCommand(
    name = "key"
) {

    override fun help(context: Context) = "Key management features."
    override val printHelpOnEmptyArgs = true

    init {
        subcommands(KeyGenerateCmd(), KeyConvertCmd())

        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    private val commonOptions by CommonOptions()

    override fun run() = Unit
}
