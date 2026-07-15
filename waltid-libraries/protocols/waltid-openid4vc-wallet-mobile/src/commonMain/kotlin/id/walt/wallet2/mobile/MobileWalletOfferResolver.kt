package id.walt.wallet2.mobile

import id.walt.openid4vci.offers.TxCode
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.waltid.openid4vci.wallet.offer.CredentialOfferParser
import id.waltid.openid4vci.wallet.offer.CredentialOfferResolver

internal suspend fun resolveMobileWalletOffer(offerUrl: String): MobileWalletOfferResolution {
    val parsedOffer = CredentialOfferParser.parseCredentialOfferUrl(offerUrl.trim())
    val dataFetcher = WebDataFetcher(WebDataFetcherId.WALLET2_ISSUANCE_HANDLER)
    val offer = try {
        CredentialOfferResolver(dataFetcher.httpClient).resolveCredentialOffer(
            credentialOffer = parsedOffer.credentialOffer,
            credentialOfferUri = parsedOffer.credentialOfferUri,
        )
    } finally {
        dataFetcher.close()
    }

    return MobileWalletOfferResolution(
        txCode = offer.grants?.preAuthorizedCode?.txCode.toMobileWalletTxCode(),
    )
}

internal fun TxCode?.toMobileWalletTxCode(): MobileWalletTxCode? =
    this?.takeIf { it.value == null }?.let { txCode ->
        val length = txCode.length
        require(length == null || length > 0) {
            "Transaction code length must be positive"
        }
        MobileWalletTxCode(
            inputMode = when (txCode.inputMode ?: "numeric") {
                "numeric" -> MobileWalletTxCodeInputMode.numeric
                "text" -> MobileWalletTxCodeInputMode.text
                else -> error("Unsupported transaction code input mode: ${txCode.inputMode}")
            },
            length = length,
            issuerDescription = txCode.description,
        )
    }
