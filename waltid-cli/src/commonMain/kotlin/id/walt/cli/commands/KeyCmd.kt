package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class KeyCmd : CliktCommand(
    name="key",
    help="Key management features",
    printHelpOnEmptyArgs = true) {

    init {
        subcommands(KeyGenerateCmd(), KeyConvertCmd())
    }

    override fun run(): Unit {}
}
fun main(args: Array<String>) = KeyCmd().main(args)
