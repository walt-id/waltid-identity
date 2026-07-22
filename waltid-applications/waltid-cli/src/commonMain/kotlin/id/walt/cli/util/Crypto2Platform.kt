package id.walt.cli.util

import id.walt.crypto2.providers.SoftwareKeyProvider

expect fun platformSoftwareKeyProviders(): List<SoftwareKeyProvider>
