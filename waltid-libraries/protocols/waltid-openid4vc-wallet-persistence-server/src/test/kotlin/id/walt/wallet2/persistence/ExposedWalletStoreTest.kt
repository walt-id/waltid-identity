package id.walt.wallet2.persistence

import id.walt.wallet2.data.WalletDescriptor
import id.walt.wallet2.data.WalletX509TrustConfig
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ExposedWalletStoreTest {

    @Test
    fun `wallet security configuration survives SQL persistence round trip`() = runTest {
        val databaseFile = Files.createTempFile("wallet2-descriptor-", ".sqlite").also {
            Files.deleteIfExists(it)
            it.toFile().deleteOnExit()
        }
        val database = initWallet2Database(
            Wallet2PersistenceConfig(
                jdbcUrl = "jdbc:sqlite:$databaseFile",
                maximumPoolSize = 1,
                minimumIdle = 1,
            )
        )
        val store = ExposedWalletStore(database)
        val descriptor = WalletDescriptor(
            id = "security-config-wallet",
            keyStoreIds = listOf("key-store"),
            credentialStoreIds = listOf("credential-store"),
            requestObjectX509Trust = WalletX509TrustConfig(
                trustAnchorPemCertificates = listOf("test-wallet-controlled-trust-anchor"),
                enableRevocation = true,
                allowedRequestObjectAlgorithms = setOf("ES256"),
                requireTrustAnchorOmittedFromX5c = true,
                rejectLeafTrustAnchor = true,
            ),
            requestObjectAudience = "https://wallet.example/custom-request-object-audience",
        )

        store.saveDescriptor(descriptor)

        assertEquals(descriptor, store.loadDescriptor(descriptor.id))
    }
}
