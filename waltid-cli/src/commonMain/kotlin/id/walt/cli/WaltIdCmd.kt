package id.walt.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import id.walt.cli.commands.KeyCmd

class WaltIdCmd : CliktCommand(
    name = "waltid",
    help = """WaltId CLI

        The WaltId CLI is a command line tool that allows you to onboard and 
        use a SSI (Self-Sovereign-Identity) ecosystem. You can manage 
        cryptographic keys, generate and register W3C Decentralized 
        Identifiers (DIDs) as well as create, issue & verify W3C Verifiable 
        credentials (VCs). 
        
        Example commands are:

        Print usage instructions
        -------------------------
        ../gradlew run --args="-h"  
        ../gradlew run --args="--help" 
        ../gradlew run --args="key -h" 
        ../gradlew run --args="key generate -h" 
        ../gradlew run --args="key convert -h" 
        
        Key generation
        ---------------
        ../gradlew run --args="key generate"
        ../gradlew run --args="key generate"
        ../gradlew run --args="key generate -tsecp256k1"
        ../gradlew run --args="key generate --keyType=RSA"
        ../gradlew run --args="key generate --keyType=RSA -o=myRSAKey.json"
        
        Key convertion
        ---------------
        ../gradlew run --args="key convert --input=./myRSAKey.pem"
        """,
    printHelpOnEmptyArgs = true
) {

    init {
        subcommands(KeyCmd())
    }

    override fun run() {}
}
