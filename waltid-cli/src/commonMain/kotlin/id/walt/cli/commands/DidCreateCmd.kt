package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import id.walt.cli.util.DidMethod
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import kotlinx.coroutines.runBlocking

class DidCreateCmd : CliktCommand(
    name = "create",
    help = "Create a brand new Decentralized Identity"
) {

    private val method by option("-m", "--method")
        .help("The DID method to be used.")
        .enum<DidMethod>(ignoreCase = true)
        // .choice(DidMethod.KEY.name, DidMethod.JWK.name)
        .default(DidMethod.KEY)

    private val keyFile by option("-k", "--key")
        .help("The Subject's key to be used. If none is provided, a new one will be generated.")
        .file()
    // .enum<DidMethod>(ignoreCase = true)

    private val key by lazy {
        keyFile?.readText() ?: generateDefaultKey()
    }

    override fun run() {
        echo("Subject's key: ${key}")
        echo("DID created: did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
    }

    private fun generateDefaultKey(): LocalKey {
        echo("Key not provided. Let's generate a new one...")
        val key = runBlocking { LocalKey.generate(KeyType.Ed25519) }
        val jwk = runBlocking { key.exportJWKPretty() }
        echo("Key generated: ${jwk}")

        return key
        // return KeyUtils.generateKey(KeyType.Ed25519)


    }
}