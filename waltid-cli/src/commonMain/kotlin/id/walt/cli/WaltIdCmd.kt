package id.walt.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import id.walt.cli.commands.CommonOptions
import id.walt.cli.commands.KeyCmd

class WaltIdCmd : CliktCommand(
    name = "waltid",
    help = """walt.id CLI

        The walt.id CLI is a command line tool that allows you to onboard and 
        use a SSI (Self-Sovereign-Identity) ecosystem. You can manage 
        cryptographic keys, generate and register W3C Decentralized 
        Identifiers (DIDs) as well as create, issue & verify W3C Verifiable 
        credentials (VCs). 
        
        Example commands are:

        Print usage instructions
        -------------------------
        waltid -h
        waltid --help
        waltid key -h
        waltid key generate -h
        waltid key convert -h
        
        Key generation
        ---------------
        waltid key generate
        waltid key generate -t secp256k1
        waltid key generate --keyType=RSA
        waltid key generate --keyType=RSA -o myRsaKey.json
        
        Key conversion
        ---------------
        waltid key convert --input=myRsaKey.pem
        """,
    printHelpOnEmptyArgs = true
) {
    init {
        subcommands(KeyCmd())
    }

    private val commonOptions by CommonOptions()

    override fun run() = Unit
}
