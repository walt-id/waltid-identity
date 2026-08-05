package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import id.walt.cli.util.WaltIdCmdHelpOptionMessage

class VCCmd : CliktCommand(
    name = "vc",
) {

    override fun help(context: Context) =
        "Issue W3C JWT VCs and verify W3C, SD-JWT VC, and mdoc credentials with policies2."
    override val printHelpOnEmptyArgs = true

    init {
        subcommands(VCSignCmd(), VCVerifyCmd())

        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    override fun run(): Unit {}
}
