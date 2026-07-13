package id.walt.walletdemo.compose.logic

fun createMockDemoWallet(): DemoWallet = MockDemoWallet()

private class MockDemoWallet : DemoWallet {
    private var credentials = emptyList<WalletDemoCredential>()

    override suspend fun bootstrap(): WalletDemoBootstrapResult =
        WalletDemoBootstrapResult(
            keyId = "mock-key",
            did = "did:key:mock-wallet-demo",
        )

    override suspend fun listCredentials(): List<WalletDemoCredential> = credentials

    override suspend fun resolveOffer(offerUrl: String): DemoOfferResolution =
        DemoOfferResolution(txCodeRequired = false)

    override suspend fun credentialDetails(id: String): WalletDemoCredentialDetails? =
        credentials.firstOrNull { it.id == id }?.let {
            WalletDemoCredentialDetails(id = it.id, credentialDataJson = "{}")
        }

    override suspend fun receive(offerUrl: String, txCode: String?): List<String> {
        credentials = listOf(
            WalletDemoCredential(
                id = "mock-credential",
                format = "jwt_vc_json",
                issuer = "walt.id demo issuer",
                subject = "did:key:mock-wallet-demo",
                label = "Mock credential",
                addedAt = "2026-06-17",
            )
        )
        return credentials.map { it.id }
    }

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult =
        WalletDemoOperationResult.Success("Mock presentation sent")
}
