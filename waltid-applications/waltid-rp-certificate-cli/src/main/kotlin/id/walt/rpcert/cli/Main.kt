package id.walt.rpcert.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import id.walt.rpcert.cli.commands.IssueJwtCommand
import id.walt.rpcert.cli.commands.ValidateJwtCommand

class RpCertCli : CliktCommand(name = "waltid-rpcert") {
    override fun help(context: Context) =
        "Issue and validate EUDI Wallet-Relying Party Registration Certificates (WRPRC / rc-wrp+jwt)."

    override fun run() = Unit
}

fun main(args: Array<String>) {
    RpCertCli()
        .subcommands(IssueJwtCommand(), ValidateJwtCommand())
        .main(args)
}
