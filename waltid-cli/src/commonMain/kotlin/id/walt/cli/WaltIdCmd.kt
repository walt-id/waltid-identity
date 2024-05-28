package id.walt.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import id.walt.cli.commands.CommonOptions
import id.walt.cli.commands.DidCmd
import id.walt.cli.commands.KeyCmd
import id.walt.cli.commands.VCCmd

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
        waltid did -h
        waltid did create -h
        waltid vc -h
        waltid vc sign -h
        waltid vc verify -h
        
        Key generation
        ---------------
        waltid key generate
        waltid key generate -t secp256k1
        waltid key generate --keyType=RSA
        waltid key generate --keyType=RSA -o myRsaKey.json
        
        Key conversion
        ---------------
        waltid key convert --input=myRsaKey.pem
        
        DID creation
        -------------
        waltid did create 
        waltid did create -k myKey.json
        
        DID resolution
        --------------
        waltid did resolve -d did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV
        
        VC signing
        -------------
        waltid vc sign --key=./myKey.json --subject=did:key:z6Mkjm2gaGsodGchfG4k8P6KwCHZsVEPZho5VuEbY94qiBB9 ./myVC.json
        waltid vc sign --key=./myKey.json \
                       --subject=did:key:z6Mkjm2gaGsodGchfG4k8P6KwCHZsVEPZho5VuEbY94qiBB9\
                       --issuer=did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV\
                       ./myVC.json
        
        VC verification
        ----------------
        waltid vc verify ./myVC.signed.json
        waltid vc verify --policy=signature ./myVC.signed.json
        waltid vc verify --policy=schema --arg=schema=mySchema.json ./myVC.signed.json
        waltid vc verify --policy=signature --policy=schema --arg=schema=mySchema.json ./myVC.signed.json
        """,
    printHelpOnEmptyArgs = true
) {
    init {
        subcommands(KeyCmd(), DidCmd(), VCCmd())
    }

    private val commonOptions by CommonOptions()

    override fun run() = Unit
}
