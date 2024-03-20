package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.InvalidFileFormat
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.rendering.TextStyles
import id.walt.cli.util.DidMethod
import id.walt.cli.util.DidUtil
import id.walt.cli.util.PrettyPrinter
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.runBlocking
import java.text.ParseException

class DidCreateCmd : CliktCommand(
    name = "create",
    help = "Create a brand new Decentralized Identity"
) {

    val print: PrettyPrinter = PrettyPrinter(this)

    private val method by option("-m", "--method")
        .help("The DID method to be used.")
        .enum<DidMethod>(ignoreCase = true)
        // .choice(DidMethod.KEY.name, DidMethod.JWK.name)
        .default(DidMethod.KEY)

    private val keyFile by option("-k", "--key")
        .help("The Subject's key to be used. If none is provided, a new one will be generated.")
        .file()

    override fun run() {
        runBlocking {
            val key = getKey()

            val jwk = runBlocking { key.exportJWKPretty() }

            print.green("DID Subject key to be used:")
            print.box(jwk)

            val result = DidUtil.createDid(method, key)

            print.green("DID created:")
            print.box(result.did)
        }
    }

    private suspend fun getKey(): JWKKey {
        if (keyFile != null) {
            try {
                return JWKKey.importJWK(keyFile!!.readText()).getOrThrow()
            } catch (e: ParseException) {
                throw InvalidFileFormat(keyFile!!.name, e.let { e.message!! })
            }
        } else {
            return generateDefaultKey()
        }
    }

    private suspend fun generateDefaultKey(): JWKKey {
        echo(TextStyles.dim("Key not provided. Let's generate a new one..."))
        val key = runBlocking { JWKKey.generate(KeyType.Ed25519) }
        echo(TextStyles.dim("Key generated with thumbprint ${key.getThumbprint()}"))

        return key
    }
}