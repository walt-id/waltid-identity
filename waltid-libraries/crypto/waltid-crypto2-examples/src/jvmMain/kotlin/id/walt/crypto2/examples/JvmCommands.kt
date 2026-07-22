package id.walt.crypto2.examples

import id.walt.crypto2.providers.cryptography.BouncyCastleSecp256k1SoftwareKeyProvider

val jvmExampleCommands = portableExampleCommands + didCoseExampleCommands + listOf(
    ExampleCommand(
        name = "es256k",
        description = "Sign and verify ES256K with the explicit JVM Bouncy Castle provider",
    ) { output ->
        runSecp256k1Example(
            provider = BouncyCastleSecp256k1SoftwareKeyProvider(),
            providerDescription = "JVM Bouncy Castle",
            output = output,
        )
    },
    ExampleCommand(
        name = "pkcs11-softhsm",
        description = "Run a managed P-256 key lifecycle with PKCS11 SoftHSM",
        includeInAll = false,
    ) { output -> Pkcs11SoftHsmExample.run(output = output) },
)
