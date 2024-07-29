package id.walt.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.output.Localization
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import id.walt.cli.commands.*
import id.walt.cli.util.WaltIdCmdHelpOptionMessage

class WaltIdCmd : CliktCommand(
    name = "waltid",
    help = """walt.id CLI

        The walt.id CLI is a command line tool that allows you to onboard and 
        use a SSI (Self-Sovereign-Identity) ecosystem. You can manage 
        cryptographic keys, generate and register W3C Decentralized 
        Identifiers (DIDs), sign & verify W3C Verifiable Credentials (VCs) and
        create & verify W3C Verifiable Presentations (VPs).
        
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
        waltid did resolve -h
        waltid vc -h
        waltid vc sign -h
        waltid vc verify -h
        waltid vp -h
        waltid vp create -h
        waltid vp verify -h
        
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
        
        VP creation
        ----------------
        waltid vp create -hd did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV \
        -hk ./holder-key.json \
        -vd did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV \
        -vc ./someVcFile.json \
        -pd ./presDef.json \
        -vp ./outputVp.jwt \
        -ps ./outputPresSub.json
        waltid vp create -hd did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV \
        -hk ./holder-key.json \
        -vd did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV \
        -vc ./firstVcFile.json \
        -vc ./secondVcFile.json \
        -pd ./presDef.json \
        -vp ./outputVp.jwt \
        -ps ./outputPresSub.json
        
        VP Verification
        ----------------
        waltid vp verify -hd did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV \
        -pd ./presDef.json \
        -ps ./presSub.json \
        -vp ./vpPath.jwt
        waltid vp verify -hd did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV \
        -pd ./presDef.json \
        -ps ./presSub.json \
        -vp ./vpPath.jwt \
        -vpp maximum-credentials \
        -vppa=max=2 \
        -vpp minimum-credentials \
        -vppa=min=1
        """,
    printHelpOnEmptyArgs = true
) {
    init {
        subcommands(KeyCmd(), DidCmd(), VCCmd(), VPCmd())

        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    private val commonOptions by CommonOptions()

    override fun run() = Unit
}
