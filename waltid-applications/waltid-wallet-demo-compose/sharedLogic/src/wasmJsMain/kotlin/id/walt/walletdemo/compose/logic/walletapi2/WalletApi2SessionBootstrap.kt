package id.walt.walletdemo.compose.logic.walletapi2

suspend fun establishWalletApi2Session(
    email: String,
    password: String,
    register: Boolean,
): WalletApi2Session {
    val baseUrl = walletApi2BaseUrl()
    val auth = WalletApi2AuthClient(baseUrl)
    if (register) {
        auth.register(email.trim(), password)
    }
    val token = auth.login(email.trim(), password)
    val client = WalletApi2Client(baseUrl, token)
    val wallets = client.listWallets()
    val remembered = WalletApi2BrowserSessionStore.load()?.walletId
    val walletId = remembered?.takeIf { it in wallets } ?: wallets.firstOrNull() ?: client.createWallet()
    return WalletApi2Session(
        baseUrl = baseUrl,
        token = token,
        walletId = walletId,
        email = email.trim(),
    ).also(WalletApi2BrowserSessionStore::save)
}
