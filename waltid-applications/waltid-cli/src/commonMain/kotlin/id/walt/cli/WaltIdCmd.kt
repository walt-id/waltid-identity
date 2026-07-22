package id.walt.cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import id.walt.cli.commands.*
import id.walt.cli.util.WaltIdCmdHelpOptionMessage

class WaltIdCmd : CliktCommand(
    name = "waltid"
) {

    override fun help(context: Context) = """
        walt.id CLI

        Crypto2-first key, DID, credential, and Final OpenID4VP/DCQL tooling.

        Key generation
        ---------------
        waltid key generate -t P-256
        waltid key generate -t P-384
        waltid key generate -t Ed25519
        waltid key generate -t secp256k1
        waltid key generate -t RSA --rsa-bits 3072 -o myRsaKey.jwk

        Key conversion
        ---------------
        waltid key convert -i myKey.jwk -o myPrivateKey.pem
        waltid key convert -i myKey.jwk -f SPKI -o myPublicKey.pem
        waltid key convert -i myPrivateKey.pem -o myPrivateKey.jwk

        DID creation
        -------------
        waltid did create -k myKey.json
        waltid did create -m jwk -k myKey.json

        DID resolution
        --------------
        waltid did resolve -d did:key:z6Mkp7AVwvWxnsNDuSSbf19sgKzrx223WY95AqZyAGifFVyV

        VC signing
        ----------
        waltid vc sign -k myKey.jwk -s did:example:holder myVC.json

        VC verification
        ----------------
        waltid vc verify myVC.signed.json
        waltid vc verify -p signature -p schema -a schema=mySchema.json myVC.signed.json

        Final OpenID4VP presentation
        ----------------------------
        waltid vp create -hd <holder-did> -hk holder.jwk -vd <verifier-id> -n <nonce> \
          -vc credential.jwt -dq query.json -vp vp-token.json
        waltid vp verify -hd <holder-did> -vd <verifier-id> -n <nonce> \
          -dq query.json -vp vp-token.json
        """.replace("\n", "  \n")

    init {
        installMordantMarkdown()
    }


    override val printHelpOnEmptyArgs = true

    init {
        subcommands(KeyCmd(), DidCmd(), VCCmd(), VPCmd())

        context {
            localization = WaltIdCmdHelpOptionMessage
        }
    }

    private val commonOptions by CommonOptions()

    override fun run() = Unit
}
