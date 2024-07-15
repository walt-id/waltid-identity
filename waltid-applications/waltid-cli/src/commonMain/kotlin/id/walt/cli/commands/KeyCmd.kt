package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import id.walt.cli.util.WaltIdCmdHelpOptionMessage

class KeyCmd : CliktCommand(
    name = "key",
    help = "Key management features.",
    printHelpOnEmptyArgs = true
) {

    init {
        subcommands(KeyGenerateCmd(), KeyConvertCmd())

        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    private val commonOptions by CommonOptions()

    override fun run() = Unit
}

fun main(args: Array<String>) = KeyCmd().main(args)
