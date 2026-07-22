package id.walt.crypto2.examples

import id.walt.crypto2.providers.cryptography.Openssl3Secp256k1SoftwareKeyProvider

val opensslExampleCommands = nativeExampleCommands + ExampleCommand(
    name = "es256k",
    description = "Sign and verify ES256K with the explicit OpenSSL 3 provider",
) { output ->
    runSecp256k1Example(
        provider = Openssl3Secp256k1SoftwareKeyProvider(),
        providerDescription = "native OpenSSL 3",
        output = output,
    )
}
