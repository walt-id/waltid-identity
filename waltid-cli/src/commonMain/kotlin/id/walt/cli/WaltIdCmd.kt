package id.walt.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
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
        waltid-cli -h
        waltid-cli --help
        waltid-cli key -h
        waltid-cli key generate -h
        waltid-cli key convert -h
        
        Key generation
        ---------------
        waltid-cli key generate
        waltid-cli key generate -t secp256k1
        waltid-cli key generate --keyType=RSA
        waltid-cli key generate --keyType=RSA -o myRsaKey.json
        
        Key conversion
        ---------------
        waltid-cli key convert --input=myRsaKey.pem
        """,
    printHelpOnEmptyArgs = true
) {

    init {
        subcommands(KeyCmd())
    }

    override fun run() = Unit
}
