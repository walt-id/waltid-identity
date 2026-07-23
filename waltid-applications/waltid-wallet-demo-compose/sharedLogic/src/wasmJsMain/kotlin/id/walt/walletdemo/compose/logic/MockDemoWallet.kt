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

    override suspend fun startIssuance(
        offerUrl: String,
        redirectUri: String,
        did: String?,
    ): WalletDemoIssuanceSession = WalletDemoIssuanceSession(
        id = "mock-issuance-session",
        grant = WalletDemoIssuanceGrant.PreAuthorizedCode,
        preview =
        WalletDemoOfferPreview(
            issuer = WalletDemoIssuerMetadata(
                credentialIssuer = "https://issuer.example",
                display = WalletDemoMetadataDisplay(
                    name = "walt.id demo issuer",
                    logoUri = null,
                    logoAltText = null,
                ),
            ),
            offeredCredentials = listOf(
                WalletDemoOfferedCredentialMetadata(
                    configurationId = "MockCredential",
                    format = "jwt_vc_json",
                    vct = null,
                    doctype = null,
                    display = WalletDemoMetadataDisplay(
                        name = "Mock credential",
                        logoUri = null,
                        logoAltText = null,
                    ),
                    claims = emptyList(),
                )
            ),
            transactionCode = null,
        ),
    )

    override suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String?,
    ): WalletDemoIssuanceOutcome {
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
        return WalletDemoIssuanceOutcome.Stored(credentials.map { it.id })
    }

    override suspend fun continueAuthorizationIssuance(
        sessionId: String,
        callbackUri: String,
    ): WalletDemoIssuanceOutcome = WalletDemoIssuanceOutcome.Failed("Mock issuer does not use authorization code")

    override suspend fun cancelIssuance(sessionId: String): WalletDemoIssuanceOutcome =
        WalletDemoIssuanceOutcome.Cancelled

    override suspend fun resumeDeferredIssuance(deferredCredentialId: String) =
        WalletDemoIssuanceOutcome.Failed("No mock deferred credential")

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult =
        WalletDemoOperationResult.Success("Mock presentation sent")

    override suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreviewResult =
        WalletDemoPresentationPreviewResult.Ready(
            WalletDemoPresentationPreview(
                previewHandle = WalletDemoPresentationPreviewHandle("mock-presentation-preview"),
                responseEncryption = WalletDemoResponseEncryption.NotRequired,
                verifierMetadata = WalletDemoVerifierMetadata(
                    display = WalletDemoMetadataDisplay(
                        name = "Mock verifier",
                        logoUri = null,
                        logoAltText = null,
                    ),
                    clientUri = null,
                    policyUri = null,
                    termsOfServiceUri = null,
                ),
                clientId = "mock-verifier",
                credentialOptions = emptyList(),
            ),
        )

    override suspend fun submitPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
    ): WalletDemoOperationResult =
        WalletDemoOperationResult.Success("Mock presentation sent")

    override suspend fun rejectPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
    ): WalletDemoOperationResult = WalletDemoOperationResult.Success("Mock presentation rejected")

    override suspend fun discardPresentationPreview(previewHandle: WalletDemoPresentationPreviewHandle) = Unit
}
