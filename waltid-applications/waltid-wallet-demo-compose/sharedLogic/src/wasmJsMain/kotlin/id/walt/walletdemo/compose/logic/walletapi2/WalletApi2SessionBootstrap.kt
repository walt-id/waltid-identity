package id.walt.walletdemo.compose.logic.walletapi2

suspend fun establishWalletApi2Session(
    email: String,
    password: String,
    register: Boolean,
): WalletApi2Session {
    val kind = walletApiKind()
    val baseUrl = walletApi2BaseUrl()
    val auth = WalletApi2AuthClient(baseUrl, kind)
    if (register) {
        require(kind.canRegister) { "This API does not support registration" }
        auth.register(email.trim(), password)
    }
    val token = auth.login(email.trim(), password)
    val client = WalletApi2Client(baseUrl, token, kind)
    val wallets = client.listAccessibleWallets()
    val walletId = selectWalletId(wallets, rememberedWalletId())
        ?: if (kind.canManageWallet) {
            client.createWallet()
        } else {
            error("No wallet services are available for this account")
        }
    return WalletApi2Session(
        kind = kind,
        baseUrl = baseUrl,
        token = token,
        walletId = walletId,
        email = email.trim(),
        walletTargets = wallets.ifEmpty { listOf(walletId) },
    ).also(WalletApi2BrowserSessionStore::save)
}

suspend fun refreshWalletTargets(session: WalletApi2Session): List<String> {
    val client = WalletApi2Client(session.baseUrl, session.token, session.kind)
    return client.listAccessibleWallets()
}
