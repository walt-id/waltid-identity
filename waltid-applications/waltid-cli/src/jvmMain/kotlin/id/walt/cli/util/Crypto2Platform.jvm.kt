package id.walt.cli.util

import id.walt.crypto2.providers.SoftwareKeyProvider
import id.walt.crypto2.providers.cryptography.BouncyCastleSecp256k1SoftwareKeyProvider

actual fun platformSoftwareKeyProviders(): List<SoftwareKeyProvider> =
    listOf(BouncyCastleSecp256k1SoftwareKeyProvider())
