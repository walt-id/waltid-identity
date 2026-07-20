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

    override suspend fun resolveOffer(offerUrl: String): WalletDemoOfferPreview =
        WalletDemoOfferPreview(
            transactionCodeRequired = false,
            credentialIssuer = "walt.id demo issuer",
            offeredCredentials = listOf("MockCredential"),
        )

    override suspend fun receive(offerUrl: String, txCode: String?): List<String> {
        credentials = listOf(
            WalletDemoCredential(
                id = "mock-credential",
                format = "jwt_vc_json",
                issuer = "walt.id demo issuer",
                subject = "did:key:mock-holder",
                label = "Mock credential",
                addedAt = "2026-06-17",
                credentialDataJson = WalletDemoSampleCredentialData.credentialDataJsonWithPortrait,
            )
        )
        return credentials.map { it.id }
    }

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult =
        WalletDemoOperationResult.Success("Mock presentation sent")

    override suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreview =
        WalletDemoPresentationPreview(
            verifierName = "Mock verifier",
            clientId = "mock-verifier",
            credentialOptions = emptyList(),
        )

    override suspend fun submitPresentation(
        requestUrl: String,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
    ): WalletDemoOperationResult =
        WalletDemoOperationResult.Success("Mock presentation sent")
}
