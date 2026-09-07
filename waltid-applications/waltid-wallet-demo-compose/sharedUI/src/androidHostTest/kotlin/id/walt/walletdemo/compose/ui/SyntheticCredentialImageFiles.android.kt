package id.walt.walletdemo.compose.ui

import java.io.File

internal actual object SyntheticCredentialImageFiles {
    actual fun read(name: String): ByteArray {
        val fixtureDirectory = checkNotNull(System.getProperty("walletDemoImageFixturesDir")) {
            "walletDemoImageFixturesDir is not configured"
        }
        return File(fixtureDirectory, name).readBytes()
    }
}
