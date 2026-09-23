package id.walt.wallet2.mobile

/**
 * Ordered OpenID4VCI create-protocol identifiers advertised to Credential Manager.
 *
 * [MobileWalletDigitalCredentialProtocols.OPENID4VCI_V1] is canonical and first. The remaining
 * entries are historical aliases still sent by some issuer pages; they are accepted and echoed,
 * not advertised as separate capabilities.
 */
internal val OPENID4VCI_CREATE_PROTOCOLS: List<String> = listOf(
    MobileWalletDigitalCredentialProtocols.OPENID4VCI_V1,
    "openid4vci1.0",
    "openid4vci-1.0",
    "openid4vci1.1",
    "openid4vci-1.1",
)
