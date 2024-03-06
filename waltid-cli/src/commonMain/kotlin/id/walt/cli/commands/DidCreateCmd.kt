package id.walt.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import id.walt.cli.util.DidMethod
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.LocalKey
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.LocalRegistrar
import id.walt.did.dids.resolver.LocalResolver
import kotlinx.coroutines.runBlocking

class DidCreateCmd : CliktCommand(
    name = "create",
    help = "Create a brand new Decentralized Identity"
) {

    companion object {
        init {
            runBlocking {
                DidService.apply {
                    registerResolver(LocalResolver())
                    updateResolversForMethods()
                    registerRegistrar(LocalRegistrar())
                    updateRegistrarsForMethods()
                }
            }
        }
    }

    private val method by option("-m", "--method")
        .help("The DID method to be used.")
        .enum<DidMethod>(ignoreCase = true)
        // .choice(DidMethod.KEY.name, DidMethod.JWK.name)
        .default(DidMethod.KEY)

    private val keyFile by option("-k", "--key")
        .help("The Subject's key to be used. If none is provided, a new one will be generated.")
        .file()
    // .enum<DidMethod>(ignoreCase = true)

    override fun run() {
        runBlocking {
            val key = getKey()

            echo(TextStyles.dim("DID subject ${key.keyType} key thumbprint: ${key.getThumbprint()}"))
            // terminal.println(
            //     Markdown(
            //         """
            //     |```json
            //     |${key.getThumbprint()}
            //     |```
            // """.trimMargin()
            //     )
            // )
            // walletService.createDid("key", mapOf("alias" to JsonPrimitive("Onboarding")))

            val result = DidService.registerByKey(method.name.lowercase(), key) //, options)
            echo(TextColors.green("DID created:"))
            terminal.println(
                Markdown(
                    """
                |```json
                |${result.did}
                |```
            """.trimMargin()
                )
            )
        }
    }

    private suspend fun getKey(): LocalKey {
        if (keyFile != null) {
            return LocalKey.importJWK(keyFile!!.readText()).getOrThrow()
        } else {
            return generateDefaultKey()
        }
    }

    private suspend fun generateDefaultKey(): LocalKey {
        echo(TextStyles.dim("Key not provided. Let's generate a new one..."))
        val key = runBlocking { LocalKey.generate(KeyType.Ed25519) }
        echo(TextStyles.dim("Key thumbprint is: ${key.getThumbprint()}"))

        val jwk = runBlocking { key.exportJWKPretty() }
        echo(TextColors.green("Generated Key (JWK):"))
        terminal.println(
            Markdown(
                """
                |```json
                |$jwk
                |```
            """.trimMargin()
            )
        )

        return key
        // return KeyUtils.generateKey(KeyType.Ed25519)


    }
}